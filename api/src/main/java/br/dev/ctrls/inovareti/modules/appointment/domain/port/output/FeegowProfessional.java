package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

/**
 * Record de domínio FeegowProfessional.
 * Representa os dados essenciais de um profissional médico obtido da Feegow de forma imutável e desacoplada.
 */
public record FeegowProfessional(
        String id,
        String name) {
}
