-- 1. Mapeamento Dinâmico de Templates (Original V5)
CREATE TABLE IF NOT EXISTS appointment_template_mapping (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    template_name varchar(120) NOT NULL,
    placeholder_index integer NOT NULL,
    feegow_field_name varchar(120) NOT NULL, -- Aqui aceitaremos 'profissionalNome' para usar o dado local
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_appointment_template_mapping PRIMARY KEY (id),
    CONSTRAINT uq_appointment_template_mapping_template_placeholder UNIQUE (template_name, placeholder_index)
);

CREATE INDEX IF NOT EXISTS idx_appointment_template_mapping_template_name 
    ON appointment_template_mapping(template_name);

-- 2. Detalhes de Status na Sessão (Original V6)
ALTER TABLE appointment_sessions 
    ADD COLUMN IF NOT EXISTS status_details varchar(500);

-- 3. Consolidação Total no Mapeamento de Médicos (Original V9 e V10)
-- Adicionando todos os campos de roteamento e o nome personalizado
ALTER TABLE appointment_doctor_mapping
    ADD COLUMN IF NOT EXISTS itsm_user_id varchar(120),
    ADD COLUMN IF NOT EXISTS discord_webhook_url varchar(500),
    ADD COLUMN IF NOT EXISTS external_wa_link varchar(500),
    ADD COLUMN IF NOT EXISTS profissional_nome varchar(255);

-- 4. Limpeza de Legado
-- Como o banco da dev foi dropado, não precisamos da tabela temporária doctor_queue_mapping (V7)
-- pois os campos acima já resolvem a estrutura final.
DROP TABLE IF EXISTS doctor_queue_mapping;

