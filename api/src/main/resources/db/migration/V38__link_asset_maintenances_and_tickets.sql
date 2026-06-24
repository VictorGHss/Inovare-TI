-- V38__link_asset_maintenances_and_tickets.sql
-- Adiciona vínculo bidirecional entre Ativos (CMDB) e Chamados (ITSM)

ALTER TABLE asset_maintenances ADD COLUMN ticket_id UUID;

ALTER TABLE asset_maintenances 
    ADD CONSTRAINT fk_asset_maintenances_ticket 
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) 
    ON DELETE SET NULL;

CREATE INDEX idx_asset_maintenances_ticket ON asset_maintenances(ticket_id);
