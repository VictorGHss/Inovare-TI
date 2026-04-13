# Changelog

## 2026-04-13

### Módulo de Confirmação Automática
- Criado o motor de confirmação de consultas com máquina de estados (PENDING, NUDGE_1_SENT, NUDGE_FINAL_SENT, CONFIRMED, CANCELED_NO_RESPONSE).
- Implementada migração Flyway V4 com tabelas de configuração, sessões, log de variáveis e mapeamento de médicos.
- Adicionadas integrações de cliente para Feegow e Blip, incluindo rate limit de 200ms para envio de mensagens.
- Criados schedulers de ingestão D+1 e monitoramento de nudge com cadência de 30 minutos.
- Disponibilizado endpoint de dicionário de variáveis em /v1/appointments/config/dictionary.
- Disponibilizado webhook /v1/webhook/blip com idempotência baseada em Redis.
