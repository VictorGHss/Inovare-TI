package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

/**
 * Record de domínio FeegowPatient.
 * Representa os dados cadastrais básicos de um paciente obtido da Feegow de forma imutável e desacoplada.
 */
public record FeegowPatient(
        String id,
        String name,
        String phone,
        String cpf,
        String birthdate) {
}
