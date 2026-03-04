-- Flyway Migration V1: Initial Schema for Inovare TI System
-- PostgreSQL DDL for all entities with proper foreign keys and constraints

-- =====================================================================
-- SECTORS
-- =====================================================================
CREATE TABLE sectors (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

-- =====================================================================
-- USERS
-- =====================================================================
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'TECHNICIAN', 'USER')),
    sector_id UUID NOT NULL,
    location VARCHAR(150) NOT NULL,
    discord_user_id VARCHAR(50),
    totp_secret VARCHAR(500),
    CONSTRAINT fk_users_sector FOREIGN KEY (sector_id) REFERENCES sectors(id) ON DELETE RESTRICT
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_sector ON users(sector_id);

-- =====================================================================
-- TICKET CATEGORIES
-- =====================================================================
CREATE TABLE ticket_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    base_sla_hours INTEGER NOT NULL CHECK (base_sla_hours >= 1)
);

-- =====================================================================
-- ITEM CATEGORIES
-- =====================================================================
CREATE TABLE item_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    is_consumable BOOLEAN NOT NULL
);

-- =====================================================================
-- ITEMS
-- =====================================================================
CREATE TABLE items (
    id UUID PRIMARY KEY,
    item_category_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    current_stock INTEGER NOT NULL DEFAULT 0 CHECK (current_stock >= 0),
    specifications JSONB,
    CONSTRAINT fk_items_category FOREIGN KEY (item_category_id) REFERENCES item_categories(id) ON DELETE RESTRICT
);

CREATE INDEX idx_items_category ON items(item_category_id);

-- =====================================================================
-- STOCK BATCHES
-- =====================================================================
CREATE TABLE stock_batches (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL,
    original_quantity INTEGER NOT NULL CHECK (original_quantity > 0),
    remaining_quantity INTEGER NOT NULL CHECK (remaining_quantity >= 0),
    unit_price DECIMAL(12, 2) NOT NULL,
    entry_date TIMESTAMP NOT NULL,
    CONSTRAINT fk_stock_batches_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
);

CREATE INDEX idx_stock_batches_item ON stock_batches(item_id);
CREATE INDEX idx_stock_batches_entry_date ON stock_batches(entry_date);

-- =====================================================================
-- TICKETS
-- =====================================================================
CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    anydesk_code VARCHAR(500),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    requester_id UUID NOT NULL,
    assigned_to_id UUID,
    category_id UUID NOT NULL,
    requested_item_id UUID,
    requested_quantity INTEGER,
    sla_deadline TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    CONSTRAINT fk_tickets_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_assigned_to FOREIGN KEY (assigned_to_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_tickets_category FOREIGN KEY (category_id) REFERENCES ticket_categories(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_requested_item FOREIGN KEY (requested_item_id) REFERENCES items(id) ON DELETE SET NULL
);

CREATE INDEX idx_tickets_requester ON tickets(requester_id);
CREATE INDEX idx_tickets_assigned_to ON tickets(assigned_to_id);
CREATE INDEX idx_tickets_category ON tickets(category_id);
CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_created_at ON tickets(created_at);

-- =====================================================================
-- TICKET COMMENTS
-- =====================================================================
CREATE TABLE ticket_comments (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    ticket_id UUID NOT NULL,
    author_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ticket_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_comments_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_ticket_comments_ticket ON ticket_comments(ticket_id);
CREATE INDEX idx_ticket_comments_author ON ticket_comments(author_id);
CREATE INDEX idx_ticket_comments_created_at ON ticket_comments(created_at);

-- =====================================================================
-- TICKET ATTACHMENTS
-- =====================================================================
CREATE TABLE ticket_attachments (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL UNIQUE,
    file_type VARCHAR(100) NOT NULL,
    ticket_id UUID NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ticket_attachments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);

CREATE INDEX idx_ticket_attachments_ticket ON ticket_attachments(ticket_id);

-- =====================================================================
-- NOTIFICATIONS
-- =====================================================================
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    link VARCHAR(500),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

-- =====================================================================
-- ARTICLES
-- =====================================================================
CREATE TABLE articles (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    author_id UUID NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    tags VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX idx_articles_author ON articles(author_id);
CREATE INDEX idx_articles_created_at ON articles(created_at);

-- =====================================================================
-- ASSETS
-- =====================================================================
CREATE TABLE assets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    patrimony_code VARCHAR(80) NOT NULL,
    specifications TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_assets_user ON assets(user_id);
CREATE INDEX idx_assets_patrimony_code ON assets(patrimony_code);
