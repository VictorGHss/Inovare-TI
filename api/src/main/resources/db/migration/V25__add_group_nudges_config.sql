-- V25: Suporte ao motor de nudges em grupo (GROUP_NUDGE_1 e GROUP_NUDGE_FINAL)

-- 1. Expandir a constraint de categoria para incluir as categorias de nudge do grupo
ALTER TABLE appointment_configs
    DROP CONSTRAINT IF EXISTS ck_appointment_configs_category;

ALTER TABLE appointment_configs
    ADD CONSTRAINT ck_appointment_configs_category
    CHECK (category IN ('CONFIRMATION', 'NUDGE_1', 'NUDGE_FINAL', 'GROUP_NOTIFICATION', 'GROUP_NUDGE_1', 'GROUP_NUDGE_FINAL'));

-- 2. Inserir as configurações padrão de Nudge de Grupo
--    timing_hours = 4  -->  Nudge 1 de grupo dispara 4 horas após a ingestão
--    timing_hours = 24 -->  Nudge Final de grupo dispara 24 horas após o Nudge 1
INSERT INTO appointment_configs (category, template_id, timing_hours)
VALUES 
    ('GROUP_NUDGE_1', 'aviso_confirmacao_pendente_grupo', 4),
    ('GROUP_NUDGE_FINAL', 'aviso_confirmacao_pendente_grupo', 24)
ON CONFLICT (category) DO UPDATE
    SET template_id  = EXCLUDED.template_id,
        timing_hours = EXCLUDED.timing_hours,
        updated_at   = now();
