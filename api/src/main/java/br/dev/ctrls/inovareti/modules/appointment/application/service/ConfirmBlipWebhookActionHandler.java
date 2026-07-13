package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;

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
    private final PatientExternalPort patientExternalPort;

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
                
                // Configurar variável de contexto da fila, ID e CPF no Blip
                try {
                    if (!listaSessoes.isEmpty()) {
                        AppointmentSession firstSession = listaSessoes.get(0);
                        String firstFeegowId = firstSession.getFeegowAppointmentId();
                        blipContextService.setUserContextForUser(userPhone, "idAgendamentoFeegow", firstFeegowId);
                        blipContextService.setUserContextForUser(userPhone, "appointmentId", firstFeegowId);
                        if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                            blipContextService.setUserContextForUser(fromIdentity, "idAgendamentoFeegow", firstFeegowId);
                            blipContextService.setUserContextForUser(fromIdentity, "appointmentId", firstFeegowId);
                        }
                        log.info("[CONFIRM-BATCH] IDs salvos no contexto do Blip: {}", firstFeegowId);

                        // Avaliar presença de CPF do paciente Feegow para requiresCpfFallback
                        FeegowPatient patient = patientExternalPort.patientInfo(firstSession.getPatientId());
                        String cpf = (patient != null) ? patient.cpf() : null;
                        boolean hasValidCpf = false;
                        if (cpf != null && !cpf.isBlank()) {
                            String cleanCpf = cpf.replaceAll("\\D", "");
                            if (cleanCpf.length() == 11) {
                                hasValidCpf = true;
                            }
                        }
                        String requiresCpfFallback = hasValidCpf ? "false" : "true";

                        blipContextService.setVariable(userPhone, "requiresCpfFallback", requiresCpfFallback);
                        blipContextService.setContactExtra(userPhone, "requiresCpfFallback", requiresCpfFallback);
                        if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                            blipContextService.setVariable(fromIdentity, "requiresCpfFallback", requiresCpfFallback);
                            blipContextService.setContactExtra(fromIdentity, "requiresCpfFallback", requiresCpfFallback);
                        }

                        // Atualiza também a variável para as identidades de túnel no lote
                        try {
                            for (AppointmentSession groupSession : listaSessoes) {
                                String guid = groupSession.getBlipGuid();
                                if (guid == null || guid.isBlank()) {
                                    guid = groupSession.getBsuid();
                                }
                                if (guid != null && !guid.isBlank()) {
                                    if (guid.contains("@")) {
                                        guid = guid.substring(0, guid.indexOf("@"));
                                    }
                                    guid = guid.trim();
                                    if (guid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                                        String tunnelIdentity = guid + "@tunnel.msging.net";
                                        blipContextService.setVariable(tunnelIdentity, "requiresCpfFallback", requiresCpfFallback);
                                        blipContextService.setContactExtra(tunnelIdentity, "requiresCpfFallback", requiresCpfFallback);
                                    }
                                }
                            }
                        } catch (Exception tunnelEx) {
                            log.warn("[CONFIRM-BATCH] Falha ao atualizar requiresCpfFallback para identidades de túnel no lote: {}", tunnelEx.getMessage());
                        }

                        if (!hasValidCpf) {
                            log.info("[CONTEST-CPF] Paciente sem CPF no prontuário Feegow. requiresCpfFallback definido como true para a identidade {}.", userPhone);
                        } else {
                            log.info("[CONTEST-CPF] Paciente possui CPF válido. requiresCpfFallback definido como false.");
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[CONFIRM-BATCH] Falha ao salvar ID ou verificar CPF no contexto: {}", ex.getMessage());
                }

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
                
                // Atualiza também o Master-State com as identidades baseadas nos GUIDs das sessões do lote
                try {
                    for (AppointmentSession groupSession : listaSessoes) {
                        String guid = groupSession.getBlipGuid();
                        if (guid == null || guid.isBlank()) {
                            guid = groupSession.getBsuid();
                        }
                        if (guid != null && !guid.isBlank()) {
                            if (guid.contains("@")) {
                                guid = guid.substring(0, guid.indexOf("@"));
                            }
                            guid = guid.trim();
                            if (guid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                                String tunnelIdentity = guid + "@tunnel.msging.net";
                                blipContextService.setMasterState(tunnelIdentity, targetBot, confirmSuccessBlockId);
                                log.info("[CONFIRM-BATCH] Master-State atualizado também para a identidade GUID do túnel no lote: {}", tunnelIdentity);
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[CONFIRM-BATCH] Falha ao atualizar Master-State para GUID do túnel no lote: {}", ex.getMessage());
                }

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
                                try {
                                    if (!listaSessoes.isEmpty()) {
                                        blipContextService.setUserContextForUser(tunnelId, "idAgendamentoFeegow", listaSessoes.get(0).getFeegowAppointmentId());
                                        blipContextService.setUserContextForUser(tunnelId, "appointmentId", listaSessoes.get(0).getFeegowAppointmentId());
                                    }
                                } catch (Exception ex) {
                                    log.warn("[CONFIRM-BATCH] Falha ao salvar ID no túnel: {}", ex.getMessage());
                                }
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

            // Avaliar presença de CPF do paciente Feegow para requiresCpfFallback
            String requiresCpfFallbackVal = "true";
            try {
                FeegowPatient patient = patientExternalPort.patientInfo(session.getPatientId());
                String cpf = (patient != null) ? patient.cpf() : null;
                boolean hasValidCpf = false;
                if (cpf != null && !cpf.isBlank()) {
                    String cleanCpf = cpf.replaceAll("\\D", "");
                    if (cleanCpf.length() == 11) {
                        hasValidCpf = true;
                    }
                }
                requiresCpfFallbackVal = hasValidCpf ? "false" : "true";

                if (!hasValidCpf) {
                    log.info("[CONTEST-CPF] Paciente sem CPF no prontuário Feegow. requiresCpfFallback definido como true para a identidade {}.", session.getPhoneNumber());
                } else {
                    log.info("[CONTEST-CPF] Paciente possui CPF válido. requiresCpfFallback definido como false.");
                }
            } catch (Exception ex) {
                log.error("[CONTEST-CPF] Falha ao verificar CPF do paciente no Feegow: {}", ex.getMessage());
            }

            final String requiresCpfFallback = requiresCpfFallbackVal;

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
            
            // Salva o ID do agendamento e CPF no contexto do Blip para persistência
            try {
                blipContextService.setUserContextForUser(userPhone, "idAgendamentoFeegow", session.getFeegowAppointmentId());
                blipContextService.setUserContextForUser(userPhone, "appointmentId", session.getFeegowAppointmentId());
                blipContextService.setVariable(userPhone, "requiresCpfFallback", requiresCpfFallback);
                blipContextService.setContactExtra(userPhone, "requiresCpfFallback", requiresCpfFallback);
                if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                    blipContextService.setUserContextForUser(fromIdentity, "idAgendamentoFeegow", session.getFeegowAppointmentId());
                    blipContextService.setUserContextForUser(fromIdentity, "appointmentId", session.getFeegowAppointmentId());
                    blipContextService.setVariable(fromIdentity, "requiresCpfFallback", requiresCpfFallback);
                    blipContextService.setContactExtra(fromIdentity, "requiresCpfFallback", requiresCpfFallback);
                }
                log.info("[CONFIRM] ID do agendamento e requiresCpfFallback salvos no contexto do Blip: {}", session.getFeegowAppointmentId());
            } catch (Exception ex) {
                log.warn("[CONFIRM] Falha ao salvar ID do agendamento ou CPF no contexto: {}", ex.getMessage());
            }

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
            
            // Reconcilia e atualiza também o Master-State com a identidade baseada no GUID do túnel
            try {
                String guid = null;
                var dbSessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(session.getFeegowAppointmentId());
                if (dbSessionOpt.isPresent()) {
                    guid = dbSessionOpt.get().getBlipGuid();
                    if (guid == null || guid.isBlank()) {
                        guid = dbSessionOpt.get().getBsuid();
                    }
                }
                if (guid == null || guid.isBlank()) {
                    guid = session.getBlipGuid();
                }
                if (guid == null || guid.isBlank()) {
                    guid = session.getBsuid();
                }

                if (guid != null && !guid.isBlank()) {
                    if (guid.contains("@")) {
                        guid = guid.substring(0, guid.indexOf("@"));
                    }
                    guid = guid.trim();
                    if (guid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                        String tunnelIdentity = guid + "@tunnel.msging.net";
                        blipContextService.setMasterState(tunnelIdentity, targetBot, confirmSuccessBlockId);
                        blipContextService.setVariable(tunnelIdentity, "requiresCpfFallback", requiresCpfFallback);
                        blipContextService.setContactExtra(tunnelIdentity, "requiresCpfFallback", requiresCpfFallback);
                        log.info("[CONFIRM] Master-State e requiresCpfFallback atualizados também para a identidade GUID do túnel: {}", tunnelIdentity);
                    } else {
                        log.debug("[CONFIRM] O guid '{}' não é um UUID/GUID válido.", guid);
                    }
                }
            } catch (Exception ex) {
                log.warn("[CONFIRM] Falha ao atualizar Master-State ou requiresCpfFallback para GUID do túnel: {}", ex.getMessage());
            }

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
                            try {
                                blipContextService.setUserContextForUser(tunnelId, "idAgendamentoFeegow", session.getFeegowAppointmentId());
                                blipContextService.setUserContextForUser(tunnelId, "appointmentId", session.getFeegowAppointmentId());
                                blipContextService.setVariable(tunnelId, "requiresCpfFallback", requiresCpfFallback);
                                blipContextService.setContactExtra(tunnelId, "requiresCpfFallback", requiresCpfFallback);
                            } catch (Exception ex) {
                                log.warn("[CONFIRM] Falha ao salvar ID no túnel: {}", ex.getMessage());
                            }
                            log.info("[CONFIRM] Aplicado redirecionamento, Builder Master-State, ID e CPF no túnel: {}", tunnelId);
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
