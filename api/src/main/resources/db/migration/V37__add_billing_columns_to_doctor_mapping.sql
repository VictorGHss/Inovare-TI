-- Migração para adicionar colunas de faturamento (Monetização) à tabela de mapeamento de médicos
ALTER TABLE appointment_doctor_mapping
    ADD COLUMN is_active boolean NOT NULL DEFAULT true,
    ADD COLUMN subscription_end_date timestamp;

-- Comentário explicativo sobre as novas colunas
COMMENT ON COLUMN appointment_doctor_mapping.is_active IS 'Indica se o médico está ativo e adimplente no sistema';
COMMENT ON COLUMN appointment_doctor_mapping.subscription_end_date IS 'Data de encerramento da assinatura contratada pelo médico';
