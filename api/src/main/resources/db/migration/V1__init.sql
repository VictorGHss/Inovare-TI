-- =============================================================================
-- V1__init.sql - Schema inicial do Inovare TI
-- PostgreSQL 16 | Gerenciado pelo Flyway
-- Ordem de criacao respeita dependencias de Foreign Keys
-- =============================================================================

-- Extensao para gen_random_uuid() (disponivel no PostgreSQL 13+)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =============================================================================
-- BLOCO 1 - Tabelas independentes (sem FK)
-- =============================================================================

CREATE TABLE sectors (
    id   uuid         NOT NULL DEFAULT gen_random_uuid(),
    name varchar(100) NOT NULL,
    CONSTRAINT pk_sectors      PRIMARY KEY (id),
    CONSTRAINT uq_sectors_name UNIQUE      (name)
);

CREATE TABLE asset_categories (
    id   uuid         NOT NULL DEFAULT gen_random_uuid(),
    name varchar(100) NOT NULL,
    CONSTRAINT pk_asset_categories      PRIMARY KEY (id),
    CONSTRAINT uq_asset_categories_name UNIQUE      (name)
);

CREATE TABLE item_categories (
    id            uuid         NOT NULL DEFAULT gen_random_uuid(),
    name          varchar(100) NOT NULL,
    is_consumable boolean      NOT NULL,
    CONSTRAINT pk_item_categories      PRIMARY KEY (id),
    CONSTRAINT uq_item_categories_name UNIQUE      (name)
);

CREATE TABLE ticket_categories (
    id             uuid         NOT NULL DEFAULT gen_random_uuid(),
    name           varchar(100) NOT NULL,
    base_sla_hours integer      NOT NULL,
    CONSTRAINT pk_ticket_categories      PRIMARY KEY (id),
    CONSTRAINT uq_ticket_categories_name UNIQUE      (name),
    CONSTRAINT ck_ticket_categories_sla  CHECK       (base_sla_hours >= 1)
);

CREATE TABLE system_settings (
    id          varchar(100) NOT NULL,
    value       varchar(255) NOT NULL,
    description varchar(500),
    CONSTRAINT pk_system_settings PRIMARY KEY (id)
);

CREATE TABLE articles (
    id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    title       varchar(255) NOT NULL,
    content     text         NOT NULL,
    author_id   uuid         NOT NULL,
    author_name varchar(255) NOT NULL,
    tags        varchar(500),
    created_at  timestamp    NOT NULL,
    updated_at  timestamp,
    CONSTRAINT pk_articles PRIMARY KEY (id)
);

-- =============================================================================
-- BLOCO 2 - users: depende de sectors
-- =============================================================================

CREATE TABLE users (
    id                   uuid         NOT NULL DEFAULT gen_random_uuid(),
    name                 varchar(150) NOT NULL,
    email                varchar(255) NOT NULL,
    password_hash        varchar(255) NOT NULL,
    must_change_password boolean      NOT NULL DEFAULT false,
    role                 varchar(20)  NOT NULL,
    sector_id            uuid         NOT NULL,
    location             varchar(150) NOT NULL,
    discord_user_id      varchar(50),
    totp_secret          varchar(500),
    recovery_code_hash   varchar(255),
    recovery_code_expires_at timestamp,
    CONSTRAINT pk_users        PRIMARY KEY (id),
    CONSTRAINT uq_users_email  UNIQUE      (email),
    CONSTRAINT ck_users_role   CHECK       (role IN ('ADMIN', 'TECHNICIAN', 'USER')),
    CONSTRAINT fk_users_sector FOREIGN KEY (sector_id) REFERENCES sectors (id)
);

-- =============================================================================
-- BLOCO 3 - notifications: user_id e coluna simples (sem FK na entidade)
-- =============================================================================

