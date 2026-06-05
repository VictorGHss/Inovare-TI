-- Migration V22: Adicionar coluna phone_number na tabela notification_groups
ALTER TABLE notification_groups ADD COLUMN IF NOT EXISTS phone_number VARCHAR(30);
