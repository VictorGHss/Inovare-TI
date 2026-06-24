-- Migração Flyway: V39__tune_audit_logs_autovacuum.sql
-- Sintoniza parâmetros de autovacuum para a tabela audit_logs, que sofre alta escrita e deleção massiva.

ALTER TABLE audit_logs SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_vacuum_threshold = 1000
);
