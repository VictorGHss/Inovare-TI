package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.util.Map;
import java.util.ArrayList;

import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.core.shared.domain.port.output.AuditPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.application.service.ConfirmationStateMachineService;

import java.util.UUID;

import org.springframework.transaction.TransactionException;

/**
 * Caso de Uso orquestrador principal para recepção e roteamento de Webhooks do Blip.
 * Intercepta payloads, valida assinaturas de token de segurança, delega verificações de idempotência
 * e executa pipelines de ação via BlipWebhookActionExecutor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandleBlipWebhookUseCase {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final ProfessionalExternalPort professionalExternalPort;
    private final BlipContextService blipContextService;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final AuditPort auditPort;
    private final BlipIdempotencyService blipIdempotencyService;
    private final BlipWebhookActionExecutor blipWebhookActionExecutor;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final AppointmentExternalPort appointmentExternalPort;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final BlipNotificationService blipNotificationService;

    // Registro auxiliar para carregar dados da sessão e mapeamento do médico de forma rápida,
    // garantindo liberação imediata da conexão com o banco antes do I/O de rede com Feegow/Blip.
    private record SessionDbData(
        AppointmentSession session,
        AppointmentDoctorMapping doctorMapping
    ) {}

    /**
     * @return nome da fila Blip resolvida após o processamento, ou {@code null} se o webhook foi ignorado
     *         (idempotência, ação não reconhecida, etc.)
     */
    public WebhookResult execute(BlipWebhookPayload payload) {
        return execute(payload, false);
    }

    /**
     * @return nome da fila Blip resolvida após processar o médico (ex.: profissional {@code 70} no manual-trigger
     *         retorna {@code Endocrinologia} em fluxo {@code confirm}).
     */
    public WebhookResult execute(BlipWebhookPayload payload, boolean skipTokenValidation) {
        String actionValue = payload.action() != null ? payload.action().trim() : "";
        String rawText = actionValue + " " + (payload.content() != null ? payload.content().toString() : "");
        String rawLower = rawText.toLowerCase();

        boolean isPrepararAtendimento = "preparar_atendimento".equalsIgnoreCase(actionValue)
            || rawLower.contains("a0776d9c-6486-42f3-8a4f-2706f0185908");
        boolean isExibirAgenda = "exibir_agenda".equalsIgnoreCase(actionValue)
            || rawLower.contains("1438bc97-34ef-4337-adf5-e03e463c042c");

        if (isPrepararAtendimento || isExibirAgenda) {
            String from = payload.from();
            if (from != null && !from.isBlank()) {
                String normalizedPhone = from.trim();
                String dbPhone = purifyPhoneNumber(from);
                if (isPrepararAtendimento) {
                    log.info("[WEBHOOK-BLOCK] Interceptando Preparar_Atendimento para {} (DB Phone: {})", normalizedPhone, dbPhone);
                    boolean isGroup = false;
                    UUID groupId = null;
                    java.util.List<AppointmentSession> activeSessions = transactionTemplate.execute(status ->
                        appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
                    );
                    if (activeSessions != null) {
                        for (AppointmentSession activeSession : activeSessions) {
                            java.util.List<NotificationGroup> groups =
                                notificationGroupRepository.findBySessionId(activeSession.getId());
                            if (groups != null && !groups.isEmpty()) {
                                isGroup = true;
                                groupId = groups.get(0).getGroupId();
                                break;
                            }
                        }
                    }
                    blipContextService.setUserContextForUser(normalizedPhone, "isGroupFlow", String.valueOf(isGroup));
                    if (isGroup && groupId != null) {
                        blipContextService.setUserContextForUser(normalizedPhone, "groupId", groupId.toString());
                    } else {
                        blipContextService.setUserContextForUser(normalizedPhone, "groupId", "");
                    }
                    return new WebhookResult("", "", "", "", "processed", "");
                } else {
                    log.info("[WEBHOOK-BLOCK] Interceptando Exibir_Agenda para {} (DB Phone: {})", normalizedPhone, dbPhone);
                    UUID groupId = null;
                    java.util.List<String> activeAppointmentIds = new ArrayList<>();
                    java.util.List<AppointmentSession> activeSessions = transactionTemplate.execute(status ->
                        appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
                    );
                    if (activeSessions != null) {
                        for (AppointmentSession activeSession : activeSessions) {
                            if (activeSession.getFeegowAppointmentId() != null) {
                                activeAppointmentIds.add(activeSession.getFeegowAppointmentId());
                            }
                            if (activeSession.getCurrentGroupId() != null) {
                                groupId = activeSession.getCurrentGroupId();
                                break;
                            }
                        }
                    }

                    if (groupId == null) {
                        NotificationGroup latestGroup = transactionTemplate.execute(status ->
                            notificationGroupRepository.findLatestByPhone(dbPhone).orElse(null)
                        );
                        if (latestGroup != null) {
                            groupId = latestGroup.getGroupId();
                            log.warn("[WEBHOOK-BLOCK] groupId recuperado via NotificationGroup para {}. groupId={}",
                                dbPhone, groupId);
                        }
                    }

                    if (groupId == null) {
                        log.error("[WEBHOOK-BLOCK] groupId nulo apos retry. phone={}, feegowAppointments={}",
                            dbPhone, activeAppointmentIds);
                        blipContextService.setUserContextForUser(
                            normalizedPhone,
                            "lista_detalhada",
                            "Ops, nao encontrei seus agendamentos agora, aguarde um instante."
                        );
                        return new WebhookResult("", "", "", "", "processed", "");
                    }

                    java.util.List<AppointmentSession> groupedSessions = new ArrayList<>();
                    java.util.List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
                    for (NotificationGroup g : groups) {
                        appointmentSessionRepository.findById(g.getSessionId()).ifPresent(groupedSessions::add);
                    }

                    groupedSessions.sort((s1, s2) -> {
                        if (s1.getAppointmentAt() == null && s2.getAppointmentAt() == null) return 0;
                        if (s1.getAppointmentAt() == null) return 1;
                        if (s2.getAppointmentAt() == null) return -1;
                        return s1.getAppointmentAt().compareTo(s2.getAppointmentAt());
                    });
                    java.util.List<String> details = new ArrayList<>();
                    for (AppointmentSession s : groupedSessions) {
                        if (s.getAppointmentAt() == null) {
                            continue;
                        }
                        String time = s.getAppointmentAt().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                        String specialty = "Consulta";
                        String doctorName = "Clínica Inovare";
                        var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(s.getDoctorProfissionalId());
                        if (mappingOpt.isPresent()) {
                            var mapping = mappingOpt.get();
                            String queue = mapping.getBlipQueueId();
                            if (queue != null && !queue.isBlank()) {
                                specialty = queue.trim();
                            }
                            String docName = mapping.getProfissionalNome();
                            if (docName != null && !docName.isBlank() && !"null".equalsIgnoreCase(docName.trim())) {
                                doctorName = docName.trim();
                            }
                        }
                        details.add("🔹 " + time + " - " + specialty + " - " + doctorName + " (ID: " + s.getId().toString() + ")");
                    }
                    String listaDetalhada = String.join("\n", details);
                    if (listaDetalhada == null || listaDetalhada.isBlank()) {
                        listaDetalhada = "Ops, nao encontrei seus agendamentos agora, aguarde um instante.";
                    }
                    blipContextService.setUserContextForUser(normalizedPhone, "lista_detalhada", listaDetalhada);
                    log.info("[WEBHOOK-BLOCK] Injetada lista_detalhada='{}' para {}", listaDetalhada, normalizedPhone);
                    return new WebhookResult("", "", "", "", "processed", "");
                }
            }
            return new WebhookResult("", "", "", "", "processed", "");
        }

        if (!skipTokenValidation) {
            String expectedToken = appointmentMotorProperties.getSecurity().getWebhookToken();
            if (expectedToken != null && !expectedToken.isBlank() && !expectedToken.equals(payload.token())) {
                log.warn("Token de webhook inválido.");
                throw new SecurityException("Invalid token");
            }
            if (expectedToken != null && !expectedToken.isBlank()) {
                auditPort.record(
                        "APPOINTMENT_MOTOR",
                        "ASSINATURA_VALIDADA",
                        "Assinatura do webhook validada. messageId=" + payload.messageId(),
                        resolveTraceId());
            }
        }

        // 1. Verificação de idempotência do messageId (Ignora se for evento duplicado)
        if (!blipIdempotencyService.registerIfFirstTime(payload.messageId())) {
            log.debug("Ação ignorada no webhook (idempotência). messageId={}", payload.messageId());
            return null;
        }

        String fromPhone = payload.from();
        String action = payload.action() != null ? payload.action().trim() : "";

        if (action.isBlank()) {
            String fallbackAction = resolveActionFromContent(payload.content());
            if (fallbackAction != null && !fallbackAction.isBlank()) {
                action = fallbackAction.trim();
            }
        }

        java.util.regex.Pattern uuidPattern = java.util.regex.Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        if (uuidPattern.matcher(action).matches()) {
            log.info("[WEBHOOK] UUID puro detectado na ação. Carregando sessão de agendamento: {}", action);
            UUID sessionId = UUID.fromString(action);
            AppointmentSession session = transactionTemplate.execute(status ->
                appointmentSessionRepository.findById(sessionId).orElse(null)
            );

            if (session != null) {
                log.info("[WEBHOOK] Sessão encontrada para UUID puro. Enviando template individual para o agendamento Feegow={}", session.getFeegowAppointmentId());
                sendAppointmentTemplateUseCase.execute(session, br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory.CONFIRMATION);
            } else {
                log.warn("[WEBHOOK] Nenhuma sessão encontrada para a ação de UUID: {}", action);
            }
            return new WebhookResult("", "", "", "", "individual_appointment_selected", "");
        }

        String normalizedAction = action.trim().toLowerCase();

        // Intercepção de respostas do Nudge para redirecionar deterministamente ao Desk (Atendimento Humano)
        boolean isManterAgendamento = normalizedAction.equals("manter agendamento")
                || normalizedAction.contains("manter_agendamento");
        boolean isCancelarConsulta = normalizedAction.equals("cancelar consulta")
                || normalizedAction.contains("cancelar_consulta");

        if (isManterAgendamento || isCancelarConsulta) {
            if (fromPhone != null && !fromPhone.isBlank()) {
                String dbPhone = purifyPhoneNumber(fromPhone);
                log.info("[WEBHOOK-NUDGE] Interceptando resposta do nudge: '{}' para o telefone: {} (DB Phone: {})",
                    action, fromPhone, dbPhone);
                
                // 1. Busca todas as sessões ativas do contato em transação microscópica
                java.util.List<AppointmentSession> activeSessions = transactionTemplate.execute(status -> 
                    appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
                );
                
                if (activeSessions != null && !activeSessions.isEmpty()) {
                    log.info("[WEBHOOK-NUDGE] Encontradas {} sessões ativas para processar.", activeSessions.size());
                    
                    // 2. Atualiza os status locais e externos
                    for (AppointmentSession session : activeSessions) {
                        try {
                            if (isManterAgendamento) {
                                // Atualiza local para CONFIRMED e Feegow para '7'
                                log.info("[WEBHOOK-NUDGE] Confirmando sessão local e Feegow para sessionId={}, feegowAppointmentId={}",
                                    session.getId(), session.getFeegowAppointmentId());
                                transactionTemplate.executeWithoutResult(status -> {
                                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                                    if (lockedSession != null) {
                                        confirmationStateMachineService.markConfirmed(lockedSession);
                                        appointmentSessionRepository.save(lockedSession);
                                    }
                                });
                                appointmentExternalPort.updateAppointmentStatus(session.getFeegowAppointmentId(), "7");
                            } else {
                                // Atualiza local para CANCELED e Feegow para '11' (desmarcado)
                                log.info("[WEBHOOK-NUDGE] Cancelando sessão local e Feegow para sessionId={}, feegowAppointmentId={}",
                                    session.getId(), session.getFeegowAppointmentId());
                                transactionTemplate.executeWithoutResult(status -> {
                                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                                    if (lockedSession != null) {
                                        confirmationStateMachineService.markCanceled(lockedSession);
                                        appointmentSessionRepository.save(lockedSession);
                                    }
                                });
                                appointmentExternalPort.updateStatus(session.getFeegowAppointmentId(), 11);
                            }
                        } catch (Exception ex) {
                            log.error("[WEBHOOK-NUDGE] Falha ao atualizar sessão no lote. sessionId={}, erro={}",
                                session.getId(), ex.getMessage(), ex);
                        }
                    }

                    // 3. Forçar Transbordo para o Atendimento Humano (Desk) via LIME
                    blipContextService.setMasterState(fromPhone, "desk@msging.net", "644d54dd-aefd-478b-93eb-10081acdd387");
                    
                    log.info("[WEBHOOK-NUDGE] Transbordo concluído para {} direcionando ao Bloco: 'desk:644d54dd-aefd-478b-93eb-10081acdd387'", fromPhone);
                } else {
                    log.warn("[WEBHOOK-NUDGE] Resposta de Nudge '{}' recebida de {}, mas nenhuma sessão ativa encontrada.",
                        action, fromPhone);
                }
            } else {
                log.warn("[WEBHOOK-NUDGE] Resposta de Nudge '{}' recebida sem 'fromPhone' identificado.", action);
            }
            return new WebhookResult("", "", "", "", "nudge_response_processed", "");
        }


        // Intercepção de texto livre / intenções em português de Confirmação:
        if (normalizedAction.equals("sim")
                || normalizedAction.equals("confirmar")
                || normalizedAction.equals("confirm")
                || normalizedAction.contains("confirmar presença")
                || normalizedAction.contains("confirmar consulta")) {
            String resolvedId = payload.appointmentId();
            if (resolvedId == null || resolvedId.isBlank()) {
                fromPhone = payload.from();
                if (fromPhone != null && !fromPhone.isBlank()) {
                    String dbPhone = purifyPhoneNumber(fromPhone);
                    java.util.List<AppointmentSession> activeSessions = transactionTemplate.execute(status -> 
                        appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
                    );
                    if (activeSessions != null && !activeSessions.isEmpty()) {
                        resolvedId = activeSessions.get(0).getFeegowAppointmentId();
                    }
                }
            }
            if (resolvedId != null && !resolvedId.isBlank()) {
                log.info("[WEBHOOK] Texto livre de confirmação '{}' interceptado. Mapeando para confirm_{}", action, resolvedId);
                action = "confirm_" + resolvedId;
            } else {
                log.warn("[WEBHOOK] Texto livre de confirmação '{}' recebido, mas nenhuma sessão ativa ou ID encontrado.", action);
                return null;
            }
        }

        // Intercepção de texto livre / intenções em português de Cancelamento:
        if (normalizedAction.equals("cancelar")
                || normalizedAction.equals("cancel")
                || normalizedAction.contains("cancelar presença")
                || normalizedAction.contains("cancelar consulta")) {
            String resolvedId = payload.appointmentId();
            if (resolvedId == null || resolvedId.isBlank()) {
                fromPhone = payload.from();
                if (fromPhone != null && !fromPhone.isBlank()) {
                    String dbPhone = purifyPhoneNumber(fromPhone);
                    java.util.List<AppointmentSession> activeSessions = transactionTemplate.execute(status -> 
                        appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
                    );
                    if (activeSessions != null && !activeSessions.isEmpty()) {
                        resolvedId = activeSessions.get(0).getFeegowAppointmentId();
                    }
                }
            }
            if (resolvedId != null && !resolvedId.isBlank()) {
                log.info("[WEBHOOK] Texto livre de cancelamento '{}' interceptado. Mapeando para cancel_{}", action, resolvedId);
                action = "cancel_" + resolvedId;
            } else {
                log.warn("[WEBHOOK] Texto livre de cancelamento '{}' recebido, mas nenhuma sessão ativa ou ID encontrado.", action);
                return null;
            }
        }

        // Intercepção de texto livre: "Solicitar Alteração" sem payload de botão
        // Resolve o ID da sessão ativa mais recente para o telefone do usuário
        if (action.equalsIgnoreCase("Solicitar Alteração")
                || action.equalsIgnoreCase("Solicitar Alteracao")
                || action.equalsIgnoreCase("alterar")
                || action.toLowerCase().contains("solicitar alter")) {
            fromPhone = payload.from();
            if (fromPhone != null && !fromPhone.isBlank()) {
                String normalizedPhone = fromPhone.trim();
                String dbPhone = purifyPhoneNumber(fromPhone);
                // FASE 1A: Leitura rápida no banco (micro-transação)
                java.util.List<AppointmentSession> activeSessions = transactionTemplate.execute(status -> 
                    appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
                );
                
                if (activeSessions != null && !activeSessions.isEmpty()) {
                    String resolvedId = activeSessions.get(0).getFeegowAppointmentId();
                    log.info("[WEBHOOK] Texto livre '{}' interceptado. Mapeando para alter_{} (sessão mais recente de {})",
                        action, resolvedId, normalizedPhone);
                    action = "alter_" + resolvedId;
                } else {
                    log.warn("[WEBHOOK] Texto livre '{}' recebido mas nenhuma sessão ativa encontrada para {}", action, normalizedPhone);
                    return null;
                }
            } else {
                log.warn("[WEBHOOK] Texto livre '{}' recebido sem 'from' identificável.", action);
                return null;
            }
        }

        // Interceptação do botão "Ver Agendamentos" do template de grupo
        if (action.toLowerCase().startsWith("ver_agenda_")) {
            String groupIdStr = action.substring("ver_agenda_".length()).trim();
            log.info("[WEBHOOK] Clique em 'Ver Agendamentos' recebido para groupId={}", groupIdStr);
            try {
                UUID groupId = UUID.fromString(groupIdStr);
                if (fromPhone != null && !fromPhone.isBlank()) {
                    String normalizedPhone = fromPhone.trim();
                    String dbPhone = purifyPhoneNumber(fromPhone);

                    // 1. Carrega as sessões do grupo e gera a lista_detalhada
                    java.util.List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
                    java.util.List<AppointmentSession> groupedSessions = new ArrayList<>();
                    for (NotificationGroup g : groups) {
                        appointmentSessionRepository.findById(g.getSessionId()).ifPresent(groupedSessions::add);
                    }

                    groupedSessions.sort((s1, s2) -> {
                        if (s1.getAppointmentAt() == null && s2.getAppointmentAt() == null) return 0;
                        if (s1.getAppointmentAt() == null) return 1;
                        if (s2.getAppointmentAt() == null) return -1;
                        return s1.getAppointmentAt().compareTo(s2.getAppointmentAt());
                    });

                    java.util.List<String> details = new ArrayList<>();
                    for (AppointmentSession s : groupedSessions) {
                        if (s.getAppointmentAt() == null) {
                            continue;
                        }
                        String time = s.getAppointmentAt().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                        String specialty = "Consulta";
                        String doctorName = "Clínica Inovare";
                        var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(s.getDoctorProfissionalId());
                        if (mappingOpt.isPresent()) {
                            var mapping = mappingOpt.get();
                            String queue = mapping.getBlipQueueId();
                            if (queue != null && !queue.isBlank()) {
                                specialty = queue.trim();
                            }
                            String docName = mapping.getProfissionalNome();
                            if (docName != null && !docName.isBlank() && !"null".equalsIgnoreCase(docName.trim())) {
                                doctorName = docName.trim();
                            }
                        }
                        details.add("🔹 " + time + " - " + specialty + " - " + doctorName + " (ID: " + s.getId().toString() + ")");
                    }

                    String listaDetalhada = String.join("\n", details);
                    if (listaDetalhada.isBlank()) {
                        listaDetalhada = "Ops, não encontrei seus agendamentos agora, aguarde um instante.";
                    }

                    // 2. Injeta no contexto do usuário
                    blipContextService.setUserContextForUser(fromPhone.trim(), "lista_detalhada", listaDetalhada);
                    log.info("[WEBHOOK] Injetada lista_detalhada para ver_agenda_{}", groupIdStr);

                    transactionTemplate.executeWithoutResult(status -> {
                        java.util.List<AppointmentSession> activeSessions =
                            appointmentSessionRepository.findActiveByPhoneNumber(dbPhone);
                        if (activeSessions == null || activeSessions.isEmpty()) {
                            log.warn("[WEBHOOK] Nenhuma sessao ativa encontrada para {} ao salvar groupId={}",
                                normalizedPhone, groupIdStr);
                            return;
                        }
                        for (AppointmentSession session : activeSessions) {
                            session.setCurrentGroupId(groupId);
                            appointmentSessionRepository.save(session);
                        }
                    });
                    log.info("[WEBHOOK] groupId salvo no banco para {}. groupId={}", normalizedPhone, groupIdStr);

                    // 3. Disparo ativo de texto — substitui o comando silencioso de master-state
                    log.info("[WEBHOOK] Disparando mensagem de texto ativa para {} com a lista de agendamentos.", fromPhone);
                    blipNotificationService.sendPlainTextMessage(fromPhone.trim(), listaDetalhada);
                } else {
                    log.warn("[WEBHOOK] fromPhone ausente ao salvar groupId={}", groupIdStr);
                }
            } catch (IllegalArgumentException e) {
                log.error("[WEBHOOK] groupId inválido no payload ver_agenda_. action={}", action);
            }
            return new WebhookResult("", "", "", "", "ver_agenda", "");
        }

        String normalizedActionLower = action.toLowerCase();
        boolean isGroupConfirm = normalizedActionLower.startsWith("confirm_group_");
        boolean isGroupAlter = normalizedActionLower.startsWith("alter_group_");

        String actionType;
        String appointmentId;

        if (isGroupConfirm || isGroupAlter) {
            String groupPrefix = isGroupConfirm ? "confirm_group_" : "alter_group_";
            actionType = isGroupConfirm ? "confirm" : "alter";
            String groupIdStr = action.substring(groupPrefix.length()).trim();
            String resolvedAppointmentId = null;
            try {
                UUID groupId = UUID.fromString(groupIdStr);
                java.util.List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
                if (groups != null && !groups.isEmpty()) {
                    UUID firstSessionId = groups.get(0).getSessionId();
                    AppointmentSession firstSession = transactionTemplate.execute(status ->
                            appointmentSessionRepository.findById(firstSessionId).orElse(null)
                    );
                    if (firstSession != null) {
                        resolvedAppointmentId = firstSession.getFeegowAppointmentId();
                    }
                }
            } catch (TransactionException e) {
                log.error("[WEBHOOK] Falha ao processar grupo de confirmação para ação: " + action, e);
            }
            if (resolvedAppointmentId == null) {
                log.warn("[WEBHOOK] Nenhuma sessão encontrada para a ação de grupo: {}", action);
                return null;
            }
            appointmentId = resolvedAppointmentId;
        } else if ("group_view_fallback".equalsIgnoreCase(action)) {
            log.info("[WEBHOOK] Interceptando group_view_fallback para resolucao de tunel.");
            String realPhone = null;
            if (payload.metadata() != null) {
                Object origFrom = payload.metadata().get("#tunnel.originalFrom");
                if (origFrom != null) {
                    realPhone = origFrom.toString().trim();
                    log.info("[WEBHOOK] Telefone real recuperado via #tunnel.originalFrom: {}", realPhone);
                }
            }
            
            if (realPhone == null || realPhone.isBlank()) {
                realPhone = fromPhone;
            }

            if (realPhone != null) {
                if (realPhone.contains("@")) {
                    realPhone = realPhone.substring(0, realPhone.indexOf("@")).trim();
                }
                realPhone = purifyPhoneNumber(realPhone);
            }

            log.info("[WEBHOOK] Telefone real purificado: {}", realPhone);

            if (realPhone != null && !realPhone.isBlank()) {
                final String targetPhone = realPhone;
                // Buscar o último grupo de notificações ativo para esse destinatário
                NotificationGroup latestGroup = transactionTemplate.execute(status ->
                    notificationGroupRepository.findLatestByPhone(targetPhone).orElse(null)
                );

                if (latestGroup != null) {
                    UUID groupId = latestGroup.getGroupId();
                    log.info("[WEBHOOK] Último grupo ativo encontrado: {} para telefone: {}", groupId, targetPhone);

                    java.util.List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
                    java.util.List<AppointmentSession> groupedSessions = new ArrayList<>();
                    for (NotificationGroup g : groups) {
                        appointmentSessionRepository.findById(g.getSessionId()).ifPresent(groupedSessions::add);
                    }

                    groupedSessions.sort((s1, s2) -> {
                        if (s1.getAppointmentAt() == null && s2.getAppointmentAt() == null) return 0;
                        if (s1.getAppointmentAt() == null) return 1;
                        if (s2.getAppointmentAt() == null) return -1;
                        return s1.getAppointmentAt().compareTo(s2.getAppointmentAt());
                    });

                    java.util.List<String> details = new ArrayList<>();
                    for (AppointmentSession s : groupedSessions) {
                        if (s.getAppointmentAt() == null) {
                            continue;
                        }
                        String time = s.getAppointmentAt().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                        String specialty = "Consulta";
                        String doctorName = "Clínica Inovare";
                        var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(s.getDoctorProfissionalId());
                        if (mappingOpt.isPresent()) {
                            var mapping = mappingOpt.get();
                            String queue = mapping.getBlipQueueId();
                            if (queue != null && !queue.isBlank()) {
                                specialty = queue.trim();
                            }
                            String docName = mapping.getProfissionalNome();
                            if (docName != null && !docName.isBlank() && !"null".equalsIgnoreCase(docName.trim())) {
                                doctorName = docName.trim();
                            }
                        }
                        details.add("🔹 " + time + " - " + specialty + " - " + doctorName + " (ID: " + s.getId().toString() + ")");
                    }

                    String listaDetalhada = String.join("\n", details);
                    if (listaDetalhada.isBlank()) {
                        listaDetalhada = "Ops, não encontrei seus agendamentos agora, aguarde um instante.";
                    }

                    if (fromPhone != null && !fromPhone.isBlank()) {
                        blipContextService.setUserContextForUser(fromPhone.trim(), "lista_detalhada", listaDetalhada);
                        log.info("[WEBHOOK] Fallback de visualização de grupo injetado para {}. groupId={}", fromPhone, groupId);
                        // Disparo ativo de texto — substitui o comando silencioso de master-state
                        log.info("[WEBHOOK] Disparando mensagem de texto ativa (fallback) para {} com a lista de agendamentos.", fromPhone);
                        blipNotificationService.sendPlainTextMessage(fromPhone.trim(), listaDetalhada);
                    }
                } else {
                    log.warn("[WEBHOOK] Nenhum grupo ativo encontrado para o telefone real: {}", targetPhone);
                }
            } else {
                log.warn("[WEBHOOK] Não foi possível resolver o telefone real do paciente.");
            }
            return new WebhookResult("", "", "", "", "group_view_processed", "");
        } else if (action.toLowerCase().startsWith("group_view_")) {
            String groupIdStr = action.substring("group_view_".length()).trim();
            log.info("[WEBHOOK] Clique em visualização de grupo recebido para groupId={}", groupIdStr);
            try {
                UUID groupId = UUID.fromString(groupIdStr);
                java.util.List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
                java.util.List<AppointmentSession> groupedSessions = new ArrayList<>();
                for (NotificationGroup g : groups) {
                    appointmentSessionRepository.findById(g.getSessionId()).ifPresent(groupedSessions::add);
                }

                groupedSessions.sort((s1, s2) -> {
                    if (s1.getAppointmentAt() == null && s2.getAppointmentAt() == null) return 0;
                    if (s1.getAppointmentAt() == null) return 1;
                    if (s2.getAppointmentAt() == null) return -1;
                    return s1.getAppointmentAt().compareTo(s2.getAppointmentAt());
                });

                java.util.List<String> details = new ArrayList<>();
                for (AppointmentSession s : groupedSessions) {
                    if (s.getAppointmentAt() == null) {
                        continue;
                    }
                    String time = s.getAppointmentAt().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                    String specialty = "Consulta";
                    String doctorName = "Clínica Inovare";
                    var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(s.getDoctorProfissionalId());
                    if (mappingOpt.isPresent()) {
                        var mapping = mappingOpt.get();
                        String queue = mapping.getBlipQueueId();
                        if (queue != null && !queue.isBlank()) {
                            specialty = queue.trim();
                        }
                        String docName = mapping.getProfissionalNome();
                        if (docName != null && !docName.isBlank() && !"null".equalsIgnoreCase(docName.trim())) {
                            doctorName = docName.trim();
                        }
                    }
                    details.add("🔹 " + time + " - " + specialty + " - " + doctorName + " (ID: " + s.getId().toString() + ")");
                }

                String listaDetalhada = String.join("\n", details);
                if (listaDetalhada.isBlank()) {
                    listaDetalhada = "Ops, não encontrei seus agendamentos agora, aguarde um instante.";
                }

                if (fromPhone != null && !fromPhone.isBlank()) {
                    blipContextService.setUserContextForUser(fromPhone.trim(), "lista_detalhada", listaDetalhada);
                    log.info("[WEBHOOK] Injetada lista_detalhada para grupo {}. Disparando mensagem ativa.", groupIdStr);
                    // Disparo ativo de texto — substitui o comando silencioso de master-state
                    blipNotificationService.sendPlainTextMessage(fromPhone.trim(), listaDetalhada);
                }
            } catch (IllegalArgumentException e) {
                log.error("[WEBHOOK] groupId inválido no payload group_view_. action={}", action);
            }
            return new WebhookResult("", "", "", "", "group_view_processed", "");
        } else {
            // Captura confirm_{ID}, alter_{ID} ou cancel_{ID}
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)(confirm|alter|cancel)_(\\d+(?:\\.\\d+)?)");
            java.util.regex.Matcher matcher = pattern.matcher(action);

            if (!matcher.find()) {
                log.debug("[WEBHOOK] Ação ignorada (não é confirm_, alter_ nem cancel_). action='{}'", action);
                return null;
            }

            actionType   = matcher.group(1).toLowerCase(); // "confirm", "alter" ou "cancel"
            String rawId        = matcher.group(2);
            appointmentId = normalizeFeegowAppointmentId(rawId);

            if (appointmentId == null || appointmentId.isBlank()) {
                log.warn("[WEBHOOK] Payload {}_  recebido sem ID válido. action={}", actionType, action);
                return null;
            }
        }

        // 2. Trava atômica de concorrência / Spin-Wait com cache integrado
        if (!blipIdempotencyService.tryAcquireLock(appointmentId)) {
            return blipIdempotencyService.getCachedResultOrSpinWait(appointmentId, actionType);
        }

        log.info("[WEBHOOK] Processando ação '{}' para agendamento ID={}", actionType, appointmentId);

        // FASE 1B: Leitura transacional em micro-transação.
        // Busca a sessão e o mapeamento do médico no banco e libera a conexão HikariCP imediatamente.
        SessionDbData dbData;
        try {
            dbData = transactionTemplate.execute(status -> {
                AppointmentSession session = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId)
                        .orElseThrow(() -> new NotFoundException("Sessão não encontrada para appointmentId=" + appointmentId));
                AppointmentDoctorMapping doctorMapping = appointmentDoctorMappingRepository
                        .findByProfissionalId(session.getDoctorProfissionalId())
                        .orElse(null);
                return new SessionDbData(session, doctorMapping);
            });
        } catch (NotFoundException | DataAccessException | IllegalStateException ex) {
            log.error("Erro na leitura transacional inicial do webhook para appointmentId={}. Detalhes: {}", appointmentId, ex.getMessage(), ex);
            return null;
        }

        if (dbData == null) {
            return null;
        }

        String doctorName = null;
        String queue = null;

        if (dbData.doctorMapping() != null) {
            doctorName = dbData.doctorMapping().getProfissionalNome();
            queue = dbData.doctorMapping().getBlipQueueId();
        }

        // FASE 2: Chamada HTTP Externa à Feegow (Fora de Transação)
        if (doctorName == null || doctorName.isBlank()) {
            try {
                doctorName = professionalExternalPort.getProfessionalName(dbData.session().getDoctorProfissionalId());
            } catch (RestClientException | IllegalStateException e) {
                log.warn("Não foi possível buscar o nome do médico na Feegow, usando fallback. erro={}", e.getMessage());
                auditPort.record(
                        "APPOINTMENT_MOTOR",
                        "CIRCUIT_BREAKER_FALLBACK",
                        "Fallback ao buscar nome do profissional na Feegow. appointmentId=" + appointmentId
                                + ", erro=" + safeMessage(e),
                        resolveTraceId());
            }
        }

        if (doctorName == null || doctorName.isBlank()) {
            doctorName = "Clínica Inovare";
        }
        doctorName = cleanDoctorName(doctorName);

        if (queue == null || queue.isBlank() || "null".equalsIgnoreCase(queue.trim()) || queue.contains("\u200E")) {
            queue = "Recepção Central / Suporte";
            log.warn("[QUEUE WARNING] Fila não encontrada no banco para o médico {}, usando fallback: {}", dbData.session().getDoctorProfissionalId(), queue);
        }

        queue = blipContextService.cleanQueueName(queue);
        if (queue.isBlank()) {
            queue = "Recepção Central / Suporte";
        }

        String dispatchIdentity = resolveDispatchIdentity(payload.from(), dbData.session());

        // Delegação de toda a pipeline de execução para o executor especializado
        return blipWebhookActionExecutor.execute(
                actionType,
                action,
                appointmentId,
                dbData.session(),
                doctorName,
                queue,
                dispatchIdentity
        );
    }

    private String normalizeFeegowAppointmentId(String feegowAppointmentId) {
        if (feegowAppointmentId == null) {
            return "";
        }
        String normalized = feegowAppointmentId.trim();
        if (normalized.matches("^\\d+\\.0+$")) {
            return normalized.substring(0, normalized.indexOf('.'));
        }
        return normalized;
    }

    private String resolveDispatchIdentity(String from, AppointmentSession session) {
        String direct = normalizeDispatchIdentity(from);
        if (direct != null) {
            return direct;
        }
        String sessionPhone = session != null ? session.getPhoneNumber() : null;
        String fallback = normalizeDispatchIdentity(sessionPhone);
        if (fallback == null) {
            log.warn("Identidade de disparo inválida. from={}, sessionPhone={}", from, sessionPhone);
        }
        return fallback;
    }

    private String normalizeDispatchIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            return null;
        }
        return identity.trim();
    }

    private String resolveActionFromContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String text) {
            return text;
        }
        Map<String, Object> contentMap = toMap(content);
        if (contentMap == null) {
            return null;
        }
        String replyValue = null;
        Object replied = contentMap.get("replied");
        if (replied instanceof Map<?, ?> repliedMap) {
            replyValue = asText(repliedMap.get("value"));
        }
        return firstNonBlank(
            replyValue,
            asText(contentMap.get("text")),
            asText(contentMap.get("value")),
            asText(contentMap.get("payload")),
            asText(contentMap.get("id"))
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return null;
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        try {
            return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String cleanDoctorName(String doctorName) {
        if (doctorName == null || doctorName.isBlank()) {
            return "Clínica Inovare";
        }
        String clean = doctorName.trim();
        clean = clean.replaceAll("(?i)^(Dr\\.|Dra\\.|Dr|Dra)\\s+", "");
        return clean.trim();
    }

    private String purifyPhoneNumber(String originalPhone) {
        if (originalPhone == null || originalPhone.isBlank()) {
            return "";
        }
        
        String trimmed = originalPhone.trim();
        if (trimmed.contains("@")) {
            trimmed = trimmed.substring(0, trimmed.indexOf('@')).trim();
        }
        
        // Remove non-digit characters
        String digitsOnly = trimmed.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) {
            return "";
        }
        
        if (digitsOnly.startsWith("55")) {
            return "+" + digitsOnly;
        }
        
        return "+55" + digitsOnly;
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("trace_id");
        }
        return traceId;
    }

    private String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) {
            return "-";
        }
        return ex.getMessage();
    }

    public record BlipWebhookPayload(String messageId, String appointmentId, String action, String from, String token, Object content, Map<String, Object> metadata) {
    }

    public record WebhookResult(String queue, String patientName, String patientCPF, String patientBirthdate, String action, String doctorName) {
    }
}
