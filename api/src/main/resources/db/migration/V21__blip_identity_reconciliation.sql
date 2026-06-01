-- Migração V21: Suporte a WhatsApp Usernames e Reconciliação de BSUIDs (Meta/Blip)

-- 1) Criar tabela de reconciliação de identidades do Blip
CREATE TABLE blip_user_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blip_guid VARCHAR(255) NOT NULL UNIQUE,
    bsuid VARCHAR(255),
    phone_number VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Criar índices de busca rápida na tabela de reconciliação
CREATE INDEX idx_blip_user_identities_guid ON blip_user_identities(blip_guid);
CREATE INDEX idx_blip_user_identities_bsuid ON blip_user_identities(bsuid);
CREATE INDEX idx_blip_user_identities_phone ON blip_user_identities(phone_number);

-- 2) Adicionar colunas de suporte blip_guid e bsuid na tabela appointment_sessions
ALTER TABLE appointment_sessions ADD COLUMN blip_guid VARCHAR(255);
ALTER TABLE appointment_sessions ADD COLUMN bsuid VARCHAR(255);

-- Criar índices correspondentes na tabela de sessões
CREATE INDEX idx_appointment_sessions_blip_guid ON appointment_sessions(blip_guid);
CREATE INDEX idx_appointment_sessions_bsuid ON appointment_sessions(bsuid);
