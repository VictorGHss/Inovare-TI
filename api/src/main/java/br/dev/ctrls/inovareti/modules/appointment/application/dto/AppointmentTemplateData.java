package br.dev.ctrls.inovareti.modules.appointment.application.dto;

public record AppointmentTemplateData(
        String appointmentId,
        String patientId,
        String patientName,
        String patientPhone,
        String doctorId,
        String doctorName,
        String specialty,
        String unitName,
        String appointmentDate,
        String appointmentDateShort,
        String appointmentTime,
        String appointmentDateTime) {
}