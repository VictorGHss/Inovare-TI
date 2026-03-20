-- =============================================================================
-- V2__insert_dev_data.sql - Infraestrutura mínima de dados
-- Escopo: apenas categorias e setores (sem usuários/admin/teste)
-- =============================================================================

-- 1. TICKET CATEGORIES
INSERT INTO ticket_categories (id, name, base_sla_hours) VALUES
    ('22222222-2222-2222-2222-222222222001', 'Hardware', 48),
    ('22222222-2222-2222-2222-222222222002', 'Software', 24),
    ('22222222-2222-2222-2222-222222222003', 'Rede', 12),
    ('22222222-2222-2222-2222-222222222004', 'Acessos', 24)
ON CONFLICT (id) DO NOTHING;

-- 2. ITEM CATEGORIES
INSERT INTO item_categories (id, name, is_consumable) VALUES
    ('33333333-3333-3333-3333-333333333001', 'Computadores e Desktops', false),
    ('33333333-3333-3333-3333-333333333002', 'Perifericos', false),
    ('33333333-3333-3333-3333-333333333003', 'Suprimentos de Impressao', true),
    ('33333333-3333-3333-3333-333333333004', 'Cabos e Conectores', true)
ON CONFLICT (id) DO NOTHING;

-- 3. SECTORS
INSERT INTO sectors (id, name) VALUES
    ('11111111-1111-1111-1111-111111111001', 'TI'),
    ('11111111-1111-1111-1111-111111111002', 'Financeiro'),
    ('11111111-1111-1111-1111-111111111003', 'Recursos Humanos'),
    ('11111111-1111-1111-1111-111111111004', 'Operacoes')
ON CONFLICT (id) DO NOTHING;

-- 4. ASSET CATEGORIES
INSERT INTO asset_categories (id, name) VALUES
    ('44444444-4444-4444-4444-444444444001', 'Laptops'),
    ('44444444-4444-4444-4444-444444444002', 'Desktops'),
    ('44444444-4444-4444-4444-444444444003', 'Acessorios')
ON CONFLICT (id) DO NOTHING;
