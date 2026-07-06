package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Estratégia de processamento específica para a ação de confirmação de consulta ("confirm").
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class ConfirmBlipWebhookActionHandler implements BlipWebhookActionHandler {

    private final AppointmentExternalPort appointmentExternalPort;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final BlipContextService blipContextService;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final BlipUserIdentityReconciliationRepositoryPort blipUserIdentityReconciliationRepository;
    private final BlipProperties blipProperties;

    @Override
    public boolean supports(String actionType) {
        return "confirm".equalsIgnoreCase(actionType);
    }

    @Override
    public void prePersistence(AppointmentSession session, String action, String fromIdentity) {
        if (action != null && action.toLowerCase().startsWith("confirm_group_")) {
            String groupIdStr = action.substring("confirm_group_".length()).trim();
            try {
                UUID groupId = UUID.fromString(groupIdStr);
                List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
                
                String userPhone = session.getPhoneNumber();
                java.util.Map<UUID, AppointmentSession> sessoesUnicas = new java.util.LinkedHashMap<>();
                for (NotificationGroup group : groups) {
                    appointmentSessionRepository.findById(group.getSessionId()).ifPresent(s -> sessoesUnicas.put(s.getId(), s));
                }
                if (userPhone != null && !userPhone.isBlank()) {
                    List<AppointmentSession> activeContactSessions = appointmentSessionRepository.findActiveByPhoneNumber(userPhone);
                    for (AppointmentSession s : activeContactSessions) {
                        sessoesUnicas.put(s.getId(), s);
                    }
                }
                if (session.getId() != null) {
                    sessoesUnicas.put(session.getId(), session);
                }
                
                List<AppointmentSession> listaSessoes = new ArrayList<>(sessoesUnicas.values());
                
                log.info("[CONFIRM-BATCH] Processando confirmação em lote para o grupo: {} / telefone: {}. Total de agendamentos: {}", groupId, userPhone, listaSessoes.size());
                
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
                        log.info("Enviando confirmação para Feegow: {}", groupSession.getFeegowAppointmentId());
                        appointmentExternalPort.updateAppointmentStatus(groupSession.getFeegowAppointmentId(), confirmedStatusId);
                        log.info("Resposta do Feegow: SUCCESS");
                    } catch (RestClientException | IllegalStateException ex) {
                        log.error("Resposta do Feegow: ERROR");
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
                } else {
                    targetQueue = blipContextService.resolveQueueName(targetQueue);
                }

                userPhone = session.getPhoneNumber();
                
                // Configurar variável de contexto da fila no Blip
                blipContextService.setQueueRedirect(userPhone, targetQueue);
                if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                    blipContextService.setQueueRedirect(fromIdentity, targetQueue);
                }

                // Recupera dinamicamente a propriedade do bloco de sucesso
                String confirmSuccessBlockId = blipProperties.getBlocks().getConfirmSuccess();
                if (confirmSuccessBlockId == null || confirmSuccessBlockId.isBlank()) {
                    confirmSuccessBlockId = "b3461299-9500-46b1-b423-12ffef3e1aba";
                }

                String targetBot = "desk@msging.net";
                if (!"644d54dd-aefd-478b-93eb-10081acdd387".equals(confirmSuccessBlockId)) {
                    String builderBotId = appointmentMotorProperties.getBlipBuilderBotId();
                    if (builderBotId != null && !builderBotId.isBlank()) {
                        targetBot = builderBotId;
                    }
                }

                // Enviar redirecionamento de estado (Change State) para o bloco correspondente
                blipContextService.setMasterState(userPhone, targetBot, confirmSuccessBlockId);
                if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                    blipContextService.setMasterState(fromIdentity, targetBot, confirmSuccessBlockId);
                }

                // Redirecionamento de estado nas identidades de túnel (deterministica e reconciliadas)
                try {
                    if (userPhone != null && !userPhone.isBlank()) {
                        List<String> tunnelIdentities = new ArrayList<>();
                        String subbotId = blipProperties.getSubbotId();
                        String subbotLocalPart = null;
                        if (subbotId != null && !subbotId.isBlank()) {
                            subbotLocalPart = subbotId.trim();
                            if (subbotLocalPart.contains("@")) {
                                subbotLocalPart = subbotLocalPart.substring(0, subbotLocalPart.indexOf('@'));
                            }
                        }

                        String phoneDigits = userPhone.trim();
                        if (phoneDigits.contains("@")) {
                            phoneDigits = phoneDigits.substring(0, phoneDigits.indexOf('@'));
                        }
                        phoneDigits = phoneDigits.replaceAll("\\D", "");
                        if (!phoneDigits.startsWith("55") && !phoneDigits.isEmpty()) {
                            phoneDigits = "55" + phoneDigits;
                        }

                        if (subbotLocalPart != null) {
                            String deterministicTunnel = phoneDigits + "." + subbotLocalPart + "@tunnel.msging.net";
                            tunnelIdentities.add(deterministicTunnel);
                        }

                        List<br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation> reconciliations = new ArrayList<>();
                        reconciliations.addAll(blipUserIdentityReconciliationRepository.findByPhoneNumber(userPhone.trim()));
                        String altPhone = userPhone.trim().startsWith("55") ? userPhone.trim().substring(2) : "55" + userPhone.trim();
                        reconciliations.addAll(blipUserIdentityReconciliationRepository.findByPhoneNumber(altPhone));

                        for (var rec : reconciliations) {
                            if (rec.getBlipGuid() != null && !rec.getBlipGuid().isBlank()) {
                                String tunnelId = rec.getBlipGuid().trim() + "@tunnel.msging.net";
                                if (!tunnelIdentities.contains(tunnelId)) {
                                    tunnelIdentities.add(tunnelId);
                                }
                            }
                        }

                        for (String tunnelId : tunnelIdentities) {
                            if (!tunnelId.equalsIgnoreCase(userPhone) && !tunnelId.equalsIgnoreCase(fromIdentity)) {
                                blipContextService.setQueueRedirect(tunnelId, targetQueue);
                                blipContextService.setBuilderMasterState(tunnelId, confirmSuccessBlockId);
                                log.info("[CONFIRM-BATCH] Aplicado redirecionamento e Builder Master-State na identidade de túnel: {}", tunnelId);
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[CONFIRM-BATCH] Falha ao aplicar redirecionamento retroativo em túneis: {}", ex.getMessage());
                }

                log.info("[CONFIRM-BATCH] Redirecionamento de estado enviado para a Blip para o usuário {} (identidade webhook: {}). Fila: '{}', Bloco de destino dinâmico: '{}:{}'",
                        userPhone, fromIdentity, targetQueue, targetBot, confirmSuccessBlockId);

            } catch (Exception e) {
                log.error("[CONFIRM-BATCH] Erro no processamento em lote da Feegow para ação: " + action, e);
            }
        }
    }

    @Override
    public void applySessionState(AppointmentSession session, String action, String fromIdentity) {
        if (action != null && action.toLowerCase().startsWith("confirm_group_")) {
            String groupIdStr = action.substring("confirm_group_".length()).trim();
            try {
                UUID groupId = UUID.fromString(groupIdStr);
                List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
                
                String userPhone = session.getPhoneNumber();
                java.util.Map<UUID, AppointmentSession> sessoesUnicas = new java.util.LinkedHashMap<>();
                for (NotificationGroup group : groups) {
                    appointmentSessionRepository.findById(group.getSessionId()).ifPresent(s -> sessoesUnicas.put(s.getId(), s));
                }
                if (userPhone != null && !userPhone.isBlank()) {
                    List<AppointmentSession> activeContactSessions = appointmentSessionRepository.findActiveByPhoneNumber(userPhone);
                    for (AppointmentSession s : activeContactSessions) {
                        sessoesUnicas.put(s.getId(), s);
                    }
                }
                if (session.getId() != null) {
                    sessoesUnicas.put(session.getId(), session);
                }

                for (AppointmentSession groupSession : sessoesUnicas.values()) {
                    confirmationStateMachineService.markConfirmed(groupSession);
                    if (!groupSession.getId().equals(session.getId())) {
                        appointmentSessionRepository.save(groupSession);
                    }
                }
                log.info("[CONFIRM-BATCH] Sessões do grupo {} / telefone {} atualizadas para CONFIRMED no banco local. Total: {}", groupId, userPhone, sessoesUnicas.size());
            } catch (Exception e) {
                log.error("[CONFIRM-BATCH] Erro ao atualizar estados do grupo de sessões no banco local. grupo={}", groupIdStr, e);
            }
        } else {
            String confirmedStatusId = resolveConfirmedStatusId();
            log.info("Enviando confirmação para Feegow: {}", session.getFeegowAppointmentId());
            try {
                appointmentExternalPort.updateAppointmentStatus(session.getFeegowAppointmentId(), confirmedStatusId);
                log.info("Resposta do Feegow: SUCCESS");
            } catch (Exception ex) {
                log.error("Resposta do Feegow: ERROR");
                log.error("[CONFIRM] Falha ao atualizar status na Feegow para ID: {}. Detalhes: {}", 
                    session.getFeegowAppointmentId(), ex.getMessage());
                throw new RuntimeException("Falha na atualização do Feegow para o agendamento " + session.getFeegowAppointmentId() + ". Cancelando confirmação local (rollback).", ex);
            }
            confirmationStateMachineService.markConfirmed(session);

            // --- REDIRECIONAMENTO OFICIAL E OBRIGATÓRIO PARA O BLIP DESK ---
            String targetQueue = null;
            var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(session.getDoctorProfissionalId());
            if (mappingOpt.isPresent()) {
                String queue = mappingOpt.get().getBlipQueueId();
                if (queue != null && !queue.isBlank() && !"null".equalsIgnoreCase(queue.trim())) {
                    targetQueue = queue.trim();
                }
            }
            if (targetQueue == null || targetQueue.isBlank()) {
                targetQueue = "Recepção Central / Suporte";
            } else {
                targetQueue = blipContextService.resolveQueueName(targetQueue);
            }

            String userPhone = session.getPhoneNumber();
            blipContextService.setQueueRedirect(userPhone, targetQueue);
            if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                blipContextService.setQueueRedirect(fromIdentity, targetQueue);
            }

            // Recupera dinamicamente a propriedade do bloco de sucesso
            String confirmSuccessBlockId = blipProperties.getBlocks().getConfirmSuccess();
            if (confirmSuccessBlockId == null || confirmSuccessBlockId.isBlank()) {
                confirmSuccessBlockId = "b3461299-9500-46b1-b423-12ffef3e1aba";
            }

            String targetBot = "desk@msging.net";
            if (!"644d54dd-aefd-478b-93eb-10081acdd387".equals(confirmSuccessBlockId)) {
                String builderBotId = appointmentMotorProperties.getBlipBuilderBotId();
                if (builderBotId != null && !builderBotId.isBlank()) {
                    targetBot = builderBotId;
                }
            }

            blipContextService.setMasterState(userPhone, targetBot, confirmSuccessBlockId);
            if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                blipContextService.setMasterState(fromIdentity, targetBot, confirmSuccessBlockId);
            }

            // Redirecionamento de estado nas identidades de túnel (deterministica e reconciliadas)
            try {
                if (userPhone != null && !userPhone.isBlank()) {
                    List<String> tunnelIdentities = new ArrayList<>();
                    String subbotId = blipProperties.getSubbotId();
                    String subbotLocalPart = null;
                    if (subbotId != null && !subbotId.isBlank()) {
                        subbotLocalPart = subbotId.trim();
                        if (subbotLocalPart.contains("@")) {
                            subbotLocalPart = subbotLocalPart.substring(0, subbotLocalPart.indexOf('@'));
                        }
                    }

                    String phoneDigits = userPhone.trim();
                    if (phoneDigits.contains("@")) {
                        phoneDigits = phoneDigits.substring(0, phoneDigits.indexOf('@'));
                    }
                    phoneDigits = phoneDigits.replaceAll("\\D", "");
                    if (!phoneDigits.startsWith("55") && !phoneDigits.isEmpty()) {
                        phoneDigits = "55" + phoneDigits;
                    }

                    if (subbotLocalPart != null) {
                        String deterministicTunnel = phoneDigits + "." + subbotLocalPart + "@tunnel.msging.net";
                        tunnelIdentities.add(deterministicTunnel);
                    }

                    List<br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation> reconciliations = new ArrayList<>();
                    reconciliations.addAll(blipUserIdentityReconciliationRepository.findByPhoneNumber(userPhone.trim()));
                    String altPhone = userPhone.trim().startsWith("55") ? userPhone.trim().substring(2) : "55" + userPhone.trim();
                    reconciliations.addAll(blipUserIdentityReconciliationRepository.findByPhoneNumber(altPhone));

                    for (var rec : reconciliations) {
                        if (rec.getBlipGuid() != null && !rec.getBlipGuid().isBlank()) {
                            String tunnelId = rec.getBlipGuid().trim() + "@tunnel.msging.net";
                            if (!tunnelIdentities.contains(tunnelId)) {
                                tunnelIdentities.add(tunnelId);
                            }
                        }
                    }

                    for (String tunnelId : tunnelIdentities) {
                        if (!tunnelId.equalsIgnoreCase(userPhone) && !tunnelId.equalsIgnoreCase(fromIdentity)) {
                            blipContextService.setQueueRedirect(tunnelId, targetQueue);
                            blipContextService.setBuilderMasterState(tunnelId, confirmSuccessBlockId);
                            log.info("[CONFIRM] Aplicado redirecionamento e Builder Master-State na identidade de túnel: {}", tunnelId);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[CONFIRM] Falha ao aplicar redirecionamento retroativo em túneis: {}", ex.getMessage());
            }

            log.info("[CONFIRM] Redirecionamento de estado enviado para a Blip para o usuário {} (identidade webhook: {}). Fila: '{}', Bloco de destino dinâmico: '{}:{}'",
                    userPhone, fromIdentity, targetQueue, targetBot, confirmSuccessBlockId);
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
