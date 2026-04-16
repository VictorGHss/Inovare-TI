package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentCategory;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfig;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfigRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.BlipClient;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.dto.AppointmentTemplateData;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SendAppointmentTemplateUseCase {

        private static final DateTimeFormatter BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        private static final DateTimeFormatter BRAZILIAN_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentConfigRepository appointmentConfigRepository;
    private final FeegowClient feegowClient;
    private final BlipClient blipClient;

    @Transactional
    public void execute(AppointmentSession session, AppointmentCategory category) {
        AppointmentConfig config = appointmentConfigRepository.findByCategory(category)
                .orElseThrow(() -> new NotFoundException("Configuração não encontrada para categoria " + category));

        FeegowClient.FeegowAppointment appointment = new FeegowClient.FeegowAppointment(
                session.getFeegowAppointmentId(),
                session.getPatientId(),
                session.getDoctorProfissionalId(),
                null,
                null,
                session.getAppointmentAt());
        FeegowClient.FeegowPatient patient = feegowClient.patientInfo(session.getPatientId());

        AppointmentTemplateData templateData = new AppointmentTemplateData(
                appointment.id(),
                appointment.patientId(),
                patient.name(),
                patient.phone(),
                appointment.doctorId(),
                appointment.doctorName(),
                "",
                appointment.unitName(),
                appointment.startAt() != null ? appointment.startAt().toLocalDate().format(BRAZILIAN_DATE) : "",
                appointment.startAt() != null ? appointment.startAt().toLocalTime().format(BRAZILIAN_TIME) : "",
                appointment.startAt() != null ? appointment.startAt().toLocalDate().format(BRAZILIAN_DATE) : "");

        blipClient.sendTemplateMessage(session.getPatientPhone(), config.getTemplateId(), templateData);
    }
}
