-- =============================================================================
-- Migração V41: Adiciona constraint de unicidade composta para idempotência de inserções
-- =============================================================================
-- Contexto: Pacientes frequentemente clicam duas vezes nos botões de confirmação do
-- WhatsApp, o que pode disparar webhooks paralelos idênticos. Esta constraint garante
-- que apenas uma credencial por (appointment_id, user_type) seja persistida no banco,
-- evitando duplicações silenciosas e mantendo o fluxo do Blip estável mesmo sob
-- condições de duplo clique ou retentativas.
--
-- A escolha de (appointment_id, user_type) em vez de (appointment_id, cpf) garante
-- que acompanhantes sem CPF também sejam protegidos pela constraint composta.
-- =============================================================================

ALTER TABLE access_credentials
    ADD CONSTRAINT uq_access_credentials_appointment_user_type
    UNIQUE (appointment_id, user_type);