CREATE TABLE notifications (
    id         uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id    uuid         NOT NULL,
    title      varchar(255) NOT NULL,
    message    text         NOT NULL,
    is_read    boolean      NOT NULL DEFAULT false,
    link       varchar(500),
    created_at timestamp    NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

-- =============================================================================
-- BLOCO 4 - items: depende de item_categories
-- =============================================================================

CREATE TABLE items (
    id               uuid         NOT NULL DEFAULT gen_random_uuid(),
    item_category_id uuid         NOT NULL,
    name             varchar(150) NOT NULL,
    current_stock    integer      NOT NULL,
    specifications   jsonb        NOT NULL DEFAULT '{}',
    CONSTRAINT pk_items          PRIMARY KEY (id),
    CONSTRAINT ck_items_stock    CHECK       (current_stock >= 0),
    CONSTRAINT fk_items_category FOREIGN KEY (item_category_id)
        REFERENCES item_categories (id)
);

-- =============================================================================
-- BLOCO 5 - tickets: depende de users, ticket_categories e items
-- =============================================================================

CREATE TABLE tickets (
    id                 uuid         NOT NULL DEFAULT gen_random_uuid(),
    title              varchar(200) NOT NULL,
    description        text,
    anydesk_code       varchar(500),
    status             varchar(20)  NOT NULL,
    priority           varchar(10)  NOT NULL,
    requester_id       uuid         NOT NULL,
    assigned_to_id     uuid,
    category_id        uuid         NOT NULL,
    requested_item_id  uuid,
    requested_quantity integer,
    sla_deadline       timestamp    NOT NULL,
    created_at         timestamp    NOT NULL,
    closed_at          timestamp,
    CONSTRAINT pk_tickets          PRIMARY KEY (id),
    CONSTRAINT ck_tickets_status   CHECK       (status   IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    CONSTRAINT ck_tickets_priority CHECK       (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT fk_tickets_requester FOREIGN KEY (requester_id)      REFERENCES users             (id),
    CONSTRAINT fk_tickets_assigned  FOREIGN KEY (assigned_to_id)    REFERENCES users             (id),
    CONSTRAINT fk_tickets_category  FOREIGN KEY (category_id)       REFERENCES ticket_categories (id),
    CONSTRAINT fk_tickets_item      FOREIGN KEY (requested_item_id) REFERENCES items             (id)
);

-- =============================================================================
-- BLOCO 6 - ticket_attachments: depende de tickets
-- =============================================================================

CREATE TABLE ticket_attachments (
    id                uuid         NOT NULL DEFAULT gen_random_uuid(),
    original_filename varchar(255) NOT NULL,
    stored_filename   varchar(255) NOT NULL,
    file_type         varchar(100) NOT NULL,
    ticket_id         uuid         NOT NULL,
    uploaded_at       timestamp    NOT NULL,
    CONSTRAINT pk_ticket_attachments        PRIMARY KEY (id),
    CONSTRAINT uq_ticket_attachments_stored UNIQUE      (stored_filename),
    CONSTRAINT fk_ticket_attachments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
);

-- =============================================================================
-- BLOCO 7 - ticket_comments: depende de tickets e users
-- =============================================================================

CREATE TABLE ticket_comments (
    id         uuid      NOT NULL DEFAULT gen_random_uuid(),
    content    text      NOT NULL,
    ticket_id  uuid      NOT NULL,
    author_id  uuid      NOT NULL,
    created_at timestamp NOT NULL,
    CONSTRAINT pk_ticket_comments        PRIMARY KEY (id),
    CONSTRAINT fk_ticket_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_ticket_comments_author FOREIGN KEY (author_id) REFERENCES users   (id)
);

-- =============================================================================
-- BLOCO 8 - stock_batches: depende de items
-- =============================================================================

CREATE TABLE stock_batches (
    id                   uuid           NOT NULL DEFAULT gen_random_uuid(),
    item_id              uuid           NOT NULL,
    original_quantity    integer        NOT NULL,
    remaining_quantity   integer        NOT NULL,
    unit_price           numeric(12, 2) NOT NULL,
    brand                varchar(100),
    supplier             varchar(150),
    purchase_reason      varchar(200),
    entry_date           timestamp      NOT NULL,
    invoice_file_name    varchar(255),
    invoice_content_type varchar(50),
    invoice_file_path    varchar(500),
    CONSTRAINT pk_stock_batches         PRIMARY KEY (id),
    CONSTRAINT ck_stock_batches_qty     CHECK       (original_quantity  >= 1),
    CONSTRAINT ck_stock_batches_rem_qty CHECK       (remaining_quantity >= 1),
    CONSTRAINT fk_stock_batches_item    FOREIGN KEY (item_id) REFERENCES items (id)
);

-- =============================================================================
-- BLOCO 9 - stock_movements: item_id como UUID simples (conforme entidade)
-- =============================================================================

CREATE TABLE stock_movements (
    id        uuid         NOT NULL DEFAULT gen_random_uuid(),
    item_id   uuid         NOT NULL,
    type      varchar(10)  NOT NULL,
    quantity  integer      NOT NULL,
    reference varchar(255) NOT NULL,
    date      timestamp    NOT NULL,
    CONSTRAINT pk_stock_movements      PRIMARY KEY (id),
    CONSTRAINT ck_stock_movements_type CHECK       (type     IN ('IN', 'OUT')),
    CONSTRAINT ck_stock_movements_qty  CHECK       (quantity >= 1),
    CONSTRAINT fk_stock_movements_item FOREIGN KEY (item_id) REFERENCES items (id)
);

-- =============================================================================
-- BLOCO 10 - assets: depende de asset_categories; user_id e UUID simples
-- =============================================================================

CREATE TABLE assets (
    id                   uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id              uuid,
    name                 varchar(150) NOT NULL,
    patrimony_code       varchar(80)  NOT NULL,
    category_id          uuid,
    specifications       text,
    invoice_file_name    varchar(255),
    invoice_content_type varchar(50),
    invoice_file_path    varchar(500),
    created_at           timestamp    NOT NULL,
    CONSTRAINT pk_assets                PRIMARY KEY (id),
    CONSTRAINT uq_assets_patrimony_code UNIQUE      (patrimony_code),
    CONSTRAINT fk_assets_category       FOREIGN KEY (category_id) REFERENCES asset_categories (id)
);

-- =============================================================================
-- BLOCO 11 - asset_maintenances: depende de assets e users
-- =============================================================================

CREATE TABLE asset_maintenances (
    id               uuid           NOT NULL DEFAULT gen_random_uuid(),
    asset_id         uuid           NOT NULL,
    maintenance_date date           NOT NULL,
    type             varchar(20)    NOT NULL,
    description      text,
    cost             numeric(10, 2),
    technician_id    uuid           NOT NULL,
    created_at       timestamp      NOT NULL,
    CONSTRAINT pk_asset_maintenances            PRIMARY KEY (id),
    CONSTRAINT ck_asset_maintenances_type       CHECK       (type IN ('PREVENTIVE', 'CORRECTIVE', 'UPGRADE', 'TRANSFER')),
    CONSTRAINT fk_asset_maintenances_asset      FOREIGN KEY (asset_id)      REFERENCES assets (id),
    CONSTRAINT fk_asset_maintenances_technician FOREIGN KEY (technician_id) REFERENCES users  (id)
);

-- =============================================================================
-- INDICES DE PERFORMANCE
-- =============================================================================

CREATE INDEX idx_tickets_status       ON tickets (status);
CREATE INDEX idx_tickets_requester    ON tickets (requester_id);
CREATE INDEX idx_tickets_assigned     ON tickets (assigned_to_id);
CREATE INDEX idx_tickets_created_at   ON tickets (created_at   DESC);
CREATE INDEX idx_tickets_sla_deadline ON tickets (sla_deadline ASC);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_notifications_unread  ON notifications (user_id) WHERE is_read = false;

CREATE INDEX idx_articles_created_at ON articles (created_at DESC);

CREATE INDEX idx_stock_batches_item_id   ON stock_batches   (item_id);
CREATE INDEX idx_stock_movements_item_id ON stock_movements (item_id);
CREATE INDEX idx_stock_movements_date    ON stock_movements (date DESC);

CREATE INDEX idx_assets_user_id              ON assets             (user_id);
CREATE INDEX idx_asset_maintenances_asset_id ON asset_maintenances (asset_id);

-- =============================================================================
-- BLOCO 12 - vault_items e vault_item_shares: depende de users e vault_items
-- =============================================================================

CREATE TABLE vault_items (
    id             uuid         NOT NULL DEFAULT gen_random_uuid(),
    title          varchar(150) NOT NULL,
    description    text,
    item_type      varchar(20)  NOT NULL,
    secret_content text,
    file_path      varchar(500),
    owner_id       uuid         NOT NULL,
    sharing_type   varchar(20)  NOT NULL,
    created_at     timestamp    NOT NULL,
    updated_at     timestamp    NOT NULL,
    CONSTRAINT pk_vault_items                    PRIMARY KEY (id),
    CONSTRAINT ck_vault_items_item_type          CHECK       (item_type IN ('CREDENTIAL', 'DOCUMENT', 'NOTE')),
    CONSTRAINT ck_vault_items_sharing_type       CHECK       (sharing_type IN ('PRIVATE', 'ALL_TECH_ADMIN', 'CUSTOM')),
    CONSTRAINT fk_vault_items_owner              FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE vault_item_shares (
    id                  uuid      NOT NULL DEFAULT gen_random_uuid(),
    vault_item_id       uuid      NOT NULL,
    shared_with_user_id uuid      NOT NULL,
    CONSTRAINT pk_vault_item_shares                     PRIMARY KEY (id),
    CONSTRAINT fk_vault_item_shares_vault_item          FOREIGN KEY (vault_item_id) REFERENCES vault_items (id),
    CONSTRAINT fk_vault_item_shares_shared_with_user    FOREIGN KEY (shared_with_user_id) REFERENCES users (id)
);

CREATE INDEX idx_vault_items_owner_id          ON vault_items       (owner_id);
CREATE INDEX idx_vault_items_sharing_type      ON vault_items       (sharing_type);
CREATE INDEX idx_vault_item_shares_vault_item  ON vault_item_shares (vault_item_id);
CREATE INDEX idx_vault_item_shares_user        ON vault_item_shares (shared_with_user_id);

-- =============================================================================
-- BLOCO 13 - audit_logs: tabela de trilha de auditoria (sem FK para preservar historico)
-- =============================================================================

CREATE TABLE audit_logs (
    id            uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id       uuid,
    action        varchar(60)  NOT NULL,
    resource_type varchar(60),
    resource_id   uuid,
    details       text,
    ip_address    varchar(45),
    created_at    timestamp    NOT NULL,
    CONSTRAINT pk_audit_logs        PRIMARY KEY (id),
    CONSTRAINT ck_audit_logs_action CHECK       (action IN (
        'VAULT_SECRET_VIEW',
        'VAULT_FILE_VIEW',
        'VAULT_ITEM_CREATE',
        'LOGIN_SUCCESS',
        'LOGIN_FAILURE',
        'TWO_FACTOR_RESET',
        'TWO_FACTOR_ADMIN_RESET',
        'USER_PERMISSION_CHANGE'
    ))
);

CREATE INDEX idx_audit_logs_user_id    ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action     ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);

-- =============================================================================
-- NOTA DE SINCRONIZACAO DO SCHEMA BASE
-- A tabela users ja contempla no schema inicial as colunas recovery_code_hash
-- (varchar(255)) e recovery_code_expires_at (timestamp) para recuperacao de 2FA.
-- =============================================================================

-- =============================================================================
-- AJUSTES INCREMENTAIS V1 (SEM NOVA MIGRACAO)
-- =============================================================================

ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS status varchar(20) NOT NULL DEFAULT 'PUBLISHED';

ALTER TABLE articles
    DROP CONSTRAINT IF EXISTS ck_articles_status;

ALTER TABLE articles
    ADD CONSTRAINT ck_articles_status CHECK (status IN ('DRAFT', 'PUBLISHED'));

ALTER TABLE audit_logs
    DROP CONSTRAINT IF EXISTS ck_audit_logs_action;

ALTER TABLE audit_logs
    ADD CONSTRAINT ck_audit_logs_action CHECK (action IN (
        'VAULT_LOGIN_SUCCESS',
        'VAULT_LOGIN_FAILURE',
        'VAULT_SECRET_VIEW',
        'VAULT_FILE_VIEW',
        'VAULT_ITEM_CREATE',
        'VAULT_ITEM_EDIT',
        'VAULT_ITEM_DELETE',
        'LOGIN_SUCCESS',
        'LOGIN_FAILURE',
        'TWO_FACTOR_RESET',
        'TWO_FACTOR_ADMIN_RESET',
        'TICKET_OPEN',
        'TICKET_ASSIGN',
        'TICKET_TRANSFER',
        'TICKET_RESOLVE',
        'INVENTORY_BATCH_ENTRY',
        'INVENTORY_ITEM_CREATE',
        'ASSET_CREATE',
        'ASSET_INVOICE_ATTACH',
        'QR_SCAN',
        'KB_ARTICLE_DRAFT_CREATE',
        'KB_ARTICLE_PUBLISH',
        'KB_ARTICLE_EDIT',
        'SECTOR_CREATE',
        'USER_CREATE',
        'USER_UPDATE',
        'USER_PASSWORD_RESET',
        'USER_PERMISSION_CHANGE'
    ));

-- =============================================================================
-- PROTOCOLO DE AUDITORIA TOTAL 360 - Novos valores canonicos de auditoria
-- Mantidos os antigos para compatibilidade com registros historicos.
-- =============================================================================

ALTER TABLE audit_logs
    DROP CONSTRAINT IF EXISTS ck_audit_logs_action;

ALTER TABLE audit_logs
    ADD CONSTRAINT ck_audit_logs_action CHECK (action IN (
        -- Vault (legado + canonico)
        'VAULT_LOGIN_SUCCESS', 'VAULT_LOGIN_FAILURE',
        'VAULT_SECRET_VIEW',   'VAULT_FILE_VIEW',
        'VAULT_ITEM_CREATE',   'VAULT_ITEM_EDIT',   'VAULT_ITEM_DELETE',
        'VAULT_AUTH_SUCCESS',  'VAULT_AUTH_FAIL',   'VAULT_ITEM_VIEW',
        -- Autenticacao
        'LOGIN_SUCCESS', 'LOGIN_FAILURE',
        -- Segundo fator (legado + canonico)
        'TWO_FACTOR_RESET', 'TWO_FACTOR_ADMIN_RESET', 'USER_2FA_ADMIN_RESET',
        -- Chamados
        'TICKET_OPEN', 'TICKET_ASSIGN', 'TICKET_TRANSFER', 'TICKET_RESOLVE',
        -- Inventario (legado + canonico)
        'INVENTORY_BATCH_ENTRY', 'INVENTORY_ITEM_CREATE',
        'STOCK_BATCH_CREATE',    'ITEM_CREATE',
        -- Ativos (legado + canonico)
        'ASSET_CREATE', 'ASSET_INVOICE_ATTACH', 'QR_SCAN',
        'ASSET_EDIT',   'ASSET_QR_SCAN',
        -- Base de Conhecimento (legado + canonico)
        'KB_ARTICLE_DRAFT_CREATE', 'KB_ARTICLE_PUBLISH', 'KB_ARTICLE_EDIT',
        'ARTICLE_POST_PUBLIC',     'ARTICLE_POST_DRAFT',  'ARTICLE_EDIT',
        -- Usuarios e setores (legado + canonico)
        'SECTOR_CREATE',
        'USER_CREATE',
        'USER_UPDATE',           'USER_EDIT',
        'USER_PASSWORD_RESET',   'USER_PASSWORD_ADMIN_RESET',
        'USER_PERMISSION_CHANGE',
        -- Perfil
        'PROFILE_PASSWORD_CHANGE'
    ));

-- =============================================================================
-- AJUSTES INCREMENTAIS NO SCHEMA BASE (manter sempre ao final do V1__init.sql)
-- =============================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS receives_it_notifications boolean NOT NULL DEFAULT true;

-- =============================================================================
-- BLOCO 14 - MODULO FINANCEIRO (ContaAzul + Conferencia de Recibos)
-- =============================================================================

CREATE TABLE contaazul_oauth_tokens (
    id            uuid         NOT NULL DEFAULT gen_random_uuid(),
    access_token  text         NOT NULL,
    refresh_token text         NOT NULL,
    token_type    varchar(20)  NOT NULL DEFAULT 'Bearer',
    scope         varchar(255),
    expires_at    timestamp    NOT NULL,
    refreshed_at  timestamp,
    created_at    timestamp    NOT NULL DEFAULT now(),
    updated_at    timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_contaazul_oauth_tokens PRIMARY KEY (id)
);

CREATE TABLE financial_link (
    id                    uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id               uuid         NOT NULL,
    contaazul_customer_id varchar(100) NOT NULL,
    contaazul_customer_name varchar(160),
    linked_by_user_id     uuid,
    created_at            timestamp    NOT NULL DEFAULT now(),
    updated_at            timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_financial_link               PRIMARY KEY (id),
    CONSTRAINT uq_financial_link_user          UNIQUE      (user_id),
    CONSTRAINT uq_financial_link_customer      UNIQUE      (contaazul_customer_id),
    CONSTRAINT fk_financial_link_user          FOREIGN KEY (user_id)           REFERENCES users (id),
    CONSTRAINT fk_financial_link_linked_by     FOREIGN KEY (linked_by_user_id) REFERENCES users (id)
);

-- ✨ Tabela de conferência vibrante: rastreia processamento e idempotência de recibos
CREATE TABLE processed_receipts (
    id                       uuid          NOT NULL DEFAULT gen_random_uuid(),
    financial_link_id        uuid          NOT NULL,
    parcela_id               varchar(120)  NOT NULL,
    receipt_hash             varchar(128)  NOT NULL,
    original_recipient_email varchar(255)  NOT NULL,
    status                   varchar(20)   NOT NULL DEFAULT 'SENT',
    brevo_message_id         varchar(120),
    payload                  jsonb         NOT NULL DEFAULT '{}'::jsonb,
    processed_at             timestamp     NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_receipts                 PRIMARY KEY (id),
    CONSTRAINT uq_processed_receipts_parcela_hash    UNIQUE      (parcela_id, receipt_hash),
    CONSTRAINT ck_processed_receipts_status          CHECK       (status IN ('SENT', 'SKIPPED_DUPLICATE', 'FAILED')),
    CONSTRAINT fk_processed_receipts_financial_link  FOREIGN KEY (financial_link_id) REFERENCES financial_link (id)
);

-- 🚨 Tabela de conferência vibrante: centraliza alertas de falhas de envio (Brevo)
CREATE TABLE system_alerts (
    id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    alert_type  varchar(60)  NOT NULL,
    severity    varchar(20)  NOT NULL DEFAULT 'ERROR',
    source      varchar(120) NOT NULL,
    title       varchar(255) NOT NULL,
    details     text,
    context     jsonb        NOT NULL DEFAULT '{}'::jsonb,
    resolved    boolean      NOT NULL DEFAULT false,
    created_at  timestamp    NOT NULL DEFAULT now(),
    resolved_at timestamp,
    CONSTRAINT pk_system_alerts            PRIMARY KEY (id),
    CONSTRAINT ck_system_alerts_severity   CHECK       (severity IN ('INFO', 'WARN', 'ERROR', 'CRITICAL'))
);

CREATE INDEX idx_fl_customer ON financial_link (contaazul_customer_id);
CREATE INDEX idx_pr_parcela  ON processed_receipts (parcela_id);