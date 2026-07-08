package br.dev.ctrls.inovareti.modules.access.domain.model;

/**
 * Define o tipo de usuário que está solicitando acesso à clínica.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
public enum UserType {
    /**
     * Paciente titular do agendamento.
     */
    PATIENT,

    /**
     * Acompanhante do paciente.
     */
    COMPANION
}
