-- Migração Flyway: V30__add_ticket_status_created_at_composite_index.sql
-- Objetivo: Criar um índice composto para acelerar as consultas principais de chamados de TI (tickets)
-- filtrados por 'status' e ordenados por 'created_at' de forma decrescente para a paginação.

CREATE INDEX idx_tickets_status_created_at ON tickets (status, created_at DESC);
