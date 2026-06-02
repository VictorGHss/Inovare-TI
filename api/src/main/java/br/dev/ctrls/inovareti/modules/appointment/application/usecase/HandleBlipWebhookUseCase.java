package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.dao.DataAccessException;

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
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookActionExecutor;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipTextSanitizer;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipAppointmentFormatter;
import br.dev.ctrls.inovareti.modules.appointment.application.service.FeegowBulkIntegrationHandler;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.application.service.ConfirmationStateMachineService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipIdentityReconciler;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipPayloadParser;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNudgeResponseHandler;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipGroupActionHandler;

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
    private final AppointmentExternalPort appointmentExternalPort;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final BlipProperties blipProperties;
    private final BlipTextSanitizer blipTextSanitizer;
    private final BlipAppointmentFormatter blipAppointmentFormatter;
    private final FeegowBulkIntegrationHandler feegowBulkIntegrationHandler;
    
    private final BlipIdentityReconciler blipIdentityReconciler;
    private final BlipPayloadParser blipPayloadParser;
    private final BlipNudgeResponseHandler blipNudgeResponseHandler;
    private final BlipGroupActionHandler blipGroupActionHandler;

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
            return handleUuidAction(action);
        }

        String normalizedAction = action.trim().toLowerCase();

        if (blipNudgeResponseHandler.handleNudgeResponse(normalizedAction, action, fromPhone, payload.bsuid())) {
            return new WebhookResult("", "", "", "", "nudge_response_processed", "");
        }

        action = resolveTextIntentions(normalizedAction, action, payload);

        if (blipGroupActionHandler.handleGroupAction(action, fromPhone, payload.bsuid(), payload.metadata())) {
            return new WebhookResult("", "", "", "", "group_action_processed", "");
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
                log.info("[WEBHOOK-BLOCK] Interceptando Exibir_Agenda de forma limpa para {} - Respondendo com sucesso sem efeitos colaterais.", dbPhone);
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
        } else {
            log.warn("[WEBHOOK] Nenhuma sessão encontrada para a ação de UUID: {}", action);
        }
        return new WebhookResult("", "", "", "", "individual_appointment_selected", "");
    }

    private String resolveTextIntentions(String normalizedAction, String action, BlipWebhookPayload payload) {
        if (normalizedAction.equals("sim")
                || normalizedAction.equals("confirmar")
                || normalizedAction.equals("confirm")
                || normalizedAction.contains("confirmar presença")
                || normalizedAction.contains("confirmar consulta")) {
            String resolvedId = resolveActiveAppointmentId(payload);
            if (resolvedId != null && !resolvedId.isBlank()) {
                log.info("[WEBHOOK] Texto livre de confirmação '{}' interceptado. Mapeando para confirm_{}", action, resolvedId);
                return "confirm_" + resolvedId;
            }
        }
        if (normalizedAction.equals("cancelar")
                || normalizedAction.equals("cancel")
                || normalizedAction.contains("cancelar presença")
                || normalizedAction.contains("cancelar consulta")) {
            String resolvedId = resolveActiveAppointmentId(payload);
            if (resolvedId != null && !resolvedId.isBlank()) {
                log.info("[WEBHOOK] Texto livre de cancelamento '{}' interceptado. Mapeando para cancel_{}", action, resolvedId);
                return "cancel_" + resolvedId;
            }
        }
        if (action.equalsIgnoreCase("Solicitar Alteração")
                || action.equalsIgnoreCase("Solicitar Alteracao")
                || action.equalsIgnoreCase("alterar")
                || action.toLowerCase().contains("solicitar alter")) {
            String resolvedId = resolveActiveAppointmentId(payload);
            if (resolvedId != null && !resolvedId.isBlank()) {
                log.info("[WEBHOOK] Texto livre '{}' interceptado. Mapeando para alter_{}", action, resolvedId);
                return "alter_" + resolvedId;
            }
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
        } catch (NotFoundException | DataAccessException | IllegalStateException ex) {
            log.error("Erro na leitura transacional inicial do webhook para appointmentId={}. Detalhes: {}", appointmentId, ex.getMessage(), ex);
            return null;
        }
    }

    private String resolveDoctorName(SessionDbData dbData, String appointmentId) {
        String doctorName = dbData.doctorMapping() != null ? dbData.doctorMapping().getProfissionalNome() : null;
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
        String bsuid
    ) {
        public BlipWebhookPayload(String messageId, String appointmentId, String action, String from, String token, Object content, Map<String, Object> metadata) {
            this(messageId, appointmentId, action, from, token, content, metadata, null);
        }
    }

    public record WebhookResult(String queue, String patientName, String patientCPF, String patientBirthdate, String action, String doctorName) {
    }
}
