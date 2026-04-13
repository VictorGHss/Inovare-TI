package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.appointment.AppointmentCategory;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSendIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.NoopAppointmentSendIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestAppointmentsUseCase {

    private static final int FEEGOW_STATUS_AGENDADO = 1;

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final FeegowClient feegowClient;
    private final AppointmentSessionRepository appointmentSessionRepository;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final Optional<AppointmentSendIdempotencyService> appointmentSendIdempotencyService;
    private final Optional<NoopAppointmentSendIdempotencyService> noopAppointmentSendIdempotencyService;

    @Transactional
    public IngestionSummary execute() {
        LocalDate targetDate = LocalDate.now().plusDays(1);
        List<FeegowClient.FeegowAppointment> appointments = feegowClient.searchAppointments(targetDate, FEEGOW_STATUS_AGENDADO);
        int totalReceived = appointments.size();

        if (appointmentMotorProperties.isTestMode()) {
            log.warn("[TEST MODE ACTIVE] Filtering only for Doctor ID: {}.", appointmentMotorProperties.getTestDoctorId());
            appointments = appointments.stream()
                    .filter(appointment -> appointmentMotorProperties.getTestDoctorId().equals(appointment.doctorId()))
                    .toList();
        }

        int filteredReceived = appointments.size();

        int created = 0;
        int messagesSent = 0;
        for (FeegowClient.FeegowAppointment appointment : appointments) {
            if (appointmentSessionRepository.findByFeegowAppointmentId(appointment.id()).isPresent()) {
                continue;
            }

            boolean canSend = appointmentSendIdempotencyService
                    .map(service -> service.registerIfFirstSend(appointment.id()))
                    .orElseGet(() -> noopAppointmentSendIdempotencyService
                            .map(service -> service.registerIfFirstSend(appointment.id()))
                            .orElse(true));

            if (!canSend) {
                log.info("Envio ignorado por idempotência Redis. appointmentId={}", appointment.id());
                continue;
            }

            FeegowClient.FeegowPatient patient = feegowClient.patientInfo(appointment.patientId());
            AppointmentSession session = AppointmentSession.builder()
                    .feegowAppointmentId(appointment.id())
                    .patientId(appointment.patientId())
                    .patientPhone(patient.phone())
                    .doctorProfissionalId(appointment.doctorId())
                    .appointmentAt(appointment.startAt())
                    .status(AppointmentSessionStatus.PENDING)
                    .lastInteractionAt(LocalDateTime.now())
                    .build();

            AppointmentSession saved = appointmentSessionRepository.save(session);
            sendAppointmentTemplateUseCase.execute(saved, AppointmentCategory.CONFIRMATION);
            created++;
            messagesSent++;
        }

        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        log.info("Ingestão de consultas executada. totalRecebido={}, totalAposFiltro={}, sessoesCriadas={}, mensagensEnviadas={}, modo={}",
                totalReceived,
                filteredReceived,
                created,
                messagesSent,
                mode);

        return new IngestionSummary(totalReceived, filteredReceived, created, messagesSent, mode);
    }

    public record IngestionSummary(int totalReceived, int filteredReceived, int sessionsCreated, int messagesSent, String mode) {
    }
}
