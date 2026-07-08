-- =============================================================================
-- Migração V41: Adiciona constraint de unicidade composta para idempotência de inserções
-- =============================================================================
-- Contexto: Pacientes frequentemente clicam duas vezes nos botões de confirmação do
-- WhatsApp, o que pode disparar webhooks paralelos idênticos. Esta constraint garante
-- que apenas uma credencial por (appointment_id, name) seja persistida no banco.
--
-- Regra de Negócio:
--   - Um mesmo agendamento PODE ter múltiplos acompanhantes (COMPANION) com nomes
--     diferentes. Ex: "Maria" e "João" podem ser cadastrados no mesmo agendamento.
--   - O que não pode ocorrer é o mesmo nome sendo inserido duas vezes no mesmo
--     agendamento — situação causada por duplo clique no WhatsApp.
--
-- Por que NÃO usar (appointment_id, user_type)?
--   Essa combinação limitaria o agendamento a apenas 1 acompanhante. Se um paciente
--   cadastrasse 2 acompanhantes, o segundo seria descartado silenciosamente pelo
--   try-catch de idempotência — erro grave de regra de negócio.
--
-- Por que (appointment_id, name)?
--   Permite múltiplos acompanhantes diferentes (nomes distintos), mas bloqueia a
--   inserção duplicada do mesmo nome gerada por retentativas paralelas do webhook.
-- =============================================================================

ALTER TABLE access_credentials
    ADD CONSTRAINT uq_access_credentials_appointment_name
    UNIQUE (appointment_id, name);
