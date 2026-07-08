-- =============================================================================
-- Migração V40: Criar tabela access_credentials para persistir credenciais de acesso
-- =============================================================================
-- Contexto: Armazena as credenciais de acesso retornadas pela leitora e integradas
-- com as catracas locais para liberação física de pacientes e acompanhantes.
-- =============================================================================

CREATE TABLE IF NOT EXISTS access_credentials (
    id                      UUID            NOT NULL,
    appointment_id          VARCHAR(255)    NOT NULL,
    name                    VARCHAR(255)    NOT NULL,
    cpf                     VARCHAR(255),
    user_type               VARCHAR(50)     NOT NULL,
    access_credential       VARCHAR(255)    NOT NULL,
    locator                 VARCHAR(255)    NOT NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_access_credentials PRIMARY KEY (id)
);

-- Índices recomendados para otimização das buscas e cruzamentos no controle de acesso
CREATE INDEX IF NOT EXISTS idx_access_credentials_appointment_id
    ON access_credentials(appointment_id);

CREATE INDEX IF NOT EXISTS idx_access_credentials_cpf
    ON access_credentials(cpf);

