-- Criação da tabela para suporte a solicitações de múltiplos itens de inventário num chamado (atendimento atómico)
CREATE TABLE ticket_item_requests (
    id          uuid    NOT NULL DEFAULT gen_random_uuid(),
    ticket_id   uuid    NOT NULL,
    item_id     uuid    NOT NULL,
    quantity    integer NOT NULL,
    CONSTRAINT pk_ticket_item_requests PRIMARY KEY (id),
    CONSTRAINT fk_ticket_item_requests_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_item_requests_item FOREIGN KEY (item_id) REFERENCES items(id)
);

-- Indexação para melhorar a performance das consultas de relacionamento por chamado
CREATE INDEX idx_ticket_item_requests_ticket ON ticket_item_requests(ticket_id);
