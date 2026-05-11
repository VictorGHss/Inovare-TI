package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMappingRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentRealtimeNotificationService;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentVariableLogRepository;
import br.dev.ctrls.inovareti.domain.appointment.ConfirmationStateMachineService;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.NoopWebhookIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.NotificationService;
import br.dev.ctrls.inovareti.domain.appointment.WebhookIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.service.BlipContextService;
import br.dev.ctrls.inovareti.domain.appointment.service.BlipTicketService;
import br.dev.ctrls.inovareti.domain.appointment.service.BlipLIMEClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HandleBlipWebhookUseCase {

    private final AppointmentSessionRepository appointmentSessionRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final FeegowClient feegowClient;
    private final BlipContextService blipContextService;
    private final BlipTicketService blipTicketService;
    private final BlipLIMEClient blipLIMEClient;
    private final AppointmentDoctorMappingRepository appointmentDoctorMappingRepository;
    private final AppointmentVariableLogRepository appointmentVariableLogRepository;
    private final AppointmentRealtimeNotificationService appointmentRealtimeNotificationService;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final NotificationService notificationService;
    private final Optional<WebhookIdempotencyService> webhookIdempotencyService;
    private final Optional<NoopWebhookIdempotencyService> noopWebhookIdempotencyService;

    @Transactional
    public void execute(BlipWebhookPayload payload) {
        String expectedToken = appointmentMotorProperties.getSecurity().getWebhookToken();
        if (expectedToken != null && !expectedToken.isBlank() && !expectedToken.equals(payload.token())) {
            log.warn("Token de webhook inválido.");
            throw new SecurityException("Invalid token");
        }

        boolean fresh = webhookIdempotencyService
                .map(service -> service.registerIfFirstTime(payload.messageId()))
                .orElseGet(() -> noopWebhookIdempotencyService.map(service -> service.registerIfFirstTime(payload.messageId())).orElse(true));

        if (!fresh) {
            log.debug("Ação ignorada no webhook (idempotência). messageId={}", payload.messageId());
            return;
        }

        String action = payload.action() != null ? payload.action().trim() : "";

        // Intercepção de texto livre: "Solicitar Alteração" sem payload de botão
        // Resolve o ID da sessão ativa mais recente para o telefone do usuário
        if (action.equalsIgnoreCase("Solicitar Alteração")
                || action.equalsIgnoreCase("Solicitar Alteracao")
                || action.equalsIgnoreCase("alterar")
                || action.toLowerCase().contains("solicitar alter")) {
            String fromPhone = payload.from();
            if (fromPhone != null && !fromPhone.isBlank()) {
                String normalizedPhone = fromPhone.trim();
                java.util.List<AppointmentSession> activeSessions =
                    appointmentSessionRepository.findActiveByPhoneNumber(normalizedPhone);
                if (!activeSessions.isEmpty()) {
                    String resolvedId = activeSessions.get(0).getFeegowAppointmentId();
                    log.info("[WEBHOOK] Texto livre '{}' interceptado. Mapeando para alter_{} (sessão mais recente de {})",
                        action, resolvedId, normalizedPhone);
                    action = "alter_" + resolvedId;
                } else {
                    log.warn("[WEBHOOK] Texto livre '{}' recebido mas nenhuma sessão ativa encontrada para {}", action, normalizedPhone);
                    return;
                }
            } else {
                log.warn("[WEBHOOK] Texto livre '{}' recebido sem 'from' identificável.", action);
                return;
            }
        }

        // Captura confirm_{ID} ou alter_{ID}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)(confirm|alter)_(\\d+(?:\\.\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(action);

        if (!matcher.find()) {
            log.debug("[WEBHOOK] Ação ignorada (não é confirm_ nem alter_). action='{}'", action);
            return;
        }

        String actionType   = matcher.group(1).toLowerCase(); // "confirm" ou "alter"
        String rawId        = matcher.group(2);
        String appointmentId = normalizeFeegowAppointmentId(rawId);

        if (appointmentId == null || appointmentId.isBlank()) {
            log.warn("[WEBHOOK] Payload {}_  recebido sem ID válido. action={}", actionType, action);
            return;
        }

        log.info("[WEBHOOK] Processando ação '{}' para agendamento ID={}", actionType, appointmentId);

        AppointmentSession session = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId)
                .orElseThrow(() -> new NotFoundException("Sessão não encontrada para appointmentId=" + appointmentId));

        String dispatchIdentity = resolveDispatchIdentity(payload.from(), session);
        if (dispatchIdentity != null) {
            session.setPhoneNumber(dispatchIdentity);
        }

        if ("confirm".equals(actionType)) {
            String confirmedStatusId = resolveConfirmedStatusId();
            log.info("[CONFIRM] Atualizando status na Feegow com código {}.", confirmedStatusId);
            feegowClient.updateAppointmentStatus(session.getFeegowAppointmentId(), confirmedStatusId);
            notificationService.notifySecretary(session, null);
            confirmationStateMachineService.markConfirmed(session);
            appointmentSessionRepository.save(session);
        } else {
            // alter_ — apenas notifica e salva sem mudar status na Feegow
            log.info("[ALTER] Solicitação de alteração para agendamento {}. Encaminhando para atendimento humano.", appointmentId);
            notificationService.notifySecretary(session, "SOLICITAÇÃO DE ALTERAÇÃO");
        }

        try {
            String profissionalId = session.getDoctorProfissionalId();
            String resolvedProfissionalId = profissionalId == null ? null : profissionalId.trim();

            // Busca fila do médico no banco — NUNCA usa caractere invisível aqui
            String queueId = null;
            if (resolvedProfissionalId != null && !resolvedProfissionalId.isBlank()) {
                queueId = appointmentDoctorMappingRepository.findByProfissionalId(resolvedProfissionalId)
                    .map(AppointmentDoctorMapping::getBlipQueueId)
                    .filter(id -> id != null && !id.isBlank()
                               && !"null".equalsIgnoreCase(id.trim())
                               && !id.contains("\u200E")) // nunca aceitar o char invisível
                    .orElse(null);
            }

            // Para alter_: força fila de Recepção/Agendamento
            if ("alter".equals(actionType)) {
                queueId = "Recepção";
                log.info("[ALTER] Fila forcçada para Recepção por solicitação de alteração.");
            } else if (queueId == null || queueId.isBlank()) {
                queueId = "Recepção Central";
                log.info("[CONFIRM] Fila não encontrada para profissional {}. Usando fallback: '{}'",
                    resolvedProfissionalId, queueId);
            }

            log.info("[WEBHOOK] Fila resolvida: '{}' para profissional={}", queueId, resolvedProfissionalId);

            if (dispatchIdentity == null) {
                log.warn("[CONFIRMATION] Identidade de disparo ausente. Teletransporte ignorado.");
            } else {
                blipTicketService.closeActiveTickets(dispatchIdentity);
                boolean queueSet = blipContextService.setQueueRedirect(dispatchIdentity, queueId);

                String flowId     = appointmentMotorProperties.getState().getBlipFluxov1FlowId();
                String stateId    = appointmentMotorProperties.getState().getBlipLandingConfirmacaoItsmStateId();
                String blockId    = appointmentMotorProperties.getState().getBlipLandingBlockId();

                // Prioridade: usar o estado específico de confirmação se configurado,
                // caso contrário usa o bloco de aterrissagem genérico
                String targetState = (stateId != null && !stateId.isBlank()) ? stateId : blockId;

                log.info("[CONFIRMATION] Agendamento {} confirmado. Teletransportando usuário {} → flowId={}, state={}",
                    appointmentId, dispatchIdentity, flowId, targetState);

                if (queueSet && flowId != null && !flowId.isBlank() && targetState != null && !targetState.isBlank()) {
                    blipContextService.setUserState(dispatchIdentity, flowId, targetState);
                } else {
                    log.warn("[CONFIRMATION] Teletransporte parcial: queueSet={}, flowId={}, targetState={}",
                        queueSet, flowId, targetState);
                }

                // Envia feedback visual ao paciente
                sendConfirmationFeedback(dispatchIdentity);
            }
        } catch (Exception ex) {
            log.warn("[CONFIRMATION] Falha ao preparar redirecionamento: {}", ex.getMessage());
        }

        String patientName = resolvePatientName(session);
        String doctorName = resolveDoctorName(session);
        appointmentRealtimeNotificationService.sendNotification(patientName, doctorName, "CONFIRMADO");
    }

    private String resolveConfirmedStatusId() {
        String configuredStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
        if (configuredStatusId == null || configuredStatusId.isBlank()) {
            return "2";
        }
        return configuredStatusId.trim();
    }

    private String resolvePatientName(AppointmentSession session) {
        return Optional.ofNullable(feegowClient.patientInfo(session.getPatientId()).name())
                .filter(name -> !name.isBlank())
                .orElse("Paciente " + session.getPatientId());
    }

    private String resolveDoctorName(AppointmentSession session) {
        return appointmentVariableLogRepository
                .findFirstBySessionIdAndDictionaryKeyOrderBySentAtDesc(session.getId(), "MEDICO_NOME")
                .map(logEntry -> logEntry.getResolvedValue())
                .filter(name -> !name.isBlank())
                .orElse("Profissional " + session.getDoctorProfissionalId());
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
        String trimmed = identity.trim();
        if (trimmed.endsWith("@tunnel.msging.net")) {
            return null;
        }
        return trimmed;
    }

    /**
     * Envia mensagem de texto simples ao paciente confirmando a presença.
     * Não bloqueia nem lança exceção — falhas são apenas logadas.
     */
    private void sendConfirmationFeedback(String dispatchIdentity) {
        try {
            String normalizedIdentity = blipLIMEClient.normalizeUserIdentity(dispatchIdentity);
            java.util.Map<String, Object> message = new java.util.HashMap<>();
            message.put("id", java.util.UUID.randomUUID().toString());
            message.put("to", normalizedIdentity);
            message.put("type", "text/plain");
            message.put("content", "Obrigado! Sua presença foi confirmada. ✅ Aguardamos você!");
            blipLIMEClient.executeMessage(message, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("[CONFIRMATION] Mensagem de feedback enviada para {}", normalizedIdentity);
        } catch (Exception ex) {
            log.warn("[CONFIRMATION] Falha ao enviar feedback de confirmação para {}. Teletransporte já foi realizado.",
                dispatchIdentity, ex);
        }
    }

    public record BlipWebhookPayload(String messageId, String appointmentId, String action, String from, String token) {
    }
}
