package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.appointment.AppointmentCategory;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestAppointmentsUseCase {

    private static final int FEEGOW_STATUS_AGENDADO = 1;

    private final FeegowClient feegowClient;
    private final AppointmentSessionRepository appointmentSessionRepository;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;

    @Transactional
    public void execute() {
        LocalDate targetDate = LocalDate.now().plusDays(1);
        List<FeegowClient.FeegowAppointment> appointments = feegowClient.searchAppointments(targetDate, FEEGOW_STATUS_AGENDADO);

        int created = 0;
        for (FeegowClient.FeegowAppointment appointment : appointments) {
            if (appointmentSessionRepository.findByFeegowAppointmentId(appointment.id()).isPresent()) {
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
        }

        log.info("Ingestão de consultas executada. totalRecebido={}, sessoesCriadas={}", appointments.size(), created);
    }
}
