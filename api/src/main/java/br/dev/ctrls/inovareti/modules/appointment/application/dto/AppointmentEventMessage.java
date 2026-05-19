package br.dev.ctrls.inovareti.modules.appointment.application.dto;

/**
 * Payload enviado para o frontend no canal de eventos de agendamentos.
 */
public record AppointmentEventMessage(
        String patientName,
        String doctorName,
        String status) {
}
