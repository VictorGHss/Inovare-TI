-- Migration to create ticket_assignments joint table for ticket co-involvement
CREATE TABLE ticket_assignments (
    ticket_id uuid NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT pk_ticket_assignments PRIMARY KEY (ticket_id, user_id),
    CONSTRAINT fk_ticket_assignments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_assignments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
