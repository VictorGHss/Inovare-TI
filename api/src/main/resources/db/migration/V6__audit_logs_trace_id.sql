-- Adiciona trace_id para rastreamento de auditoria.
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS trace_id varchar(64);
