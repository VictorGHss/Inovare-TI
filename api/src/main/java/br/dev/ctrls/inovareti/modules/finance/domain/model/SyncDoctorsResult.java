package br.dev.ctrls.inovareti.modules.finance.domain.model;

/**
 * Resultado sumarizado do sincronizador de médicos entre sistema local
 * e Conta Azul, contendo contagens de novos registros e atualizações.
 */
public record SyncDoctorsResult(
        int novos,
        int atualizados) {
}

