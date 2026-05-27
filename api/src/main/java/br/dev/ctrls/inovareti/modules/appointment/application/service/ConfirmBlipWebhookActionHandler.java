package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Estratégia de processamento específica para a ação de confirmação de consulta ("confirm").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmBlipWebhookActionHandler implements BlipWebhookActionHandler {

    private final AppointmentExternalPort appointmentExternalPort;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final BlipContextService blipContextService;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;

    @Override
    public boolean supports(String actionType) {
        return "confirm".equalsIgnoreCase(actionType);
    }

    @Override
    public void prePersistence(AppointmentSession session, String action) {
        if (action != null && action.toLowerCase().startsWith("confirm_group_")) {
            String groupIdStr = action.substring("confirm_group_".length()).trim();
            try {
                UUID groupId = UUID.fromString(groupIdStr);
                List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
                List<AppointmentSession> listaSessoes = new ArrayList<>();
                for (NotificationGroup group : groups) {
                    appointmentSessionRepository.findById(group.getSessionId()).ifPresent(listaSessoes::add);
                }
                
                log.info("[CONFIRM-BATCH] Processando confirmação em lote para o grupo: {}. Total de agendamentos: {}", groupId, listaSessoes.size());
                
                // --- ATUALIZAÇÃO IMEDIATA DO STATUS LOCAL (FIM DO LEMBRETE FANTASMA) ---
                for (AppointmentSession groupSession : listaSessoes) {
                    confirmationStateMachineService.markConfirmed(groupSession);
                    try {
                        appointmentSessionRepository.save(groupSession);
                    } catch (Exception ex) {
                        log.error("[CONFIRM-BATCH] Falha ao persistir status local CONFIRMED antes do Feegow para sessionId={}", groupSession.getId(), ex);
                    }
                }
                // -----------------------------------------------------------------------

                String confirmedStatusId = resolveConfirmedStatusId();
                for (AppointmentSession groupSession : listaSessoes) {
                    try {
                        appointmentExternalPort.updateAppointmentStatus(groupSession.getFeegowAppointmentId(), confirmedStatusId);
                    } catch (RestClientException | IllegalStateException ex) {
                        log.error(
                            "[CONFIRM-BATCH] Falha ao atualizar status na Feegow. appointmentId={}, erro={}",
                            groupSession.getFeegowAppointmentId(),
                            ex.getMessage(),
                            ex);
                    }
                }

                // Estratégia de desempate determinista: extrair a primeira fila válida dos médicos associados ao grupo
                String targetQueue = null;
                for (AppointmentSession groupSession : listaSessoes) {
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

                String userPhone = session.getPhoneNumber();
                
                // Configurar variável de contexto da fila no Blip
                blipContextService.setQueueRedirect(userPhone, targetQueue);

                // Enviar redirecionamento de estado (Change State) para o bloco Atendimento Humano
                blipContextService.setMasterState(userPhone, "desk@msging.net", "644d54dd-aefd-478b-93eb-10081acdd387");
                log.info("[CONFIRM-BATCH] Redirecionamento de estado enviado para a Blip para usuário {}. Fila: '{}', Bloco: 'desk:644d54dd-aefd-478b-93eb-10081acdd387'",
                        userPhone, targetQueue);

            } catch (Exception e) {
                log.error("[CONFIRM-BATCH] Erro no processamento em lote da Feegow para ação: " + action, e);
            }
        } else {
            String confirmedStatusId = resolveConfirmedStatusId();
            log.info("[CONFIRM] Atualizando status na Feegow com código {}.", confirmedStatusId);
            try {
                appointmentExternalPort.updateAppointmentStatus(session.getFeegowAppointmentId(), confirmedStatusId);
            } catch (RestClientException | IllegalStateException ex) {
                log.error(
                    "[CONFIRM] Falha ao atualizar status na Feegow (continuando redirecionamento Blip). appointmentId={}, erro={}",
                    session.getFeegowAppointmentId(),
                    ex.getMessage(),
                    ex);
            }
        }
    }

    @Override
    public void applySessionState(AppointmentSession session, String action) {
        if (action != null && action.toLowerCase().startsWith("confirm_group_")) {
            String groupIdStr = action.substring("confirm_group_".length()).trim();
            try {
                UUID groupId = UUID.fromString(groupIdStr);
                List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
                for (NotificationGroup group : groups) {
                    AppointmentSession groupSession = appointmentSessionRepository.findById(group.getSessionId()).orElse(null);
                    if (groupSession != null) {
                        confirmationStateMachineService.markConfirmed(groupSession);
                        appointmentSessionRepository.save(groupSession);
                    }
                }
                log.info("[CONFIRM-BATCH] Sessões do grupo {} atualizadas para CONFIRMED no banco local.", groupId);
            } catch (Exception e) {
                log.error("[CONFIRM-BATCH] Erro ao atualizar estados do grupo de sessões no banco local. grupo={}", groupIdStr, e);
            }
        } else {
            confirmationStateMachineService.markConfirmed(session);
            log.info("[MENSAGERIA] Ação de {} processada com sucesso no banco e na Feegow. Navegação entregue ao Builder nativo.", "confirmação");
        }
    }

    private String resolveConfirmedStatusId() {
        String configuredStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
        if (configuredStatusId == null || configuredStatusId.isBlank()) {
            return "7";
        }
        String trimmed = configuredStatusId.trim();
        if ("2".equals(trimmed)) {
            return "7";
        }
        return trimmed;
    }
}
