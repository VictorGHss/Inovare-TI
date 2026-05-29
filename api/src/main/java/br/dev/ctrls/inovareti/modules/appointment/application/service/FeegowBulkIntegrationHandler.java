package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável pelo processamento de baixa e cancelamento em lote
 * de agendamentos no banco de dados local e na API externa do Feegow ERP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeegowBulkIntegrationHandler {

    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final AppointmentExternalPort appointmentExternalPort;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final TransactionTemplate transactionTemplate;

    /**
     * Executa a confirmação em lote de todos os agendamentos pertencentes ao grupo
     * e os sincroniza com a API do Feegow ERP.
     *
     * @param groupId ID do grupo de notificações
     * @param purifiedPhone telefone purificado do paciente
     * @return lista de sessões de agendamento afetadas
     */
    public List<AppointmentSession> executeConfirmBatch(UUID groupId, String purifiedPhone) {
        List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
        java.util.Map<UUID, AppointmentSession> uniqueSessions = new java.util.LinkedHashMap<>();
        for (NotificationGroup group : groups) {
            appointmentSessionRepository.findById(group.getSessionId()).ifPresent(s -> uniqueSessions.put(s.getId(), s));
        }
        
        if (purifiedPhone != null && !purifiedPhone.isBlank()) {
            List<AppointmentSession> activeContactSessions = appointmentSessionRepository.findActiveByPhoneNumber(purifiedPhone);
            for (AppointmentSession s : activeContactSessions) {
                uniqueSessions.put(s.getId(), s);
            }
        }

        List<AppointmentSession> sessionList = new ArrayList<>(uniqueSessions.values());
        log.info("[BULK-INTEGRATION] Processando confirmação em lote no banco e ERP Feegow. Total: {}", sessionList.size());

        // 1. Atualizar status local de todas as sessões para CONFIRMADO
        for (AppointmentSession groupSession : sessionList) {
            transactionTemplate.executeWithoutResult(status -> {
                AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(groupSession.getId()).orElse(null);
                if (lockedSession != null) {
                    confirmationStateMachineService.markConfirmed(lockedSession);
                    appointmentSessionRepository.save(lockedSession);
                }
            });
        }

        // 2. Disparar API do Feegow individualmente para cada agendamento
        String confirmedStatusId = "7"; // Status Confirmado padrão no Feegow
        String configuredStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
        if (configuredStatusId != null && !configuredStatusId.isBlank()) {
            String trimmed = configuredStatusId.trim();
            if (!"2".equals(trimmed)) {
                confirmedStatusId = trimmed;
            }
        }

        for (AppointmentSession groupSession : sessionList) {
            try {
                appointmentExternalPort.updateAppointmentStatus(groupSession.getFeegowAppointmentId(), confirmedStatusId);
                log.info("[BULK-INTEGRATION] Status atualizado no Feegow para CONFIRMADO: {}", groupSession.getFeegowAppointmentId());
            } catch (RestClientException | IllegalStateException ex) {
                log.error("[BULK-INTEGRATION] Falha ao atualizar status na Feegow para ID: {}, erro: {}",
                    groupSession.getFeegowAppointmentId(), ex.getMessage(), ex);
            }
        }

        return sessionList;
    }

    /**
     * Resolve a fila Blip de redirecionamento com base nos médicos das sessões afetadas.
     *
     * @param sessionList lista de sessões de agendamento afetadas
     * @return nome da fila Blip resolvida
     */
    public String resolveTargetQueue(List<AppointmentSession> sessionList) {
        String targetQueue = null;
        for (AppointmentSession groupSession : sessionList) {
            var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(groupSession.getDoctorProfissionalId());
            if (mappingOpt.isPresent()) {
                String queue = mappingOpt.get().getBlipQueueId();
                if (queue != null && !queue.isBlank() && !"null".equalsIgnoreCase(queue.trim())) {
                    targetQueue = queue.trim();
                    break;
                }
            }
        }

        if (targetQueue == null || targetQueue.isBlank()) {
            targetQueue = "Recepção Central / Suporte";
        }
        return targetQueue;
    }

    /**
     * Resolve a fila Blip de redirecionamento para o grupo correspondente à solicitação de alteração.
     *
     * @param groupId ID do grupo de notificações
     * @return nome da fila Blip resolvida
     */
    public String resolveAlterGroupQueue(UUID groupId) {
        String targetQueue = "Recepção Central / Suporte";
        List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
        if (groups != null && !groups.isEmpty()) {
            UUID firstSessionId = groups.get(0).getSessionId();
            AppointmentSession firstSession = transactionTemplate.execute(status ->
                appointmentSessionRepository.findById(firstSessionId).orElse(null)
            );
            if (firstSession != null) {
                var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(firstSession.getDoctorProfissionalId());
                if (mappingOpt.isPresent()) {
                    String queue = mappingOpt.get().getBlipQueueId();
                    if (queue != null && !queue.isBlank() && !"null".equalsIgnoreCase(queue.trim())) {
                        targetQueue = queue.trim();
                    }
                }
            }
        }
        return targetQueue;
    }
}
