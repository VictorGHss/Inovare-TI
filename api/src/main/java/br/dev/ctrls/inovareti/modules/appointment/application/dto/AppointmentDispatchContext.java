package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import java.util.UUID;

/**
 * Record imutável que encapsula todos os dados já resolvidos para o envio de um template.
 * Usado para passar contexto entre threads sem risco de vazamento de estado Hibernate.
 */
public record AppointmentDispatchContext(
        UUID sessionId,
        String feegowAppointmentId,
        String patientName,
        String patientPhone,
        String patientId,
        String doctorProfissionalId,
        String doctorName,       // já resolvido: banco → Feegow → fallback
        String queueName,        // já resolvido: banco → fallback
        String appointmentDate,  // formatado dd/MM/yyyy
        String appointmentDateShort, // formatado dd/MM
        String appointmentTime,  // formatado HH:mm
        String phoneNumber       // telefone normalizado para o Blip
) {
}
