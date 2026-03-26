-- V3__create_financial_transactions.sql
-- Cria a tabela financial_transactions necessária para registrar débitos direcionados a médicos/setores

CREATE TABLE IF NOT EXISTS financial_transactions (
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

CREATE INDEX IF NOT EXISTS idx_financial_transactions_target ON financial_transactions (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_financial_transactions_created_at ON financial_transactions (created_at DESC);
