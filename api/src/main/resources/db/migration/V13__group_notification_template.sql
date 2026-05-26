-- V13: Suporte ao template de notificação de grupo (aviso_agendamento_grupo)

-- 1. Expandir o CHECK constraint de appointment_configs para aceitar GROUP_NOTIFICATION
ALTER TABLE appointment_configs
    DROP CONSTRAINT IF EXISTS ck_appointment_configs_category;

ALTER TABLE appointment_configs
    ADD CONSTRAINT ck_appointment_configs_category
    CHECK (category IN ('CONFIRMATION', 'NUDGE_1', 'NUDGE_FINAL', 'GROUP_NOTIFICATION'));

-- 2. Inserir (ou atualizar, caso já exista) a configuração do template de grupo
--    timing_hours = 0  -->  disparo imediato pelo motor de ingestão, não por scheduler baseado em horas
INSERT INTO appointment_configs (category, template_id, timing_hours)
VALUES ('GROUP_NOTIFICATION', 'aviso_agendamento_grupo', 0)
ON CONFLICT (category) DO UPDATE
    SET template_id  = EXCLUDED.template_id,
        timing_hours = EXCLUDED.timing_hours,
        updated_at   = now();
