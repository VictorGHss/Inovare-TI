-- =============================================================================
-- V3__add_2fa_recovery_code.sql
-- Adiciona colunas para suporte ao fluxo de recuperação do 2FA
-- recovery_code_hash: hash BCrypt do código temporário enviado ao Discord
-- recovery_code_expires_at: expiração do código (15 minutos após a solicitação)
-- =============================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS recovery_code_hash      varchar(255),
    ADD COLUMN IF NOT EXISTS recovery_code_expires_at timestamp;
