package br.dev.ctrls.inovareti.domain.audit;

/**
 * Enumera todas as ações críticas rastreadas pela trilha de auditoria.
 * Os valores devem coincidir com o CHECK CONSTRAINT da tabela audit_logs.
 */
public enum AuditAction {

    // ── Vault ──────────────────────────────────────────────────────────────────
    /** @deprecated use VAULT_AUTH_SUCCESS */
    @Deprecated
    VAULT_LOGIN_SUCCESS,
    /** @deprecated use VAULT_AUTH_FAIL */
    @Deprecated
    VAULT_LOGIN_FAILURE,
    /** @deprecated use VAULT_ITEM_VIEW */
    @Deprecated
    VAULT_SECRET_VIEW,
    VAULT_FILE_VIEW,
    VAULT_ITEM_CREATE,
    VAULT_ITEM_EDIT,
    VAULT_ITEM_DELETE,
    /** Autenticação 2FA bem-sucedida para acesso ao cofre. */
    VAULT_AUTH_SUCCESS,
    /** Falha na autenticação 2FA para acesso ao cofre. */
    VAULT_AUTH_FAIL,
    /** Visualização de conteúdo secreto ou arquivo do cofre. */
    VAULT_ITEM_VIEW,

    // ── Autenticação ───────────────────────────────────────────────────────────
    LOGIN_SUCCESS,
    LOGIN_FAILURE,

    // ── Segundo fator ──────────────────────────────────────────────────────────
    TWO_FACTOR_RESET,
    /** @deprecated use USER_2FA_ADMIN_RESET */
    @Deprecated
    TWO_FACTOR_ADMIN_RESET,
    /** Reset de 2FA realizado por um administrador. */
    USER_2FA_ADMIN_RESET,

    // ── Chamados ───────────────────────────────────────────────────────────────
    TICKET_OPEN,
    TICKET_ASSIGN,
    TICKET_TRANSFER,
    TICKET_RESOLVE,

    // ── Inventário ─────────────────────────────────────────────────────────────
    /** @deprecated use STOCK_BATCH_CREATE */
    @Deprecated
    INVENTORY_BATCH_ENTRY,
    /** @deprecated use ITEM_CREATE */
    @Deprecated
    INVENTORY_ITEM_CREATE,
    /** Criação de lote de estoque. */
    STOCK_BATCH_CREATE,
    /** Criação de item de inventário. */
    ITEM_CREATE,

    // ── Ativos ─────────────────────────────────────────────────────────────────
    ASSET_CREATE,
    ASSET_INVOICE_ATTACH,
    /** @deprecated use ASSET_QR_SCAN */
    @Deprecated
    QR_SCAN,
    /** Edição de ativo (CMDB). */
    ASSET_EDIT,
    /** Escaneamento de QR Code de ativo. */
    ASSET_QR_SCAN,

    // ── Base de Conhecimento ───────────────────────────────────────────────────
    /** @deprecated use ARTICLE_POST_DRAFT */
    @Deprecated
    KB_ARTICLE_DRAFT_CREATE,
    /** @deprecated use ARTICLE_POST_PUBLIC */
    @Deprecated
    KB_ARTICLE_PUBLISH,
    /** @deprecated use ARTICLE_EDIT */
    @Deprecated
    KB_ARTICLE_EDIT,
    /** Publicação de artigo (status PUBLISHED). */
    ARTICLE_POST_PUBLIC,
    /** Criação/salvamento de rascunho de artigo (status DRAFT). */
    ARTICLE_POST_DRAFT,
    /** Edição de artigo existente. */
    ARTICLE_EDIT,

    // ── Gestão de usuários e setores ───────────────────────────────────────────
    SECTOR_CREATE,
    USER_CREATE,
    /** @deprecated use USER_EDIT */
    @Deprecated
    USER_UPDATE,
    /** Edição de dados de usuário (por admin). */
    USER_EDIT,
    /** @deprecated use USER_PASSWORD_ADMIN_RESET */
    @Deprecated
    USER_PASSWORD_RESET,
    /** Reset de senha realizado por administrador. */
    USER_PASSWORD_ADMIN_RESET,
    USER_PERMISSION_CHANGE,

    // ── Perfil ─────────────────────────────────────────────────────────────────
    /** Alteração de senha realizada pelo próprio usuário. */
    PROFILE_PASSWORD_CHANGE
}
