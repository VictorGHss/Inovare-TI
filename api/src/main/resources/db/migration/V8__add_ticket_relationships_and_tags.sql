-- Criação da tabela de relacionamento entre chamados (autorreferencial N:N)
CREATE TABLE ticket_relations (
    ticket_id UUID NOT NULL,
    related_ticket_id UUID NOT NULL,
    CONSTRAINT pk_ticket_relations PRIMARY KEY (ticket_id, related_ticket_id),
    CONSTRAINT fk_ticket_relations_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_relations_related FOREIGN KEY (related_ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);

-- Criação da tabela para persistência de tags corporativas associadas aos chamados
CREATE TABLE ticket_tags (
    ticket_id UUID NOT NULL,
    tag VARCHAR(50) NOT NULL,
    CONSTRAINT pk_ticket_tags PRIMARY KEY (ticket_id, tag),
    CONSTRAINT fk_ticket_tags_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);
