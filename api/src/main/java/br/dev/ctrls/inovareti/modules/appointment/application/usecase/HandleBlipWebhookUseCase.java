package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.core.shared.domain.port.output.AuditPort;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipAppointmentFormatter;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipGroupActionHandler;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipIdentityReconciler;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNudgeResponseHandler;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipPayloadParser;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipTextSanitizer;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookActionExecutor;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final TransactionTemplate transactionTemplate;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final BlipProperties blipProperties;
    private final BlipTextSanitizer blipTextSanitizer;
    private final BlipIdentityReconciler blipIdentityReconciler;
    private final BlipPayloadParser blipPayloadParser;
    private final BlipNudgeResponseHandler blipNudgeResponseHandler;
    private final BlipGroupActionHandler blipGroupActionHandler;
    private final BlipAppointmentFormatter blipAppointmentFormatter;

    private record SessionDbData(
        AppointmentSession session,
        AppointmentDoctorMapping doctorMapping
    ) {}

    public WebhookResult execute(BlipWebhookPayload payload) {
        return execute(payload, false);
    }

    /**
     * Ponto de entrada para execução do processamento do Webhook do Blip.
     */
    public WebhookResult execute(BlipWebhookPayload payload, boolean skipTokenValidation) {
        String actionValue = payload.action() != null ? payload.action().trim() : "";
        String rawText = actionValue + " " + (payload.content() != null ? payload.content().toString() : "");
        String rawLower = rawText.toLowerCase();

        String prepararUuid = blipProperties.getBlocks().getPrepararAtendimento();
        String exibirUuid = blipProperties.getBlocks().getExibirAgenda();

        boolean isPrepararAtendimento = "preparar_atendimento".equalsIgnoreCase(actionValue)
            || (prepararUuid != null && java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(prepararUuid.toLowerCase()) + "\\b").matcher(rawLower).find());
        boolean isExibirAgenda = "exibir_agenda".equalsIgnoreCase(actionValue)
            || (exibirUuid != null && java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(exibirUuid.toLowerCase()) + "\\b").matcher(rawLower).find());

        if (isPrepararAtendimento || isExibirAgenda) {
            return handlePrepararOuExibir(payload, isPrepararAtendimento);
        }

        if (!skipTokenValidation) {
            validateWebhookToken(payload);
        }

        if (!blipIdempotencyService.registerIfFirstTime(payload.messageId())) {
            log.debug("Ação ignorada no webhook (idempotência). messageId={}", payload.messageId());
            return null;
        }

        String fromPhone = payload.from();
        String action = payload.action() != null ? payload.action().trim() : "";

        if (action.isBlank()) {
            String fallbackAction = blipPayloadParser.resolveActionFromContent(payload.content());
            if (fallbackAction != null && !fallbackAction.isBlank()) {
                action = fallbackAction.trim();
            }
        }

        java.util.regex.Pattern uuidPattern = java.util.regex.Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        if (uuidPattern.matcher(action).matches()) {
            String msgType = payload.type();
            boolean isLegitimateType = "text/plain".equalsIgnoreCase(msgType)
                    || "application/vnd.lime.reply+json".equalsIgnoreCase(msgType);
            if (isLegitimateType) {
                WebhookResult result = handleUuidAction(action);
                if (result != null) {
                    return result;
                }
                WebhookResult groupResult = blipGroupActionHandler.handleGroupAction(action, fromPhone, payload.bsuid(), payload.metadata());
                if (groupResult != null) {
                    return groupResult;
                }
            } else {
                log.debug("[WEBHOOK] Ignorando UUID de ação '{}' pois o tipo de mensagem '{}' não é legítimo para cliques.", action, msgType);
            }
        }

        String normalizedAction = action.trim().toLowerCase();

        if (blipNudgeResponseHandler.handleNudgeResponse(normalizedAction, action, fromPhone, payload.bsuid())) {
            return new WebhookResult("", "", "", "", "nudge_response_processed", "");
        }

        action = resolveTextIntentions(normalizedAction, action, payload);

        WebhookResult groupResult = blipGroupActionHandler.handleGroupAction(action, fromPhone, payload.bsuid(), payload.metadata());
        if (groupResult != null) {
            return groupResult;
        }

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)(confirm|alter|cancel)_(\\d+(?:\\.\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(action);

        if (!matcher.find()) {
            log.debug("[WEBHOOK] Ação ignorada (não é confirm_, alter_ nem cancel_). action='{}'", action);
            return null;
        }

        String actionType = matcher.group(1).toLowerCase();
        String rawId = matcher.group(2);
        String appointmentId = blipPayloadParser.normalizeFeegowAppointmentId(rawId);

        if (appointmentId == null || appointmentId.isBlank()) {
            log.warn("[WEBHOOK] Payload {}_  recebido sem ID válido. action={}", actionType, action);
            return null;
        }

        if (!blipIdempotencyService.tryAcquireLock(appointmentId)) {
            return blipIdempotencyService.getCachedResultOrSpinWait(appointmentId, actionType);
        }

        log.info("[WEBHOOK] Processando ação '{}' para agendamento ID={}", actionType, appointmentId);

        SessionDbData dbData = fetchSessionDbData(appointmentId);
        if (dbData == null) {
            return null;
        }

        String doctorName = resolveDoctorName(dbData, appointmentId);
        String queue = resolveQueue(dbData);
        String dispatchIdentity = blipPayloadParser.resolveDispatchIdentity(payload.from(), dbData.session());

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

    private WebhookResult handlePrepararOuExibir(BlipWebhookPayload payload, boolean isPrepararAtendimento) {
        String from = payload.from();
        if (from != null && !from.isBlank()) {
            String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(from, payload.bsuid());
            String normalizedPhone = from.trim();
            if (isPrepararAtendimento) {
                log.info("[WEBHOOK-BLOCK] Interceptando Preparar_Atendimento para {} (DB Phone: {})", normalizedPhone, dbPhone);
                boolean isGroup = false;
                UUID groupId = null;
                List<AppointmentSession> activeSessions = transactionTemplate.execute(status ->
                    appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
                );
                if (activeSessions != null) {
                    for (AppointmentSession activeSession : activeSessions) {
                        List<NotificationGroup> groups =
                            notificationGroupRepository.findBySessionId(activeSession.getId());
                        if (groups != null && !groups.isEmpty()) {
                            isGroup = true;
                            groupId = groups.get(0).getGroupId();
                            break;
                        }
                    }
                }
                blipContextService.setUserContextForUser(normalizedPhone, "isGroupFlow", String.valueOf(isGroup));
                blipContextService.setUserContextForUser(normalizedPhone, "groupId", isGroup && groupId != null ? groupId.toString() : "");
                return new WebhookResult("", "", "", "", "processed", "");
            } else {
                log.info("[WEBHOOK-BLOCK] Interceptando Exibir_Agenda para {} (DB Phone: {})", normalizedPhone, dbPhone);
                // Busca ativa de agendamentos e injeção formatada de consultas no contexto Blip
                List<AppointmentSession> activeSessions = transactionTemplate.execute(status ->
                    appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
                );
                if (activeSessions != null) {
                    activeSessions.sort((s1, s2) -> {
                        if (s1.getAppointmentAt() == null && s2.getAppointmentAt() == null) return 0;
                        if (s1.getAppointmentAt() == null) return 1;
                        if (s2.getAppointmentAt() == null) return -1;
                        return s1.getAppointmentAt().compareTo(s2.getAppointmentAt());
                    });
                }
                String listaDetalhada = blipAppointmentFormatter.buildListaDetalhada(activeSessions);
                blipContextService.setUserContextForUser(normalizedPhone, "lista_detalhada", listaDetalhada);
                log.info("[WEBHOOK-BLOCK] Injetada lista_detalhada para {}.", normalizedPhone);
                return new WebhookResult("", "", "", "", "processed", "");
            }
        }
        return new WebhookResult("", "", "", "", "processed", "");
    }

    private void validateWebhookToken(BlipWebhookPayload payload) {
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

    private WebhookResult handleUuidAction(String action) {
        log.info("[WEBHOOK] UUID puro detectado na ação. Carregando sessão de agendamento: {}", action);
        UUID sessionId = UUID.fromString(action);
        AppointmentSession session = transactionTemplate.execute(status ->
            appointmentSessionRepository.findById(sessionId).orElse(null)
        );

        if (session != null) {
            log.info("[WEBHOOK] Sessão encontrada para UUID puro. Enviando template individual para o agendamento Feegow={}", session.getFeegowAppointmentId());
            sendAppointmentTemplateUseCase.execute(session, br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory.CONFIRMATION);
            return new WebhookResult("", "", "", "", "individual_appointment_selected", "");
        } else {
            log.info("[WEBHOOK] Nenhuma sessão encontrada para a ação de UUID: {}", action);
            return null;
        }
    }

    private enum WebhookIntent {
        CONFIRM, CANCEL, ALTER, UNKNOWN
    }

    private WebhookIntent detectIntent(String text) {
        if (text == null) return WebhookIntent.UNKNOWN;
        String normalized = text.trim().toLowerCase();
        
        return switch (normalized) {
            case "sim", "confirmar", "confirm" -> WebhookIntent.CONFIRM;
            case "cancelar", "cancel" -> WebhookIntent.CANCEL;
            case "solicitar alteração", "solicitar alteracao", "alterar" -> WebhookIntent.ALTER;
            case String s when s.contains("confirmar presença") || s.contains("confirmar consulta") -> WebhookIntent.CONFIRM;
            case String s when s.contains("cancelar presença") || s.contains("cancelar consulta") -> WebhookIntent.CANCEL;
            case String s when s.contains("solicitar alter") -> WebhookIntent.ALTER;
            default -> WebhookIntent.UNKNOWN;
        };
    }

    private String resolveTextIntentions(String normalizedAction, String action, BlipWebhookPayload payload) {
        WebhookIntent intent = detectIntent(normalizedAction);
        
        return switch (intent) {
            case CONFIRM -> processIntent("confirm", action, payload, "confirmação");
            case CANCEL -> processIntent("cancel", action, payload, "cancelamento");
            case ALTER -> processIntent("alter", action, payload, "alteração");
            case UNKNOWN -> action;
        };
    }

    private String processIntent(String prefix, String action, BlipWebhookPayload payload, String label) {
        String resolvedId = resolveActiveAppointmentId(payload);
        if (resolvedId != null && !resolvedId.isBlank()) {
            log.info("[WEBHOOK] Texto livre de {} '{}' interceptado. Mapeando para {}_{}", label, action, prefix, resolvedId);
            return prefix + "_" + resolvedId;
        }
        return action;
    }

    private String resolveActiveAppointmentId(BlipWebhookPayload payload) {
        String resolvedId = payload.appointmentId();
        if (resolvedId == null || resolvedId.isBlank()) {
            String fromPhone = payload.from();
            if (fromPhone != null && !fromPhone.isBlank()) {
                String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, payload.bsuid());
                List<AppointmentSession> activeSessions = transactionTemplate.execute(status -> 
                    appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
                );
                if (activeSessions != null && !activeSessions.isEmpty()) {
                    resolvedId = activeSessions.get(0).getFeegowAppointmentId();
                }
            }
        }
        return resolvedId;
    }

    private SessionDbData fetchSessionDbData(String appointmentId) {
        try {
            return transactionTemplate.execute(status -> {
                AppointmentSession session = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId)
                        .orElseThrow(() -> new NotFoundException("Sessão não encontrada para appointmentId=" + appointmentId));
                AppointmentDoctorMapping doctorMapping = appointmentDoctorMappingRepository
                        .findByProfissionalId(session.getDoctorProfissionalId())
                        .orElse(null);
                return new SessionDbData(session, doctorMapping);
            });
        } catch (NotFoundException | IllegalStateException ex) {
            log.error("Erro na leitura transacional inicial do webhook para appointmentId={}. Detalhes: {}", appointmentId, ex.getMessage(), ex);
            return null;
        } catch (RuntimeException ex) {
            log.error("Erro de infraestrutura ao buscar sessão do webhook para appointmentId={}. Detalhes: {}", appointmentId, ex.getMessage(), ex);
            return null;
        }
    }

    private String resolveDoctorName(SessionDbData dbData, String appointmentId) {
        String doctorName = dbData.doctorMapping() != null ? dbData.doctorMapping().getProfissionalNome() : null;
        if (doctorName == null || doctorName.isBlank()) {
            try {
                doctorName = professionalExternalPort.getProfessionalName(dbData.session().getDoctorProfissionalId());
            } catch (IllegalStateException e) {
                log.warn("Não foi possível buscar o nome do médico na Feegow, usando fallback. erro={}", e.getMessage());
                auditPort.record(
                        "APPOINTMENT_MOTOR",
                        "CIRCUIT_BREAKER_FALLBACK",
                        "Fallback ao buscar nome do profissional na Feegow. appointmentId=" + appointmentId
                                + ", erro=" + safeMessage(e),
                        resolveTraceId());
            } catch (RuntimeException e) {
                log.warn("Erro de integração ao buscar nome do médico na Feegow, usando fallback. erro={}", e.getMessage());
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
        return blipTextSanitizer.cleanDoctorName(doctorName);
    }

    private String resolveQueue(SessionDbData dbData) {
        String queue = dbData.doctorMapping() != null ? dbData.doctorMapping().getBlipQueueId() : null;
        if (queue == null || queue.isBlank() || "null".equalsIgnoreCase(queue.trim()) || queue.contains("\u200E")) {
            queue = "Recepção Central / Suporte";
            log.warn("[QUEUE WARNING] Fila não encontrada no banco para o médico {}, usando fallback: {}", dbData.session().getDoctorProfissionalId(), queue);
        }
        queue = blipContextService.cleanQueueName(queue);
        return queue.isBlank() ? "Recepção Central / Suporte" : queue;
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

    public record BlipWebhookPayload(
        String messageId, 
        String appointmentId, 
        String action, 
        String from, 
        String token, 
        Object content, 
        Map<String, Object> metadata,
        String bsuid,
        String type
    ) {
        public BlipWebhookPayload(String messageId, String appointmentId, String action, String from, String token, Object content, Map<String, Object> metadata) {
            this(messageId, appointmentId, action, from, token, content, metadata, null, null);
        }
        public BlipWebhookPayload(String messageId, String appointmentId, String action, String from, String token, Object content, Map<String, Object> metadata, String bsuid) {
            this(messageId, appointmentId, action, from, token, content, metadata, bsuid, null);
        }
    }

    public record WebhookResult(String queue, String patientName, String patientCPF, String patientBirthdate, String action, String doctorName) {
    }
}
