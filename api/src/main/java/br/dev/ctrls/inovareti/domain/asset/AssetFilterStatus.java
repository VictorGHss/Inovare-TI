package br.dev.ctrls.inovareti.domain.asset;

/**
 * Filtro de status para consulta de ativos.
 * ALL → todos os ativos; IN_USE → vinculados a um usuário; IN_STOCK → sem vínculo.
 */
public enum AssetFilterStatus {
    ALL,
    IN_USE,
    IN_STOCK
}
