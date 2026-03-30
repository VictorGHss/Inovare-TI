-- =============================================================================
-- V1__init.sql - Schema inicial do Inovare TI (Versão Consolidada)
-- PostgreSQL 16 | Gerenciado pelo Flyway
-- =============================================================================

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
    status      varchar(20)  NOT NULL DEFAULT 'PUBLISHED',
    created_at  timestamp    NOT NULL,
    updated_at  timestamp,
    CONSTRAINT pk_articles PRIMARY KEY (id),
    CONSTRAINT ck_articles_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE TABLE processing_attempts (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    sale_id varchar(120) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    last_attempt_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_processing_attempts PRIMARY KEY (id),
    CONSTRAINT uq_processing_attempts_sale_id UNIQUE (sale_id)
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
    contaazul_id         varchar(120),
    totp_secret          varchar(500),
    recovery_code_hash   varchar(255),
    recovery_code_expires_at timestamp,
    receives_it_notifications boolean NOT NULL DEFAULT true,
    CONSTRAINT pk_users        PRIMARY KEY (id),
    CONSTRAINT uq_users_email  UNIQUE      (email),
    CONSTRAINT uq_users_contaazul_id UNIQUE (contaazul_id),
    CONSTRAINT ck_users_role   CHECK       (role IN ('ADMIN', 'TECHNICIAN', 'USER')),
    CONSTRAINT fk_users_sector FOREIGN KEY (sector_id) REFERENCES sectors (id)
);

-- =============================================================================
-- BLOCO 3 - notifications
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
    CONSTRAINT fk_items_category FOREIGN KEY (item_category_id) REFERENCES item_categories (id)
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
    CONSTRAINT fk_tickets_requester FOREIGN KEY (requester_id)      REFERENCES users(id),
    CONSTRAINT fk_tickets_assigned  FOREIGN KEY (assigned_to_id)    REFERENCES users(id),
    CONSTRAINT fk_tickets_category  FOREIGN KEY (category_id)       REFERENCES ticket_categories(id),
    CONSTRAINT fk_tickets_item      FOREIGN KEY (requested_item_id) REFERENCES items(id)
);

-- =============================================================================
-- BLOCO 6 - ticket_attachments
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
-- BLOCO 7 - ticket_comments
-- =============================================================================

CREATE TABLE ticket_comments (
    id         uuid      NOT NULL DEFAULT gen_random_uuid(),
    content    text      NOT NULL,
    ticket_id  uuid      NOT NULL,
    author_id  uuid      NOT NULL,
    created_at timestamp NOT NULL,
    CONSTRAINT pk_ticket_comments        PRIMARY KEY (id),
    CONSTRAINT fk_ticket_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_ticket_comments_author FOREIGN KEY (author_id) REFERENCES users (id)
);

-- =============================================================================
-- BLOCO 8 - stock_batches (FIX: remaining_quantity >= 0 para FIFO)
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
    CONSTRAINT ck_stock_batches_rem_qty CHECK       (remaining_quantity >= 0),
    CONSTRAINT fk_stock_batches_item    FOREIGN KEY (item_id) REFERENCES items (id)
);

-- =============================================================================
-- BLOCO 9 - stock_movements (Incluso unit_price_at_time)
-- =============================================================================

CREATE TABLE stock_movements (
    id        uuid         NOT NULL DEFAULT gen_random_uuid(),
    item_id   uuid         NOT NULL,
    type      varchar(10)  NOT NULL,
    quantity  integer      NOT NULL,
    unit_price_at_time numeric(19,2),
    reference varchar(255) NOT NULL,
    date      timestamp    NOT NULL,
    CONSTRAINT pk_stock_movements      PRIMARY KEY (id),
    CONSTRAINT ck_stock_movements_type CHECK       (type     IN ('IN', 'OUT')),
    CONSTRAINT ck_stock_movements_qty  CHECK       (quantity >= 1),
    CONSTRAINT fk_stock_movements_item FOREIGN KEY (item_id) REFERENCES items (id)
);

-- =============================================================================
-- BLOCO 10 - assets (Incluso acquisition_value)
-- =============================================================================

CREATE TABLE assets (
    id                   uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id              uuid,
    name                 varchar(150) NOT NULL,
    patrimony_code       varchar(80)  NOT NULL,
    category_id          uuid,
    specifications       text,
    acquisition_value    numeric(19,2),
    invoice_file_name    varchar(255),
    invoice_content_type varchar(50),
    invoice_file_path    varchar(500),
    created_at           timestamp    NOT NULL,
    CONSTRAINT pk_assets                PRIMARY KEY (id),
    CONSTRAINT uq_assets_patrimony_code UNIQUE      (patrimony_code),
    CONSTRAINT fk_assets_category       FOREIGN KEY (category_id) REFERENCES asset_categories (id)
);

-- =============================================================================
-- BLOCO 11 - asset_maintenances
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
-- BLOCO 12 - vault_items e shares
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
    CONSTRAINT pk_vault_items PRIMARY KEY (id),
    CONSTRAINT fk_vault_items_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE vault_item_shares (
    id                  uuid      NOT NULL DEFAULT gen_random_uuid(),
    vault_item_id       uuid      NOT NULL,
    shared_with_user_id uuid      NOT NULL,
    CONSTRAINT pk_vault_item_shares PRIMARY KEY (id),
    CONSTRAINT fk_vault_item_shares_vault_item FOREIGN KEY (vault_item_id) REFERENCES vault_items (id),
    CONSTRAINT fk_vault_item_shares_shared_with_user FOREIGN KEY (shared_with_user_id) REFERENCES users (id)
);

