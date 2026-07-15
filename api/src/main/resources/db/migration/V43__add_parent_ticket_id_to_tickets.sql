-- Migration to add parent_ticket_id to tickets table for ticket hierarchy support
ALTER TABLE tickets ADD COLUMN parent_ticket_id uuid;

ALTER TABLE tickets ADD CONSTRAINT fk_tickets_parent_ticket 
    FOREIGN KEY (parent_ticket_id) REFERENCES tickets(id) ON DELETE SET NULL;

CREATE INDEX idx_tickets_parent_ticket_id ON tickets(parent_ticket_id);
