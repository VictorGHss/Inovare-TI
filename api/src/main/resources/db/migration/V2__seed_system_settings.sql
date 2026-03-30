-- =============================================================================
-- V2__seed_system_settings.sql
-- Insere valores iniciais para `system_settings` (SLAs e limites)
-- Não sobrescreve valores existentes (ON CONFLICT DO NOTHING)
-- =============================================================================

INSERT INTO system_settings (id, value, description) VALUES
  ('SLA_URGENT_HOURS', '4', 'Horas para atendimento urgente (SLA)'),
  ('SLA_HIGH_HOURS', '24', 'Horas para atendimento alto (SLA)'),
  ('SLA_NORMAL_HOURS', '72', 'Horas para atendimento normal (SLA)'),
  ('ATTACHMENT_SIZE_LIMIT_MB', '5', 'Limite de tamanho de anexos em MB'),
  ('REPORT_DEFAULT_DAY', '12', 'Dia padrão do mês para relatórios agendados')
ON CONFLICT (id) DO NOTHING;
