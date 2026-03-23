-- V4__Alter_processing_attempts_id_to_uuid.sql
-- Corrige a coluna `id` da tabela processing_attempts para UUID (gen_random_uuid)
-- Estratégia: adiciona coluna temporária uuid, popula com gen_random_uuid(), substitui PK, remove coluna antiga

BEGIN;

-- Garante extensão para gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Remove PK existente se houver
ALTER TABLE IF EXISTS processing_attempts DROP CONSTRAINT IF EXISTS processing_attempts_pkey;

-- Adiciona coluna temporária
ALTER TABLE processing_attempts ADD COLUMN IF NOT EXISTS id_new uuid DEFAULT gen_random_uuid();

-- Popula id_new para registros existentes
UPDATE processing_attempts SET id_new = gen_random_uuid() WHERE id_new IS NULL;

-- Remove a coluna antiga (bigserial)
ALTER TABLE processing_attempts DROP COLUMN IF EXISTS id;

-- Renomeia a coluna nova para id
ALTER TABLE processing_attempts RENAME COLUMN id_new TO id;

-- Define PK na nova coluna id
ALTER TABLE processing_attempts ADD CONSTRAINT processing_attempts_pkey PRIMARY KEY (id);

-- Garante tipos/constraints esperados para outras colunas
ALTER TABLE processing_attempts ALTER COLUMN sale_id TYPE varchar(120);
ALTER TABLE processing_attempts ALTER COLUMN sale_id SET NOT NULL;
ALTER TABLE processing_attempts ALTER COLUMN attempts SET NOT NULL;
ALTER TABLE processing_attempts ALTER COLUMN last_attempt_at SET NOT NULL;

-- Garante índice único em sale_id
CREATE UNIQUE INDEX IF NOT EXISTS idx_processing_attempts_sale_id ON processing_attempts (sale_id);

COMMIT;