-- =============================================================================
-- BLOCO 13 - audit_logs
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
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

-- =============================================================================
-- BLOCO 14 - MODULO FINANCEIRO (ERP + Transações)
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
    contaazul_customer_id varchar(100) NOT NULL,
    contaazul_customer_name varchar(160),
    email                 varchar(255),
    nome_cliente          varchar(255),
    email_synced_at       timestamp with time zone,
    contaazul_pessoa_uuid varchar(36),
    canal                 varchar(20)  NOT NULL DEFAULT 'EMAIL',
    linked_by_user_id     uuid,
    created_at            timestamp    NOT NULL DEFAULT now(),
    updated_at            timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_financial_link PRIMARY KEY (id),
    CONSTRAINT uq_financial_link_customer_canal UNIQUE (contaazul_customer_id, canal),
    CONSTRAINT ck_financial_link_canal CHECK (canal IN ('EMAIL', 'DISCORD')),
    CONSTRAINT fk_financial_link_linked_by FOREIGN KEY (linked_by_user_id) REFERENCES users (id)
);

CREATE TABLE doctor_email_mapping (
    id                      uuid         NOT NULL DEFAULT gen_random_uuid(),
    contaazul_customer_uuid varchar(64)  NOT NULL,
    user_id                 uuid,
    doctor_name             varchar(160),
    doctor_email            varchar(255),
    created_at              timestamp    NOT NULL DEFAULT now(),
    updated_at              timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_doctor_email_mapping PRIMARY KEY (id),
    CONSTRAINT uq_doctor_email_mapping_customer_uuid UNIQUE (contaazul_customer_uuid),
    CONSTRAINT fk_doctor_email_mapping_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE processed_sales (
    id           uuid         NOT NULL DEFAULT gen_random_uuid(),
    sale_id      varchar(120) NOT NULL,
    processed_at timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_sales PRIMARY KEY (id),
    CONSTRAINT uq_processed_sales_sale_id UNIQUE (sale_id)
);

CREATE TABLE processed_receipts (
    id                       uuid          NOT NULL DEFAULT gen_random_uuid(),
    financial_link_id        uuid          NOT NULL,
    parcela_id               varchar(120)  NOT NULL,
    receipt_hash             varchar(128)  NOT NULL,
    original_recipient_email varchar(255)  NOT NULL,
    status                   varchar(20)   NOT NULL DEFAULT 'SENT',
    brevo_message_id         varchar(120),
    retry_count              integer       NOT NULL DEFAULT 0,
    payload                  jsonb         NOT NULL DEFAULT '{}'::jsonb,
    processed_at             timestamp     NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_receipts PRIMARY KEY (id),
    CONSTRAINT uq_processed_receipts_parcela_hash UNIQUE (parcela_id, receipt_hash),
    CONSTRAINT ck_processed_receipts_status CHECK (status IN ('SENT', 'SKIPPED_DUPLICATE', 'FAILED', 'PENDING_RETRY', 'HISTORICO')),
    CONSTRAINT fk_processed_receipts_financial_link FOREIGN KEY (financial_link_id) REFERENCES financial_link (id)
);

CREATE TABLE system_alerts (
    id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    alert_type  varchar(60)  NOT NULL,
    severity    varchar(20)  NOT NULL DEFAULT 'ERROR',
    source      varchar(120) NOT NULL,
    title       varchar(255) NOT NULL,
    details     text,
    context     jsonb        NOT NULL DEFAULT '{}'::jsonb,
    resolved    boolean      NOT NULL DEFAULT false,
    resolved_by uuid,
    created_at  timestamp    NOT NULL DEFAULT now(),
    resolved_at timestamp,
    CONSTRAINT pk_system_alerts PRIMARY KEY (id),
    CONSTRAINT ck_system_alerts_severity CHECK (severity IN ('INFO', 'WARN', 'ERROR', 'CRITICAL'))
);

-- BLOCO 14.A - financial_transactions
CREATE TABLE financial_transactions (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    target_type varchar(10) NOT NULL,
    target_id uuid NOT NULL,
    resource_type varchar(10) NOT NULL,
    amount numeric(19,2) NOT NULL,
    ticket_id uuid,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_financial_transactions PRIMARY KEY (id),
    CONSTRAINT ck_financial_transactions_target_type CHECK (target_type IN ('DOCTOR','SECTOR')),
    CONSTRAINT ck_financial_transactions_resource_type CHECK (resource_type IN ('INVENTORY','ASSET')),
    CONSTRAINT fk_financial_transactions_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE SET NULL
);

-- =============================================================================
-- BLOCO 15 - report_schedules (Automação Dia 12)
-- =============================================================================

CREATE TABLE report_schedules (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    report_type varchar(60) NOT NULL,
    target_user_id uuid,
    send_email boolean NOT NULL DEFAULT true,
    send_discord boolean NOT NULL DEFAULT false,
    schedule_day integer NOT NULL DEFAULT 12,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_report_schedules PRIMARY KEY (id),
    CONSTRAINT fk_report_schedules_user FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- Índices e Configurações de Performance
CREATE INDEX idx_tickets_status ON tickets (status);
CREATE INDEX idx_financial_transactions_created_at ON financial_transactions (created_at DESC);
CREATE INDEX idx_report_schedules_active ON report_schedules (is_active);