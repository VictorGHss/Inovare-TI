package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface FeegowClientPort {

    List<FeegowAppointment> searchAppointments(LocalDate date, int statusId);

    List<FeegowAppointment> searchAppointments(LocalDate date, int statusId, String profissionalId);

    FeegowPatient patientInfo(String patientId);

    String getProfessionalName(String professionalId);

    List<FeegowProfessional> listProfessionals();

    void updateAppointmentStatus(String appointmentId, String statusId);

    void updateStatus(String appointmentId, int statusId);

    record FeegowAppointment(
            String id,
            String patientId,
            String doctorId,
            String doctorName,
            String unitName,
            LocalDateTime startAt,
            String statusId) {
    }

    record FeegowPatient(
            String id,
            String name,
            String phone,
            String cpf,
            String birthdate) {
    }

    record FeegowProfessional(
            String id,
            String name) {
    }
}
