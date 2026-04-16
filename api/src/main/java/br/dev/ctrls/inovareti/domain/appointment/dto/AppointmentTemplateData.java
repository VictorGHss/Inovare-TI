package br.dev.ctrls.inovareti.domain.appointment.dto;

public record AppointmentTemplateData(
        String appointmentId,
        String patientId,
        String patientName,
        String patientPhone,
        String doctorId,
        String doctorName,
        String unitName,
        String appointmentDateTime) {
}