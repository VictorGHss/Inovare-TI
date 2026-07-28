-- =============================================================================
-- V46__add_is_active_to_doctor_configurations.sql
-- Adds is_active column to doctor_configurations table.
-- =============================================================================

ALTER TABLE doctor_configurations
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;

COMMENT ON COLUMN doctor_configurations.is_active IS 'Indica se a configuração do médico está ativa para busca e envio no motor de agendamentos.';
