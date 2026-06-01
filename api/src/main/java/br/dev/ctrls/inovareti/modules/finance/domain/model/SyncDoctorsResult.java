package br.dev.ctrls.inovareti.modules.finance.domain.model;

/**
 * Resultado sumarizado do sincronizador de mÃ©dicos entre sistema local
 * e Conta Azul, contendo contagens de novos registros e atualizaÃ§Ãµes.
 */
public record SyncDoctorsResult(
        int novos,
        int atualizados) {
}

