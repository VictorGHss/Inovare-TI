-- =============================================================================
-- V45__add_time_shift_and_advance_notice_to_doctor_configurations.sql
-- Adds configurable columns for time shift offset and advance notice days per doctor.
-- =============================================================================

ALTER TABLE doctor_configurations
    ADD COLUMN IF NOT EXISTS display_time_offset_minutes INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS advance_notice_days INT DEFAULT 1;

COMMENT ON COLUMN doctor_configurations.display_time_offset_minutes IS 'Deslocamento em minutos para o horário exibido na mensagem do paciente (ex: -10 para 10 min antes).';
COMMENT ON COLUMN doctor_configurations.advance_notice_days IS 'Quantidade de dias de antecedência para busca e envio da mensagem de confirmação (ex: 2 para enviar nas quartas os procedimentos de sexta).';

-- Seed initial custom configurations for Dr. Eduardo Mattos (28) and Dr. Giuliano (27)
INSERT INTO doctor_configurations (feegow_profissional_id, doctor_name, display_time_offset_minutes, advance_notice_days)
VALUES 
    (28, 'Dr. Eduardo Mattos', -10, 1),
    (27, 'Dr. Giuliano', 0, 2)
ON CONFLICT (feegow_profissional_id) DO UPDATE 
SET 
    display_time_offset_minutes = EXCLUDED.display_time_offset_minutes,
    advance_notice_days = EXCLUDED.advance_notice_days;
