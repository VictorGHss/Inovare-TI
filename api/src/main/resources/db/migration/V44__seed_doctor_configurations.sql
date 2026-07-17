-- =============================================================================
-- V44__create_doctor_configurations_table.sql
-- Creates the doctor_configurations table to store dynamic metadata for
-- each doctor: GerAcesso turnstile credentials and Blip attendance queue IDs.
--
-- Data is NOT seeded here. Use a local DML script outside of version control
-- to populate this table with sensitive credentials.
-- =============================================================================

CREATE TABLE IF NOT EXISTS doctor_configurations (
    feegow_profissional_id  BIGINT      NOT NULL,
    doctor_name             VARCHAR(255) NOT NULL,
    ger_acesso_matricula    VARCHAR(120),
    ger_acesso_cpf          VARCHAR(20),
    blip_queue_id           VARCHAR(120),
    blip_queue_name         VARCHAR(255),
    CONSTRAINT pk_doctor_configurations PRIMARY KEY (feegow_profissional_id)
);

COMMENT ON TABLE  doctor_configurations                        IS 'Configurações dinâmicas de médicos: credenciais de catraca e filas do Blip.';
COMMENT ON COLUMN doctor_configurations.feegow_profissional_id IS 'ID numérico do profissional no Feegow (chave primária).';
COMMENT ON COLUMN doctor_configurations.doctor_name            IS 'Nome descritivo do médico para auditoria e exibição no painel.';
COMMENT ON COLUMN doctor_configurations.ger_acesso_matricula   IS 'Matrícula utilizada nas catracas físicas do GerAcesso.';
COMMENT ON COLUMN doctor_configurations.ger_acesso_cpf         IS 'CPF limpo (somente dígitos) do médico para autenticação no GerAcesso.';
COMMENT ON COLUMN doctor_configurations.blip_queue_id          IS 'UUID da fila de atendimento humano no Blip para transbordo pós-confirmação.';
COMMENT ON COLUMN doctor_configurations.blip_queue_name        IS 'Nome legível da fila Blip para exibição e logs.';
