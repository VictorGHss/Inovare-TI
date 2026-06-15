-- V33__add_asset_to_item_allocations.sql
-- Módulo de Relacionamentos Avançados de Inventário — Integração de Alocações com Ativos Físicos (CMDB)
-- Permite que insumos sejam alocados nominalmente a um equipamento físico da tabela assets.

-- 1. Remove a restrição NOT NULL da coluna parent_item_id para permitir alocações exclusivas de ativos
ALTER TABLE item_allocations ALTER COLUMN parent_item_id DROP NOT NULL;

-- 2. Adiciona a nova coluna asset_id (UUID, nulo autorizado)
ALTER TABLE item_allocations ADD COLUMN asset_id UUID;

-- 3. Adiciona a constraint de chave estrangeira com a tabela assets configurando ON DELETE SET NULL
ALTER TABLE item_allocations
    ADD CONSTRAINT fk_item_allocations_asset
    FOREIGN KEY (asset_id)
    REFERENCES assets(id)
    ON DELETE SET NULL;

-- 4. Adiciona restrição de integridade para garantir que a alocação tenha ao menos um destino válido
ALTER TABLE item_allocations
    ADD CONSTRAINT ck_item_allocations_destination_present
    CHECK (parent_item_id IS NOT NULL OR asset_id IS NOT NULL);

-- 5. Criação de índice na coluna asset_id para otimização de joins e filtros patrimoniais
CREATE INDEX idx_item_allocations_asset ON item_allocations(asset_id);
