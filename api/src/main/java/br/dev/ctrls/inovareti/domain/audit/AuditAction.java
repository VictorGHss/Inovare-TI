package br.dev.ctrls.inovareti.domain.audit;

/**
 * Enumera todas as ações críticas rastreadas pela trilha de auditoria.
 * Os valores devem coincidir com o CHECK CONSTRAINT da tabela audit_logs.
 */
public enum AuditAction {

    // Ações do Vault
    VAULT_LOGIN_SUCCESS,
    VAULT_LOGIN_FAILURE,
    VAULT_SECRET_VIEW,
    VAULT_FILE_VIEW,
    VAULT_ITEM_CREATE,
    VAULT_ITEM_EDIT,
    VAULT_ITEM_DELETE,

    // Ações de autenticação
    LOGIN_SUCCESS,
    LOGIN_FAILURE,

    // Ações de segundo fator
    TWO_FACTOR_RESET,
    TWO_FACTOR_ADMIN_RESET,

    // Ações de chamados
    TICKET_OPEN,
    TICKET_ASSIGN,
    TICKET_TRANSFER,
    TICKET_RESOLVE,

    // Ações de inventário e ativos
    INVENTORY_BATCH_ENTRY,
    INVENTORY_ITEM_CREATE,
    ASSET_CREATE,
    ASSET_INVOICE_ATTACH,
    QR_SCAN,

    // Ações da base de conhecimento
    KB_ARTICLE_DRAFT_CREATE,
    KB_ARTICLE_PUBLISH,
    KB_ARTICLE_EDIT,

    // Ações administrativas
    SECTOR_CREATE,
    USER_CREATE,
    USER_UPDATE,
    USER_PASSWORD_RESET,
    USER_PERMISSION_CHANGE
}
