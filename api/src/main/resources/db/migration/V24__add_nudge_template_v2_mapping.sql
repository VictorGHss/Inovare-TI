-- V24: Mapeamento de variáveis e configuração do novo template de nudge aviso_confirmacao_pendente_v2

-- 1. Inserir o mapeamento de variáveis para o novo template 'aviso_confirmacao_pendente_v2'
-- Placeholder {1} (index 0) mapeado para o nome do paciente (patient_name)
INSERT INTO appointment_template_mapping (template_name, placeholder_index, feegow_field_name)
VALUES ('aviso_confirmacao_pendente_v2', 0, 'patient_name')
ON CONFLICT (template_name, placeholder_index) DO UPDATE
    SET feegow_field_name = EXCLUDED.feegow_field_name,
        updated_at = now();

-- 2. Atualizar a tabela de configurações para usar o novo template aviso_confirmacao_pendente_v2
-- nas categorias de nudges
UPDATE appointment_configs
SET template_id = 'aviso_confirmacao_pendente_v2',
    updated_at = now()
WHERE category IN ('NUDGE_1', 'NUDGE_FINAL');
