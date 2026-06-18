-- Migração para adicionar colunas de faturamento (Monetização) à tabela de mapeamento de médicos
ALTER TABLE appointment_doctor_mapping
    ADD COLUMN is_active boolean NOT NULL DEFAULT true,
    ADD COLUMN subscription_end_date timestamp;

-- Comentário explicativo sobre as novas colunas
COMMENT ON COLUMN appointment_doctor_mapping.is_active IS 'Indica se o médico está ativo e adimplente no sistema';
COMMENT ON COLUMN appointment_doctor_mapping.subscription_end_date IS 'Data de encerramento da assinatura contratada pelo médico';

-- Atualização da Check Constraint de status de sessões para suportar o status finalizador CANCELED
ALTER TABLE appointment_sessions DROP CONSTRAINT IF EXISTS ck_appointment_sessions_status;
ALTER TABLE appointment_sessions ADD CONSTRAINT ck_appointment_sessions_status CHECK (status IN ('PENDING', 'NUDGE_1_SENT', 'NUDGE_FINAL_SENT', 'CONFIRMED', 'CANCELED_NO_RESPONSE', 'CANCELED'));

