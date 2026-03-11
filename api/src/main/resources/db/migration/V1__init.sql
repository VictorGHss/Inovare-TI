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