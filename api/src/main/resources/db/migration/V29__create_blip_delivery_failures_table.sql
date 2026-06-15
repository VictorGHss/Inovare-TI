-- =============================================================================
-- Migração V29: Criar tabela blip_delivery_failures para rastreamento de erros de entrega do Blip
-- =============================================================================
-- Contexto: Criação da tabela para armazenar as falhas de entrega notificadas pela Blip
-- (status receipts com event = 'failed'). A tabela permite auditar e expor métricas
-- detalhadas sobre erros de envio de templates WhatsApp (como saldo insuficiente,
-- número inválido, expiração de janela, etc.), mantendo correlação direta com
-- as sessões de agendamento e logs de execução (via trace_id).
-- =============================================================================

CREATE TABLE IF NOT EXISTS blip_delivery_failures (
    id              uuid          NOT NULL DEFAULT gen_random_uuid(),
    message_id      varchar(128)  NOT NULL,
    appointment_id  varchar(64),
    error_code      integer       NOT NULL,
    error_message   text,
    trace_id        varchar(64),
    created_at      timestamp     NOT NULL DEFAULT now(),
    CONSTRAINT pk_blip_delivery_failures PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_blip_delivery_failures_message_id
    ON blip_delivery_failures(message_id);

CREATE INDEX IF NOT EXISTS idx_blip_delivery_failures_appointment_id
    ON blip_delivery_failures(appointment_id);
