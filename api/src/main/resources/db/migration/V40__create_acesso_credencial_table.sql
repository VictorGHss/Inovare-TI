-- =============================================================================
-- Migração V40: Criar tabela acesso_credencial para persistir credenciais de acesso
-- =============================================================================
-- Contexto: Armazena as credenciais de acesso retornadas pela leitora e integradas
-- com as catracas locais para liberação física de pacientes e acompanhantes.
-- =============================================================================

CREATE TABLE IF NOT EXISTS acesso_credencial (
    id                      UUID            NOT NULL,
    id_agendamento          VARCHAR(255)    NOT NULL,
    nome                    VARCHAR(255)    NOT NULL,
    cpf                     VARCHAR(255),
    tipo_usuario            VARCHAR(50)     NOT NULL,
    credencial_ger_acesso   VARCHAR(255)    NOT NULL,
    localizador             VARCHAR(255)    NOT NULL,
    data_criacao            TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_acesso_credencial PRIMARY KEY (id)
);

-- Índices recomendados para otimização das buscas e cruzamentos no controle de acesso
CREATE INDEX IF NOT EXISTS idx_acesso_credencial_id_agendamento
    ON acesso_credencial(id_agendamento);

CREATE INDEX IF NOT EXISTS idx_acesso_credencial_cpf
    ON acesso_credencial(cpf);
