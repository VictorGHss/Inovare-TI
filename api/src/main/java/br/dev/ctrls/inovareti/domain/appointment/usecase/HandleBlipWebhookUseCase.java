package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.util.Locale;
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
import br.dev.ctrls.inovareti.domain.appointment.BlipClient;
import br.dev.ctrls.inovareti.domain.appointment.ConfirmationStateMachineService;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import java.util.Map;
import br.dev.ctrls.inovareti.domain.appointment.NoopWebhookIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.NotificationService;
import br.dev.ctrls.inovareti.domain.appointment.WebhookIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HandleBlipWebhookUseCase {

    private final AppointmentSessionRepository appointmentSessionRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final FeegowClient feegowClient;
    private final BlipClient blipClient;
    private final AppointmentDoctorMappingRepository appointmentDoctorMappingRepository;
    private final AppointmentVariableLogRepository appointmentVariableLogRepository;
    private final AppointmentRealtimeNotificationService appointmentRealtimeNotificationService;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final NotificationService notificationService;
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

        // Suporte ao botão com payload confirm_*
        String action = payload.action() != null ? payload.action().trim().toUpperCase(Locale.ROOT) : "";

        // Ignora qualquer outra tentativa ou mensagem do bot (ex: perguntando nome) que não seja o botão confirm_
        if (!action.startsWith("CONFIRM_")) {
            log.info("Ação ignorada no webhook (focando apenas em botões confirm_). action={}", action);
            return;
        }

        // Se o action for um payload do botão
        if (action.startsWith("CONFIRM_")) {
            String confirmIdRaw = action.substring("CONFIRM_".length());
            String confirmId = normalizeFeegowAppointmentId(confirmIdRaw);
            if (confirmId == null || confirmId.isBlank()) {
                log.warn("Payload CONFIRM_ recebido sem id válido. action={}", action);
                return;
            }

            log.info("Processando clique do agendamento ID: " + confirmId);

            AppointmentSession session = appointmentSessionRepository.findByFeegowAppointmentId(confirmId)
                    .orElseThrow(() -> new NotFoundException("Sessão não encontrada para appointmentId=" + confirmId));
            String confirmedStatusId = resolveConfirmedStatusId();
            log.info("Payload de botão CONFIRM_ recebido. Atualizando status na Feegow com código {}.", confirmedStatusId);
            feegowClient.updateAppointmentStatus(session.getFeegowAppointmentId(), confirmedStatusId);
            notificationService.notifySecretary(session, null);
            confirmationStateMachineService.markConfirmed(session);
            appointmentSessionRepository.save(session);
            // Tenta recuperar a fila de atendimento salva nos extras do contato (mergeContactExtras)
            try {
                log.info("Buscando extras do contato para handoff. payload.from={}", payload.from());
                Map<String, String> extras = blipClient.getContactExtras(payload.from());
                String queueId = null;
                if (extras != null && !extras.isEmpty()) {
                    String filaDestino = extras.get("fila_destino");
                    String filaNome = extras.get("fila_nome");
                    if (filaDestino != null && !filaDestino.isBlank()) {
                        queueId = filaDestino.trim();
                    } else if (filaNome != null && !filaNome.isBlank()) {
                        queueId = filaNome.trim();
                    }
                }

                if (queueId != null && !queueId.isBlank()) {
                    log.info("Executando handoff para fila salva nos extras do contato: {}", queueId);
                    blipClient.setHandoffContext(payload.from(), queueId);
                } else {
                    // fallback: tenta basear-se no mapeamento do médico
                    AppointmentDoctorMapping mapping = appointmentDoctorMappingRepository.findByProfissionalId(session.getDoctorProfissionalId())
                            .orElseGet(() -> appointmentDoctorMappingRepository.findByProfissionalId("EXTERNAL")
                                    .orElse(null));

                    if (mapping != null) {
                        if (mapping.isExternal()) {
                            blipClient.sendPlainText(payload.from(), mapping.getExternalWaLink());
                        } else {
                            blipClient.setHandoffContext(payload.from(), mapping.getBlipQueueId());
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Falha ao recuperar extras do contato ou executar handoff: {}", ex.getMessage());
                blipClient.pushUserBackToBuilder(payload.from());
            }

            String patientName = resolvePatientName(session);
            String doctorName = resolveDoctorName(session);
            appointmentRealtimeNotificationService.sendNotification(patientName, doctorName, "CONFIRMADO");
        }
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

    public record BlipWebhookPayload(String messageId, String appointmentId, String action, String from) {
    }
}
