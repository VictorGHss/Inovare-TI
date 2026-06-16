-- V35__remove_external_links_from_doctor_mapping.sql
-- Remoção de colunas obsoletas de links externos para blindagem do canal de atendimento unificado

ALTER TABLE appointment_doctor_mapping
    DROP COLUMN IF EXISTS is_external,
    DROP COLUMN IF EXISTS external_wa_link;
