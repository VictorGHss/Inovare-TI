package br.dev.ctrls.inovareti.domain.audit;

/**
 * Enumera todas as ações críticas rastreadas pela trilha de auditoria.
 * Os valores devem coincidir com o CHECK CONSTRAINT da tabela audit_logs.
 */
public enum AuditAction {

    // Ações do Vault
    VAULT_SECRET_VIEW,
    VAULT_FILE_VIEW,
    VAULT_ITEM_CREATE,

    // Ações de autenticação
    LOGIN_SUCCESS,
    LOGIN_FAILURE,

    // Ações de segundo fator
    TWO_FACTOR_RESET,
    TWO_FACTOR_ADMIN_RESET,

    // Ações administrativas
    USER_PERMISSION_CHANGE
}
