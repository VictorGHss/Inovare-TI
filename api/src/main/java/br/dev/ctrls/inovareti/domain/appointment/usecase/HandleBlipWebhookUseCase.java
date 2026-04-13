package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionRepository;
import br.dev.ctrls.inovareti.domain.appointment.BlipClient;
import br.dev.ctrls.inovareti.domain.appointment.ConfirmationStateMachineService;
import br.dev.ctrls.inovareti.domain.appointment.DoctorMapping;
import br.dev.ctrls.inovareti.domain.appointment.DoctorMappingRepository;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.NoopWebhookIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.WebhookIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HandleBlipWebhookUseCase {

    private static final int FEEGOW_STATUS_CONFIRMADO = 7;

    private final AppointmentSessionRepository appointmentSessionRepository;
    private final FeegowClient feegowClient;
    private final BlipClient blipClient;
    private final DoctorMappingRepository doctorMappingRepository;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final Optional<WebhookIdempotencyService> webhookIdempotencyService;
    private final Optional<NoopWebhookIdempotencyService> noopWebhookIdempotencyService;

    @Transactional
    public void execute(BlipWebhookPayload payload) {
        boolean fresh = webhookIdempotencyService
                .map(service -> service.registerIfFirstTime(payload.messageId()))
                .orElseGet(() -> noopWebhookIdempotencyService.map(service -> service.registerIfFirstTime(payload.messageId())).orElse(true));

        if (!fresh) {
            log.info("Webhook ignorado por idempotencia. messageId={}", payload.messageId());
            return;
        }

        AppointmentSession session = appointmentSessionRepository.findByFeegowAppointmentId(payload.appointmentId())
                .orElseThrow(() -> new NotFoundException("Sessão não encontrada para appointmentId=" + payload.appointmentId()));

        String action = payload.action().trim().toUpperCase(Locale.ROOT);

        switch (action) {
            case "CONFIRMAR" -> {
                feegowClient.updateStatus(session.getFeegowAppointmentId(), FEEGOW_STATUS_CONFIRMADO);
                confirmationStateMachineService.markConfirmed(session);
                appointmentSessionRepository.save(session);
            }
            case "ALTERAR" -> {
                DoctorMapping mapping = doctorMappingRepository.findByProfissionalId(session.getDoctorProfissionalId())
                        .orElseGet(() -> doctorMappingRepository.findByProfissionalId("EXTERNAL")
                        .orElseThrow(() -> new NotFoundException("Fila externa não encontrada no appointment_doctor_mapping")));
                blipClient.setHandoffContext(payload.from(), mapping.getBlipQueueId());
            }
            default -> log.info("Webhook recebido com ação sem transição configurada. action={}", action);
        }
    }

    public record BlipWebhookPayload(String messageId, String appointmentId, String action, String from) {
    }
}
