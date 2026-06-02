package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

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
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsÃ¡vel pelo processamento de baixa e cancelamento em lote
 * de agendamentos no banco de dados local e na API externa do Feegow ERP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class FeegowBulkIntegrationHandler {

    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final AppointmentExternalPort appointmentExternalPort;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final TransactionTemplate transactionTemplate;
    private final BlipContextService blipContextService;
    private final BlipProperties blipProperties;

    /**
     * Executa a confirmaÃ§Ã£o em lote de todos os agendamentos pertencentes ao grupo
     * e os sincroniza com a API do Feegow ERP.
     *
     * @param groupId ID do grupo de notificaÃ§Ãµes
     * @param purifiedPhone telefone purificado do paciente
     * @return lista de sessÃµes de agendamento afetadas
     */
    public List<AppointmentSession> executeConfirmBatch(UUID groupId, String purifiedPhone) {
        log.info("[BULK-INTEGRATION] Iniciando busca robusta de grupo para confirmaÃ§Ã£o em lote. groupId: {}", groupId);

        java.util.Map<UUID, AppointmentSession> uniqueSessions = new java.util.LinkedHashMap<>();

        // 1. EstratÃ©gia A: Buscar da tabela 'notification_groups'
        List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
        log.info("[BULK-INTEGRATION] Busca por groupId na tabela 'notification_groups' retornou {} registros.", groups.size());
        for (NotificationGroup group : groups) {
            appointmentSessionRepository.findById(group.getSessionId())
                .ifPresent(s -> {
                    uniqueSessions.put(s.getId(), s);
                    log.info("[BULK-INTEGRATION] SessÃ£o vinculada via grupo carregada: id={}, feegowAppointmentId={}", s.getId(), s.getFeegowAppointmentId());
                });
        }

        // 2. EstratÃ©gia B: Buscar diretamente na tabela de agendamentos pelo 'currentGroupId'
        List<AppointmentSession> sessionsByGroupField = appointmentSessionRepository.findByCurrentGroupId(groupId);
        log.info("[BULK-INTEGRATION] Busca direta por 'currentGroupId' na tabela de agendamentos retornou {} registros.", sessionsByGroupField.size());
        for (AppointmentSession s : sessionsByGroupField) {
            if (!uniqueSessions.containsKey(s.getId())) {
                uniqueSessions.put(s.getId(), s);
                log.info("[BULK-INTEGRATION] SessÃ£o vinculada via campo currentGroupId carregada: id={}, feegowAppointmentId={}", s.getId(), s.getFeegowAppointmentId());
            }
        }
        
        // 3. EstratÃ©gia C (Fallback): Se a lista ainda estiver vazia e tivermos o telefone, buscar sessÃµes ativas do telefone
        if (uniqueSessions.isEmpty() && purifiedPhone != null && !purifiedPhone.isBlank()) {
            log.warn("[BULK-INTEGRATION] Nenhuma sessÃ£o encontrada para groupId={}. Aplicando fallback para sessÃµes ativas do telefone: {}", groupId, purifiedPhone);
            List<AppointmentSession> activeContactSessions = appointmentSessionRepository.findActiveByPhoneNumber(purifiedPhone);
            for (AppointmentSession s : activeContactSessions) {
                uniqueSessions.put(s.getId(), s);
                log.info("[BULK-INTEGRATION] SessÃ£o de fallback ativa carregada do telefone: id={}, feegowAppointmentId={}", s.getId(), s.getFeegowAppointmentId());
            }
        }

        List<AppointmentSession> sessionList = new ArrayList<>(uniqueSessions.values());
        log.info("[BULK-INTEGRATION] Total de sessÃµes elegÃ­veis unificadas para confirmaÃ§Ã£o em lote: {}", sessionList.size());

        // 4. Atualizar status local de todas as sessÃµes para CONFIRMADO de forma transacional e com bloqueio pessimista
        for (AppointmentSession groupSession : sessionList) {
            transactionTemplate.executeWithoutResult(status -> {
                AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(groupSession.getId()).orElse(null);
                if (lockedSession != null) {
                    confirmationStateMachineService.markConfirmed(lockedSession);
                    appointmentSessionRepository.save(lockedSession);
                    log.info("[BULK-INTEGRATION] Status local da sessÃ£o {} atualizado para CONFIRMED com sucesso.", lockedSession.getId());
                } else {
                    log.warn("[BULK-INTEGRATION] NÃ£o foi possÃ­vel bloquear a sessÃ£o {} para atualizaÃ§Ã£o de status.", groupSession.getId());
                }
            });
        }

        // 5. Disparar API do Feegow individualmente em lote para cada agendamento
        String confirmedStatusId = "7"; // Status Confirmado padrÃ£o no Feegow
        String configuredStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
        if (configuredStatusId != null && !configuredStatusId.isBlank()) {
            String trimmed = configuredStatusId.trim();
            if (!"2".equals(trimmed)) {
                confirmedStatusId = trimmed;
            }
        }

        log.info("[BULK-INTEGRATION] Disparando atualizaÃ§Ãµes de status para o Feegow (status={}). Total: {}", confirmedStatusId, sessionList.size());
        for (AppointmentSession groupSession : sessionList) {
            try {
                appointmentExternalPort.updateAppointmentStatus(groupSession.getFeegowAppointmentId(), confirmedStatusId);
                log.info("[BULK-INTEGRATION] Status enviado Ã  API do Feegow para ID: {}", groupSession.getFeegowAppointmentId());
            } catch (RestClientException | IllegalStateException ex) {
                log.error("[BULK-INTEGRATION] Falha crÃ­tica ao enviar atualizaÃ§Ã£o de status para a Feegow. ID: {}, erro: {}",
                    groupSession.getFeegowAppointmentId(), ex.getMessage(), ex);
            }
        }

        return sessionList;
    }

    /**
     * Resolve a fila Blip de redirecionamento com base nos mÃ©dicos das sessÃµes afetadas.
     *
     * @param sessionList lista de sessÃµes de agendamento afetadas
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
            targetQueue = "RecepÃ§Ã£o Central / Suporte";
        }
        return targetQueue;
    }

    /**
     * Resolve a fila Blip de redirecionamento para o grupo correspondente Ã  solicitaÃ§Ã£o de alteraÃ§Ã£o.
     *
     * @param groupId ID do grupo de notificaÃ§Ãµes
     * @return nome da fila Blip resolvida
     */
    public String resolveAlterGroupQueue(UUID groupId) {
        String targetQueue = "RecepÃ§Ã£o Central / Suporte";
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

    /**
     * Processa de forma assÃ­ncrona a confirmaÃ§Ã£o em lote de todos os agendamentos pertencentes ao grupo
     * e os sincroniza com a API do Feegow ERP, redirecionando o fluxo do paciente no Blip.
     */
    @org.springframework.scheduling.annotation.Async
    public void confirmGroupAsync(UUID groupId, String fromPhone) {
        log.info("[ASYNC-BATCH] Iniciando processamento assÃ­ncrono de confirm_group para groupId: {}", groupId);
        try {
            String dbPhone = fromPhone != null ? purifyPhoneNumber(fromPhone) : null;
            
            // 1. Executa a confirmaÃ§Ã£o em lote local e na API Feegow
            List<AppointmentSession> sessionList = executeConfirmBatch(groupId, dbPhone);
            
            // 2. Resolve a fila de desempate
            String targetQueue = resolveTargetQueue(sessionList);

            // 3. Executa o redirecionamento no Blip em background
            if (fromPhone != null && !fromPhone.isBlank()) {
                blipContextService.setQueueRedirect(fromPhone.trim(), targetQueue);
                String deskBlockId = blipProperties.getBlocks().getDeskStateId();
                blipContextService.setMasterState(fromPhone.trim(), "desk@msging.net", deskBlockId);
                log.info("[ASYNC-BATCH] Paciente {} redirecionado com sucesso para a fila '{}', bloco desk: '{}'", 
                    fromPhone, targetQueue, deskBlockId);
            }
        } catch (Exception e) {
            log.error("[ASYNC-BATCH] Erro crÃ­tico no processamento assÃ­ncrono de confirmaÃ§Ã£o do grupo: " + groupId, e);
        }
    }

    /**
     * Processa de forma assÃ­ncrona o redirecionamento de alteraÃ§Ã£o em lote do grupo de agendamentos no Blip.
     */
    @org.springframework.scheduling.annotation.Async
    public void alterGroupAsync(UUID groupId, String fromPhone) {
        log.info("[ASYNC-BATCH] Iniciando processamento assÃ­ncrono de alter_group para groupId: {}", groupId);
        try {
            String targetQueue = resolveAlterGroupQueue(groupId);

            if (fromPhone != null && !fromPhone.isBlank()) {
                blipContextService.setQueueRedirect(fromPhone.trim(), targetQueue);
                String deskBlockId = blipProperties.getBlocks().getDeskStateId();
                blipContextService.setMasterState(fromPhone.trim(), "desk@msging.net", deskBlockId);
                log.info("[ASYNC-BATCH] Paciente {} redirecionado com sucesso para alteraÃ§Ã£o na fila '{}', bloco desk: '{}'", 
                    fromPhone, targetQueue, deskBlockId);
            }
        } catch (Exception e) {
            log.error("[ASYNC-BATCH] Erro crÃ­tico no processamento assÃ­ncrono de alteraÃ§Ã£o do grupo: " + groupId, e);
        }
    }

    private String purifyPhoneNumber(String originalPhone) {
        if (originalPhone == null || originalPhone.isBlank()) {
            return "";
        }
        
        String trimmed = originalPhone.trim();
        if (trimmed.contains("@")) {
            trimmed = trimmed.substring(0, trimmed.indexOf('@')).trim();
        }
        
        String digitsOnly = trimmed.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) {
            return "";
        }
        
        if (digitsOnly.startsWith("55")) {
            return "+" + digitsOnly;
        }
        
        return "+55" + digitsOnly;
    }
}


