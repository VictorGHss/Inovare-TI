package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.time.LocalDateTime;

/**
 * Record de domínio FeegowAppointment.
 * Representa os dados essenciais de um agendamento obtido da Feegow de forma imutável e desacoplada.
 */
public record FeegowAppointment(
        String id,
        String patientId,
        String doctorId,
        String doctorName,
        String unitName,
        LocalDateTime startAt,
        String statusId) {
}
