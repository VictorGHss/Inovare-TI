-- V32__add_inventory_relationships.sql
-- Módulo de Relacionamentos Avançados de Inventário
-- Criação de vínculos hierárquicos entre ativos e controle de alocação de periféricos/consumíveis

-- 1. Comando A: Adição da coluna autorreferencial de ativo pai na tabela items
-- Adiciona o campo parent_item_id para permitir a composição hierárquica (ex.: PC + Monitor).
-- Configura ON DELETE SET NULL para que, caso o ativo pai seja excluído, o filho continue existindo de forma avulsa.
ALTER TABLE items
    ADD COLUMN parent_item_id UUID;

ALTER TABLE items
    ADD CONSTRAINT fk_items_parent_item
    FOREIGN KEY (parent_item_id)
    REFERENCES items(id)
    ON DELETE SET NULL;

-- Índice para acelerar a busca de componentes acoplados a um ativo pai
CREATE INDEX idx_items_parent_item_id ON items(parent_item_id);

-- 2. Comando B: Criação da tabela de alocações (item_allocations)
-- Esta tabela mapeia o consumo e a vinculação de periféricos e consumíveis a ativos principais.
-- - parent_item_id: Ativo principal (ex.: Impressora) que recebe o periférico/consumível.
-- - child_item_id: Periférico ou consumível (ex.: Toner) alocado.
-- - allocated_by_id: Técnico/Usuário que registrou a alocação.
-- - ticket_id: Chamado de TI associado, se houver.
CREATE TABLE item_allocations (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    parent_item_id    UUID         NOT NULL,
    child_item_id     UUID         NOT NULL,
    quantity          INTEGER      NOT NULL,
    allocated_at      TIMESTAMP    NOT NULL,
    allocated_by_id   UUID         NOT NULL,
    ticket_id         UUID,
    CONSTRAINT pk_item_allocations PRIMARY KEY (id),
    CONSTRAINT ck_item_allocations_quantity CHECK (quantity > 0),
    CONSTRAINT fk_item_allocations_parent FOREIGN KEY (parent_item_id) REFERENCES items(id) ON DELETE CASCADE,
    CONSTRAINT fk_item_allocations_child FOREIGN KEY (child_item_id) REFERENCES items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_item_allocations_allocated_by FOREIGN KEY (allocated_by_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_item_allocations_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE SET NULL
);

-- Índices nas chaves estrangeiras da tabela de alocações para otimizar buscas e joins
CREATE INDEX idx_item_allocations_parent ON item_allocations(parent_item_id);
CREATE INDEX idx_item_allocations_child ON item_allocations(child_item_id);
CREATE INDEX idx_item_allocations_allocated_by ON item_allocations(allocated_by_id);
CREATE INDEX idx_item_allocations_ticket ON item_allocations(ticket_id);
