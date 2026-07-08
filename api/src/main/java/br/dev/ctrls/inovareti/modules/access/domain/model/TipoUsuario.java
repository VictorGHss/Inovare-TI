package br.dev.ctrls.inovareti.modules.access.domain.model;

/**
 * Define o tipo de usuário que está solicitando acesso à clínica.
 */
public enum TipoUsuario {
    /**
     * Paciente titular do agendamento.
     */
    PACIENTE,

    /**
     * Acompanhante do paciente.
     */
    ACOMPANHANTE
}
