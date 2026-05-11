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

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)confirm_(\\d+(?:\\.\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(action);

        if (!matcher.find()) {
            log.debug("Ação ignorada no webhook (focando apenas em botões confirm_). action={}", action);
            return;
        }

        String confirmIdRaw = matcher.group(1);
        String confirmId = normalizeFeegowAppointmentId(confirmIdRaw);
        if (confirmId == null || confirmId.isBlank()) {
            log.warn("Payload CONFIRM_ recebido sem id válido. action={}", action);
            return;
        }

        log.info("Processando clique do agendamento ID: " + confirmId);

        AppointmentSession session = appointmentSessionRepository.findByFeegowAppointmentId(confirmId)
                .orElseThrow(() -> new NotFoundException("Sessão não encontrada para appointmentId=" + confirmId));
        
        String dispatchIdentity = resolveDispatchIdentity(payload.from(), session);
        if (dispatchIdentity != null) {
            session.setPhoneNumber(dispatchIdentity);
        }

        String confirmedStatusId = resolveConfirmedStatusId();
        log.info("Payload de botão CONFIRM_ recebido. Atualizando status na Feegow com código {}.", confirmedStatusId);
        feegowClient.updateAppointmentStatus(session.getFeegowAppointmentId(), confirmedStatusId);
        notificationService.notifySecretary(session, null);
        confirmationStateMachineService.markConfirmed(session);
        appointmentSessionRepository.save(session);

        try {
            String profissionalId = session.getDoctorProfissionalId();
            String resolvedProfissionalId = profissionalId == null ? null : profissionalId.trim();
            String queueId = null;
            if (resolvedProfissionalId != null && !resolvedProfissionalId.isBlank()) {
                queueId = appointmentDoctorMappingRepository.findByProfissionalId(resolvedProfissionalId)
                    .map(AppointmentDoctorMapping::getBlipQueueId)
                    .filter(id -> !id.isBlank())
                    .orElse(null);
            }

            if (queueId == null || queueId.isBlank()) {
                queueId = "Recepção Central";
                log.info("Fila não encontrada para profissional {}, usando fallback: {}", resolvedProfissionalId, queueId);
            }

            if (dispatchIdentity == null) {
                log.warn("Identidade de disparo ausente no webhook. Redirecionamento ignorado.");
            } else {
                blipTicketService.closeActiveTickets(dispatchIdentity);
                boolean queueSet = blipContextService.setQueueRedirect(dispatchIdentity, queueId);
                
                if (queueSet) {
                    String flowId = appointmentMotorProperties.getState().getBlipFluxov1FlowId();
                    String blockId = appointmentMotorProperties.getState().getBlipLandingBlockId();
                    
                    if (flowId != null && !flowId.isBlank() && blockId != null && !blockId.isBlank()) {
                        blipContextService.setUserState(dispatchIdentity, flowId, blockId);
                    } else {
                        log.warn("Teletransporte interno cancelado. flowId ou blockId não configurados.");
                    }
                } else {
                    log.warn("Teletransporte interno cancelado. Falha ao definir a fila no contexto.");
                }
            }
        } catch (Exception ex) {
            log.warn("Falha ao preparar redirecionamento de fila: {}", ex.getMessage());
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

    public record BlipWebhookPayload(String messageId, String appointmentId, String action, String from, String token) {
    }
}
