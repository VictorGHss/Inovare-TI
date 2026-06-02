package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentTemplateData;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável por consultar os sistemas externos (Feegow) e montar
 * a DTO estruturada de parâmetros (AppointmentTemplateData) exigida pela API do Blip.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentTemplateDataBuilder {

    private static final DateTimeFormatter BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter BRAZILIAN_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_TEMPLATE_VALUE = "Recepção";
    private static final String DEFAULT_PROVIDER_VALUE = "Clínica Inovare";

    private final PatientExternalPort patientExternalPort;
    private final AppointmentExternalPort appointmentExternalPort;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Carrega as informações e constrói o objeto AppointmentTemplateData para envio.
     */
    public AppointmentTemplateData build(AppointmentSession session) {
        FeegowAppointment appointment = fetchAppointment(session);
        FeegowPatient patient = fetchPatient(session.getPatientId());
        String doctorName = resolveDoctorName(session.getDoctorProfissionalId(), appointment.doctorName());

        String appointmentDate = appointment.startAt() != null
            ? appointment.startAt().toLocalDate().format(BRAZILIAN_DATE) : DEFAULT_TEMPLATE_VALUE;
        String appointmentDateShort = appointment.startAt() != null
            ? appointment.startAt().toLocalDate().format(SHORT_BRAZILIAN_DATE) : DEFAULT_TEMPLATE_VALUE;
        String appointmentTime = appointment.startAt() != null
            ? appointment.startAt().toLocalTime().format(BRAZILIAN_TIME) : DEFAULT_TEMPLATE_VALUE;

        return new AppointmentTemplateData(
            fallbackValue(appointment.id()),
            fallbackValue(appointment.patientId()),
            fallbackValue(patient != null ? patient.name() : null),
            fallbackValue(patient != null ? patient.phone() : null),
            fallbackValue(appointment.doctorId()),
            fallbackProviderValue(doctorName),
            DEFAULT_PROVIDER_VALUE,
            fallbackProviderValue(appointment.unitName()),
            appointmentDate,
            appointmentDateShort,
            appointmentTime,
            appointmentDate
        );
    }

    private FeegowAppointment fetchAppointment(AppointmentSession session) {
        try {
            return appointmentExternalPort.searchAppointments(
                session.getAppointmentAt().toLocalDate(),
                1,
                session.getDoctorProfissionalId()
            ).stream()
             .filter(a -> a.id().equals(session.getFeegowAppointmentId()))
             .findFirst()
             .orElse(buildFallbackAppointment(session));
        } catch (RuntimeException ex) {
            log.warn("Erro ao buscar agendamento na Feegow: {}", ex.getMessage());
            return buildFallbackAppointment(session);
        }
    }

    private FeegowAppointment buildFallbackAppointment(AppointmentSession session) {
        return new FeegowAppointment(
            session.getFeegowAppointmentId(),
            session.getPatientId(),
            session.getDoctorProfissionalId(),
            null, null, session.getAppointmentAt(), null
        );
    }

    private FeegowPatient fetchPatient(String patientId) {
        try {
            return patientExternalPort.patientInfo(patientId);
        } catch (RuntimeException ex) {
            log.warn("Erro ao buscar dados do paciente: {}", ex.getMessage());
            return null;
        }
    }

    private String resolveDoctorName(String doctorId, String fallbackName) {
        String doctorName = null;
        String normalizedProfissionalId = doctorId != null ? doctorId.trim() : "";
        if (!normalizedProfissionalId.isEmpty()) {
            try {
                doctorName = transactionTemplate.execute(status ->
                    appointmentDoctorMappingRepository.findByProfissionalIdLocked(normalizedProfissionalId)
                        .map(AppointmentDoctorMapping::getProfissionalNome)
                        .filter(nome -> !nome.isBlank() && !"null".equalsIgnoreCase(nome.trim()))
                        .orElse(null)
                );
            } catch (RuntimeException ex) {
                log.warn("Erro ao buscar mapeamento no banco para doctorId={}", doctorId, ex);
            }
        }
        if (doctorName == null || doctorName.isBlank() || "null".equalsIgnoreCase(doctorName.trim())) {
            doctorName = fallbackName;
        }
        return (doctorName == null || doctorName.isBlank() || "null".equalsIgnoreCase(doctorName.trim()))
            ? DEFAULT_PROVIDER_VALUE : doctorName.trim();
    }

    private String fallbackValue(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim()) || "Informação não disponível".equalsIgnoreCase(value.trim())) {
            return "Recepção";
        }
        return value.trim();
    }

    private String fallbackProviderValue(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim()) || "Informação não disponível".equalsIgnoreCase(value.trim())) {
            return "Clínica Inovare";
        }
        return value.trim();
    }
}
