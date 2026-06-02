package br.dev.ctrls.inovareti.modules.asset.domain.model;

/**
 * Filtro de status para consulta de ativos.
 * ALL → todos os ativos; IN_USE → vinculados a um usuário; IN_STOCK → sem vínculo.
 */
public enum AssetFilterStatus {
    ALL,
    IN_USE,
    IN_STOCK
}
