-- V3__Create_Processing_Attempts.sql
-- Cria a tabela processing_attempts para rastrear tentativas de download de recibos

CREATE TABLE IF NOT EXISTS processing_attempts (
    id BIGSERIAL PRIMARY KEY,
    sale_id VARCHAR(255) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_processing_attempts_sale_id ON processing_attempts (sale_id);
