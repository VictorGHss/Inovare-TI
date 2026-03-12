-- =============================================================================
-- V2__insert_dev_data.sql - Dados de desenvolvimento do Inovare TI
-- Traducao fiel do DatabaseSeeder.java para SQL puro
-- ATENCAO: Execute apenas em ambiente de desenvolvimento (@Profile("dev"))
-- =============================================================================
-- Hashes gerados com BCryptPasswordEncoder (custo 10):
--   admin123  -> $2a$10$E4kc4J73WuOP7Vn7UO5DTuf3Rg7oxJAA9c5Dklp4vjyaUiPqyB./K
--   tech123   -> $2a$10$6nWPUSHrKJxoDX2p0CSjoOl8FO7ByzPN.hJZeTkdp2GqKDEPwU52u
--   user123   -> $2a$10$/18BvCN1toDObzdS8OfMZOhGYAgJCNK.WDdodyl4HvE51bOTogJzy
-- =============================================================================

-- =============================================================================
-- 1. TICKET CATEGORIES
-- =============================================================================
INSERT INTO ticket_categories (id, name, base_sla_hours) VALUES
    ('22222222-2222-2222-2222-222222222001', 'Hardware',  48),
    ('22222222-2222-2222-2222-222222222002', 'Software',  24),
    ('22222222-2222-2222-2222-222222222003', 'Rede',      12),
    ('22222222-2222-2222-2222-222222222004', 'Acessos',   24);

-- =============================================================================
-- 2. ITEM CATEGORIES
-- =============================================================================
INSERT INTO item_categories (id, name, is_consumable) VALUES
    ('33333333-3333-3333-3333-333333333001', 'Computadores e Desktops',  false),
    ('33333333-3333-3333-3333-333333333002', 'Perifericos',              false),
    ('33333333-3333-3333-3333-333333333003', 'Suprimentos de Impressao', true),
    ('33333333-3333-3333-3333-333333333004', 'Cabos e Conectores',       true);

-- =============================================================================
-- 3. SECTORS
-- =============================================================================
INSERT INTO sectors (id, name) VALUES
    ('11111111-1111-1111-1111-111111111001', 'TI'),
    ('11111111-1111-1111-1111-111111111002', 'Financeiro'),
    ('11111111-1111-1111-1111-111111111003', 'Recursos Humanos'),
    ('11111111-1111-1111-1111-111111111004', 'Operacoes');

-- =============================================================================
-- 4. ASSET CATEGORIES
-- =============================================================================
INSERT INTO asset_categories (id, name) VALUES
    ('44444444-4444-4444-4444-444444444001', 'Laptops'),
    ('44444444-4444-4444-4444-444444444002', 'Desktops'),
    ('44444444-4444-4444-4444-444444444003', 'Acessorios');

-- =============================================================================
-- 5. USERS (sector_id referenciam sectors acima)
-- senha admin    = admin123
-- senha tecnico  = tech123
-- senha usuarios = user123
-- =============================================================================
INSERT INTO users (id, name, email, password_hash, must_change_password, role, sector_id, location) VALUES
    (
        '55555555-5555-5555-5555-555555555001',
        'Administrador',
        'admin@inovare.med.br',
        '$2a$10$E4kc4J73WuOP7Vn7UO5DTuf3Rg7oxJAA9c5Dklp4vjyaUiPqyB./K',
        false,
        'ADMIN',
        '11111111-1111-1111-1111-111111111001',
        'Sede'
    ),
    (
        '55555555-5555-5555-5555-555555555002',
        'Tecnico Suporte',
        'tecnico@inovare.med.br',
        '$2a$10$6nWPUSHrKJxoDX2p0CSjoOl8FO7ByzPN.hJZeTkdp2GqKDEPwU52u',
        false,
        'TECHNICIAN',
        '11111111-1111-1111-1111-111111111001',
        'Sede'
    ),
    (
        '55555555-5555-5555-5555-555555555003',
        'Joao Silva',
        'joao.silva@inovare.med.br',
        '$2a$10$/18BvCN1toDObzdS8OfMZOhGYAgJCNK.WDdodyl4HvE51bOTogJzy',
        false,
        'USER',
        '11111111-1111-1111-1111-111111111002',
        'Andar 3'
    ),
    (
        '55555555-5555-5555-5555-555555555004',
        'Maria Santos',
        'maria.santos@inovare.med.br',
        '$2a$10$/18BvCN1toDObzdS8OfMZOhGYAgJCNK.WDdodyl4HvE51bOTogJzy',
        false,
        'USER',
        '11111111-1111-1111-1111-111111111003',
        'Andar 2'
    ),
    (
        '55555555-5555-5555-5555-555555555005',
        'Pedro Costa',
        'pedro.costa@inovare.med.br',
        '$2a$10$/18BvCN1toDObzdS8OfMZOhGYAgJCNK.WDdodyl4HvE51bOTogJzy',
        false,
        'USER',
        '11111111-1111-1111-1111-111111111004',
        'Galpao'
    );

-- =============================================================================
-- 6. ITEMS (item_category_id referenciam item_categories)
-- =============================================================================
INSERT INTO items (id, item_category_id, name, current_stock, specifications) VALUES
    ('66666666-6666-6666-6666-666666666001', '33333333-3333-3333-3333-333333333002', 'Mouse Wireless Logitech', 15, '{}'),
    ('66666666-6666-6666-6666-666666666002', '33333333-3333-3333-3333-333333333002', 'Teclado Mecanico RGB',    8,  '{}'),
    ('66666666-6666-6666-6666-666666666003', '33333333-3333-3333-3333-333333333002', 'Monitor Dell 24pol',      3,  '{}'),
    ('66666666-6666-6666-6666-666666666004', '33333333-3333-3333-3333-333333333003', 'Toner HP LaserJet',       12, '{}'),
    ('66666666-6666-6666-6666-666666666005', '33333333-3333-3333-3333-333333333003', 'Papel A4 (resma)',         25, '{}'),
    ('66666666-6666-6666-6666-666666666006', '33333333-3333-3333-3333-333333333004', 'Cabo HDMI 2m',            20, '{}'),
    ('66666666-6666-6666-6666-666666666007', '33333333-3333-3333-3333-333333333004', 'Cabo Ethernet Cat6',      50, '{}');

-- =============================================================================
-- 7. STOCK BATCHES (um lote por item, espelhando seedStockBatches)
-- =============================================================================
INSERT INTO stock_batches (id, item_id, original_quantity, remaining_quantity, unit_price, brand, supplier, purchase_reason, entry_date) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', '66666666-6666-6666-6666-666666666001', 15, 15, 149.90, 'Logitech',  'Kabum',           'Reposicao mensal',                   NOW() - INTERVAL '10 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02', '66666666-6666-6666-6666-666666666002',  8,  8, 349.90, 'Corsair',   'Amazon',          'Expansao de TI',                     NOW() - INTERVAL '12 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb03', '66666666-6666-6666-6666-666666666003',  3,  3, 1199.00,'Dell',      'Dell Store',      'Novo projeto',                       NOW() - INTERVAL '8 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb04', '66666666-6666-6666-6666-666666666004', 12, 12, 189.90, 'HP',        'Kalunga',         'Reposicao mensal',                   NOW() - INTERVAL '5 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb05', '66666666-6666-6666-6666-666666666005', 25, 25,  25.00, NULL,        'Kalunga',         'Reposicao mensal',                   NOW() - INTERVAL '7 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb06', '66666666-6666-6666-6666-666666666006', 20, 20,  24.90, 'Kingston',  'Mercado Livre',   'Manutencao preventiva',              NOW() - INTERVAL '14 days'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb07', '66666666-6666-6666-6666-666666666007', 50, 50,   8.50, NULL,        'Magazine Luiza',  'Substituicao de equipamento danificado', NOW() - INTERVAL '9 days');

-- =============================================================================
-- 8. TICKETS (25 chamados gerais + 3 de solicitacao de item)
-- Distribuicao fiel ao seeder: 3 solicitantes, 4 categorias, 3 status, 4 prioridades
-- =============================================================================
INSERT INTO tickets (id, title, description, status, priority, requester_id, assigned_to_id, category_id, sla_deadline, created_at, closed_at) VALUES
    -- OPEN tickets (sem tecnico atribuido)
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa001', 'Chamado #1 - Hardware (HIGH)',    'Computador nao liga apos queda de energia',     'OPEN', 'HIGH',   '55555555-5555-5555-5555-555555555003', NULL,                                     '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '25 days' + INTERVAL '48 hours', NOW() - INTERVAL '25 days', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa002', 'Chamado #2 - Acessos (LOW)',      'Necessito acesso ao sistema de RH',             'OPEN', 'LOW',    '55555555-5555-5555-5555-555555555004', NULL,                                     '22222222-2222-2222-2222-222222222004', NOW() - INTERVAL '22 days' + INTERVAL '24 hours', NOW() - INTERVAL '22 days', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa003', 'Chamado #3 - Software (NORMAL)', 'Excel nao abre arquivos .xlsx',                 'OPEN', 'NORMAL', '55555555-5555-5555-5555-555555555005', NULL,                                     '22222222-2222-2222-2222-222222222002', NOW() - INTERVAL '20 days' + INTERVAL '24 hours', NOW() - INTERVAL '20 days', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa004', 'Chamado #4 - Rede (URGENT)',      'Sem internet no setor de Operacoes',            'OPEN', 'URGENT', '55555555-5555-5555-5555-555555555005', NULL,                                     '22222222-2222-2222-2222-222222222003', NOW() - INTERVAL '3 days'  + INTERVAL '12 hours', NOW() - INTERVAL '3 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa005', 'Chamado #5 - Hardware (NORMAL)', 'Mouse com duplo clique',                        'OPEN', 'NORMAL', '55555555-5555-5555-5555-555555555003', NULL,                                     '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '18 days' + INTERVAL '48 hours', NOW() - INTERVAL '18 days', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa006', 'Chamado #6 - Acessos (HIGH)',     'Senha expirada, bloqueio de conta',             'OPEN', 'HIGH',   '55555555-5555-5555-5555-555555555004', NULL,                                     '22222222-2222-2222-2222-222222222004', NOW() - INTERVAL '5 days'  + INTERVAL '24 hours', NOW() - INTERVAL '5 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa007', 'Chamado #7 - Software (LOW)',     'Impressora aparece offline no sistema',         'OPEN', 'LOW',    '55555555-5555-5555-5555-555555555003', NULL,                                     '22222222-2222-2222-2222-222222222002', NOW() - INTERVAL '10 days' + INTERVAL '24 hours', NOW() - INTERVAL '10 days', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa008', 'Chamado #8 - Rede (NORMAL)',      'Wi-Fi instavel na sala de reunioes',            'OPEN', 'NORMAL', '55555555-5555-5555-5555-555555555005', NULL,                                     '22222222-2222-2222-2222-222222222003', NOW() - INTERVAL '7 days'  + INTERVAL '12 hours', NOW() - INTERVAL '7 days',  NULL),

    -- IN_PROGRESS tickets (tecnicos atribuidos)
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa009', 'Chamado #9 - Hardware (URGENT)',  'HD com erros de leitura, dados em risco',       'IN_PROGRESS', 'URGENT', '55555555-5555-5555-5555-555555555003', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '28 days' + INTERVAL '48 hours', NOW() - INTERVAL '28 days', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa010', 'Chamado #10 - Software (HIGH)',   'Antivirus com quarentena indevida de arquivos', 'IN_PROGRESS', 'HIGH',   '55555555-5555-5555-5555-555555555004', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222002', NOW() - INTERVAL '15 days' + INTERVAL '24 hours', NOW() - INTERVAL '15 days', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa011', 'Chamado #11 - Rede (NORMAL)',     'VPN nao conecta fora da rede corporativa',      'IN_PROGRESS', 'NORMAL', '55555555-5555-5555-5555-555555555005', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222003', NOW() - INTERVAL '12 days' + INTERVAL '12 hours', NOW() - INTERVAL '12 days', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa012', 'Chamado #12 - Acessos (HIGH)',    'Usuario sem perfil no ERP',                     'IN_PROGRESS', 'HIGH',   '55555555-5555-5555-5555-555555555003', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222004', NOW() - INTERVAL '9 days'  + INTERVAL '24 hours', NOW() - INTERVAL '9 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa013', 'Chamado #13 - Hardware (LOW)',    'Teclado com teclas travando',                   'IN_PROGRESS', 'LOW',    '55555555-5555-5555-5555-555555555004', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '6 days'  + INTERVAL '48 hours', NOW() - INTERVAL '6 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa014', 'Chamado #14 - Software (URGENT)', 'Sistema de ponto nao registra entrada',         'IN_PROGRESS', 'URGENT', '55555555-5555-5555-5555-555555555005', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222002', NOW() - INTERVAL '2 days'  + INTERVAL '24 hours', NOW() - INTERVAL '2 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa015', 'Chamado #15 - Rede (HIGH)',       'Switch do andar 3 sem energia',                 'IN_PROGRESS', 'HIGH',   '55555555-5555-5555-5555-555555555003', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222003', NOW() - INTERVAL '1 day'   + INTERVAL '12 hours', NOW() - INTERVAL '1 day',   NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa016', 'Chamado #16 - Acessos (NORMAL)', 'Permissao negada ao drive compartilhado',       'IN_PROGRESS', 'NORMAL', '55555555-5555-5555-5555-555555555004', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222004', NOW() - INTERVAL '4 days'  + INTERVAL '24 hours', NOW() - INTERVAL '4 days',  NULL),

    -- RESOLVED tickets (com closed_at)
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa017', 'Chamado #17 - Hardware (HIGH)',   'Monitor sem sinal apos reinicio',               'RESOLVED', 'HIGH',   '55555555-5555-5555-5555-555555555003', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '30 days' + INTERVAL '48 hours', NOW() - INTERVAL '30 days', NOW() - INTERVAL '29 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa018', 'Chamado #18 - Software (NORMAL)','Windows Update falhou com codigo 80070002',     'RESOLVED', 'NORMAL', '55555555-5555-5555-5555-555555555004', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222002', NOW() - INTERVAL '27 days' + INTERVAL '24 hours', NOW() - INTERVAL '27 days', NOW() - INTERVAL '26 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa019', 'Chamado #19 - Rede (URGENT)',     'Queda total de internet no escritorio',         'RESOLVED', 'URGENT', '55555555-5555-5555-5555-555555555005', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222003', NOW() - INTERVAL '24 days' + INTERVAL '12 hours', NOW() - INTERVAL '24 days', NOW() - INTERVAL '24 days' + INTERVAL '6 hours'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa020', 'Chamado #20 - Acessos (LOW)',     'Conta do Teams bloqueada por inatividade',      'RESOLVED', 'LOW',    '55555555-5555-5555-5555-555555555003', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222004', NOW() - INTERVAL '21 days' + INTERVAL '24 hours', NOW() - INTERVAL '21 days', NOW() - INTERVAL '20 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa021', 'Chamado #21 - Hardware (LOW)',    'Fonte do desktop com barulho',                  'RESOLVED', 'LOW',    '55555555-5555-5555-5555-555555555004', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '19 days' + INTERVAL '48 hours', NOW() - INTERVAL '19 days', NOW() - INTERVAL '17 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa022', 'Chamado #22 - Software (HIGH)',   'Feegow nao abre tela branca',                   'RESOLVED', 'HIGH',   '55555555-5555-5555-5555-555555555005', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222002', NOW() - INTERVAL '16 days' + INTERVAL '24 hours', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days' + INTERVAL '3 hours'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa023', 'Chamado #23 - Rede (NORMAL)',     'Velocidade de rede reduzida no financeiro',     'RESOLVED', 'NORMAL', '55555555-5555-5555-5555-555555555003', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222003', NOW() - INTERVAL '13 days' + INTERVAL '12 hours', NOW() - INTERVAL '13 days', NOW() - INTERVAL '12 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa024', 'Chamado #24 - Acessos (URGENT)', 'Diretoria sem acesso ao servidor de arquivos',  'RESOLVED', 'URGENT', '55555555-5555-5555-5555-555555555004', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222004', NOW() - INTERVAL '11 days' + INTERVAL '24 hours', NOW() - INTERVAL '11 days', NOW() - INTERVAL '10 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa025', 'Chamado #25 - Hardware (NORMAL)','Bateria do notebook nao carrega',               'RESOLVED', 'NORMAL', '55555555-5555-5555-5555-555555555005', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '8 days'  + INTERVAL '48 hours', NOW() - INTERVAL '8 days',  NOW() - INTERVAL '7 days');

-- Chamados de solicitacao de item (com requested_item_id e requested_quantity)
INSERT INTO tickets (id, title, description, status, priority, requester_id, assigned_to_id, category_id, requested_item_id, requested_quantity, sla_deadline, created_at, closed_at) VALUES
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa026',
        'Solicitacao de Item #26 - Mouse Wireless Logitech',
        'Solicitacao automatica de 3x Mouse Wireless Logitech',
        'RESOLVED', 'NORMAL',
        '55555555-5555-5555-5555-555555555003',
        '55555555-5555-5555-5555-555555555002',
        '22222222-2222-2222-2222-222222222001',
        '66666666-6666-6666-6666-666666666001',
        3,
        NOW() - INTERVAL '17 days' + INTERVAL '24 hours',
        NOW() - INTERVAL '17 days',
        NOW() - INTERVAL '16 days'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa027',
        'Solicitacao de Item #27 - Cabo HDMI 2m',
        'Solicitacao automatica de 5x Cabo HDMI 2m',
        'RESOLVED', 'NORMAL',
        '55555555-5555-5555-5555-555555555004',
        '55555555-5555-5555-5555-555555555002',
        '22222222-2222-2222-2222-222222222001',
        '66666666-6666-6666-6666-666666666006',
        5,
        NOW() - INTERVAL '14 days' + INTERVAL '24 hours',
        NOW() - INTERVAL '14 days',
        NOW() - INTERVAL '13 days'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa028',
        'Solicitacao de Item #28 - Toner HP LaserJet',
        'Solicitacao automatica de 2x Toner HP LaserJet',
        'RESOLVED', 'NORMAL',
        '55555555-5555-5555-5555-555555555005',
        '55555555-5555-5555-5555-555555555002',
        '22222222-2222-2222-2222-222222222001',
        '66666666-6666-6666-6666-666666666004',
        2,
        NOW() - INTERVAL '6 days' + INTERVAL '24 hours',
        NOW() - INTERVAL '6 days',
        NOW() - INTERVAL '5 days'
    );

-- =============================================================================
-- 9. ASSETS
-- =============================================================================
INSERT INTO assets (id, user_id, name, patrimony_code, category_id, specifications, created_at) VALUES
    (
        '77777777-7777-7777-7777-777777777001',
        '55555555-5555-5555-5555-555555555003',
        'Notebook Dell Latitude 5420',
        'INV-NTB-0001',
        '44444444-4444-4444-4444-444444444001',
        'CPU i5, 16GB RAM, SSD 512GB, Windows 11',
        NOW() - INTERVAL '6 months'
    ),
    (
        '77777777-7777-7777-7777-777777777002',
        '55555555-5555-5555-5555-555555555003',
        'Monitor LG 24pol',
        'INV-MON-0027',
        '44444444-4444-4444-4444-444444444003',
        '24 polegadas, Full HD, HDMI',
        NOW() - INTERVAL '2 days'
    ),
    (
        '77777777-7777-7777-7777-777777777003',
        '55555555-5555-5555-5555-555555555004',
        'Notebook Lenovo ThinkPad E14',
        'INV-NTB-0012',
        '44444444-4444-4444-4444-444444444001',
        'CPU i7, 16GB RAM, SSD 256GB, Windows 11',
        NOW() - INTERVAL '45 days'
    ),
    (
        '77777777-7777-7777-7777-777777777004',
        '55555555-5555-5555-5555-555555555005',
        'Desktop HP ProDesk 400',
        'INV-DESK-0008',
        '44444444-4444-4444-4444-444444444002',
        'CPU i5, 8GB RAM, SSD 256GB, Ethernet Cat6',
        NOW() - INTERVAL '12 days'
    );

-- =============================================================================
-- 10. ASSET MAINTENANCES
-- =============================================================================
INSERT INTO asset_maintenances (id, asset_id, maintenance_date, type, description, cost, technician_id, created_at) VALUES
    (
        '99999999-9999-9999-9999-999999999001',
        '77777777-7777-7777-7777-777777777001',
        (NOW() - INTERVAL '50 days')::date,
        'CORRECTIVE',
        'Substituicao de ventoinha por superaquecimento',
        320.00,
        '55555555-5555-5555-5555-555555555002',
        NOW() - INTERVAL '50 days'
    ),
    (
        '99999999-9999-9999-9999-999999999002',
        '77777777-7777-7777-7777-777777777001',
        (NOW() - INTERVAL '20 days')::date,
        'PREVENTIVE',
        'Limpeza interna e troca de pasta termica',
        180.00,
        '55555555-5555-5555-5555-555555555002',
        NOW() - INTERVAL '20 days'
    ),
    (
        '99999999-9999-9999-9999-999999999003',
        '77777777-7777-7777-7777-777777777001',
        (NOW() - INTERVAL '7 days')::date,
        'CORRECTIVE',
        'Troca do SSD por falha de leitura',
        450.00,
        '55555555-5555-5555-5555-555555555002',
        NOW() - INTERVAL '7 days'
    ),
    (
        '99999999-9999-9999-9999-999999999004',
        '77777777-7777-7777-7777-777777777004',
        (NOW() - INTERVAL '15 days')::date,
        'UPGRADE',
        'Upgrade de memoria RAM de 8GB para 16GB',
        260.00,
        '55555555-5555-5555-5555-555555555002',
        NOW() - INTERVAL '15 days'
    ),
    (
        '99999999-9999-9999-9999-999999999005',
        '77777777-7777-7777-7777-777777777002',
        (NOW() - INTERVAL '5 days')::date,
        'PREVENTIVE',
        'Revisao de cabos e ajuste de brilho',
        75.00,
        '55555555-5555-5555-5555-555555555002',
        NOW() - INTERVAL '5 days'
    );

-- =============================================================================
-- 11. SYSTEM SETTINGS
-- =============================================================================
INSERT INTO system_settings (id, value, description) VALUES
    ('SLA_URGENT_HOURS', '4', 'Tempo maximo em horas para chamados urgentes');

-- =============================================================================
-- 12. ARTICLES
-- =============================================================================
INSERT INTO articles (id, title, content, author_id, author_name, tags, created_at) VALUES
    (
        'cccccccc-cccc-cccc-cccc-cccccccccc01',
        'Como trocar o toner da impressora',
        '# Como trocar o toner da impressora

## Materiais necessarios
- Novo cartucho de toner
- Pano macio e seco

## Passo a passo

### 1. Desligar a impressora
Certifique-se de desligar completamente a impressora antes de comecar o procedimento.

### 2. Abrir o painel frontal
Localize o painel de acesso ao cartucho e puxe-o gentilmente em sua direcao.

### 3. Remover o cartucho gasto
Segure a aba de retirada do cartucho e puxe-o para fora com um movimento suave.

### 4. Instalar o novo toner
Retire o novo cartucho de sua embalagem e remova a fita protetora. Alinhe o cartucho com as guias e insira-o ate ouvir um clique.

### 5. Fechar o painel
Pressione o painel frontal ate que ele se encaixe no lugar.

### 6. Ligar a impressora
Ligue a impressora e realize uma impressao de teste para confirmar.',
        '55555555-5555-5555-5555-555555555001',
        'Administrador',
        'impressora, toner, tinta, manutencao',
        NOW() - INTERVAL '30 days'
    ),
    (
        'cccccccc-cccc-cccc-cccc-cccccccccc02',
        'Sistema Feegow nao abre (Tela Branca)',
        '# Sistema Feegow nao abre (Tela Branca)

## Problema comum
Ao acessar o Feegow, uma tela branca aparece sem carregar o sistema.

## Causas possiveis
- Cache do navegador corrompido
- Cookies expirados
- Historico de navegacao com dados obsoletos

## Solucao

### Passo 1: Limpar o Cache do Navegador
1. Abra o navegador (Chrome, Firefox, Safari, etc)
2. Pressione Ctrl + Shift + Delete (Windows) ou Cmd + Shift + Delete (Mac)
3. Selecione o periodo Todos os tempos
4. Marque as opcoes: Cookies e outros dados de sites / Arquivos em cache
5. Clique em Limpar dados

### Passo 2: Fechar e reabrir o navegador
Feche completamente o navegador e abra-o novamente.

### Passo 3: Acessar o Feegow
Acesse o portal do Feegow novamente em uma nova aba.

## Se o problema persistir
Abra um chamado tecnico informando: navegador e versao, sistema operacional e mensagens de erro.',
        '55555555-5555-5555-5555-555555555001',
        'Administrador',
        'feegow, sistema, erro, cache, navegador',
        NOW() - INTERVAL '25 days'
    ),
    (
        'cccccccc-cccc-cccc-cccc-cccccccccc03',
        'Configurar assinatura de E-mail no Outlook',
        '# Configurar assinatura de E-mail no Outlook

## Passo 1: Abrir as Configuracoes do Outlook
1. Abra o Outlook
2. Clique em Arquivo (canto superior esquerdo)
3. Selecione Opcoes

## Passo 2: Acessar a secao de Assinatura
1. Na janela de Opcoes, clique em Correio
2. Em seguida, clique em Assinaturas...

## Passo 3: Criar uma nova assinatura
1. Clique em Novo para criar uma assinatura
2. Digite um nome para a assinatura (ex: Corporativa)
3. Clique em OK

## Passo 4: Editar a assinatura
Na caixa de texto grande, digite sua assinatura com: nome, cargo, departamento, telefone, e-mail.

## Passo 5: Configurar uso automatico
No dropdown Escolher Assinatura Padrao, selecione sua assinatura e clique em OK para salvar.

## Pronto!
Sua assinatura sera adicionada automaticamente a todos os e-mails enviados.',
        '55555555-5555-5555-5555-555555555001',
        'Administrador',
        'email, outlook, assinatura, configuracao',
        NOW() - INTERVAL '20 days'
    );

-- =============================================================================
-- 13. CARGA EXTRA PARA TESTE DE ESTRESSE DO DASHBOARD (TOP 5)
-- Requisito: adicionar ao final da V2, sem criar nova migration.
-- =============================================================================

-- 4 usuarios adicionais (total geral passa para 9 usuarios)
INSERT INTO users (id, name, email, password_hash, must_change_password, role, sector_id, location) VALUES
    (
        '55555555-5555-5555-5555-555555555006',
        'Ana Paula Lima',
        'ana.lima@inovare.med.br',
        '$2a$10$/18BvCN1toDObzdS8OfMZOhGYAgJCNK.WDdodyl4HvE51bOTogJzy',
        false,
        'USER',
        '11111111-1111-1111-1111-111111111002',
        'Andar 4'
    ),
    (
        '55555555-5555-5555-5555-555555555007',
        'Carlos Eduardo Rocha',
        'carlos.rocha@inovare.med.br',
        '$2a$10$/18BvCN1toDObzdS8OfMZOhGYAgJCNK.WDdodyl4HvE51bOTogJzy',
        false,
        'USER',
        '11111111-1111-1111-1111-111111111003',
        'Andar 2'
    ),
    (
        '55555555-5555-5555-5555-555555555008',
        'Fernanda Alves',
        'fernanda.alves@inovare.med.br',
        '$2a$10$/18BvCN1toDObzdS8OfMZOhGYAgJCNK.WDdodyl4HvE51bOTogJzy',
        false,
        'USER',
        '11111111-1111-1111-1111-111111111004',
        'Unidade B'
    ),
    (
        '55555555-5555-5555-5555-555555555009',
        'Ricardo Menezes',
        'ricardo.menezes@inovare.med.br',
        '$2a$10$6nWPUSHrKJxoDX2p0CSjoOl8FO7ByzPN.hJZeTkdp2GqKDEPwU52u',
        false,
        'TECHNICIAN',
        '11111111-1111-1111-1111-111111111001',
        'Sede'
    );

-- 10 ativos adicionais para ampliar o volume de CMDB e distribuicao por usuarios
INSERT INTO assets (id, user_id, name, patrimony_code, category_id, specifications, created_at) VALUES
    ('77777777-7777-7777-7777-777777777005', '55555555-5555-5555-5555-555555555006', 'Notebook Dell Latitude 5440',  'INV-NTB-0201', '44444444-4444-4444-4444-444444444001', 'CPU i5, 16GB RAM, SSD 512GB, Windows 11', NOW() - INTERVAL '90 days'),
    ('77777777-7777-7777-7777-777777777006', '55555555-5555-5555-5555-555555555006', 'Dock Station Dell WD19',       'INV-ACC-0202', '44444444-4444-4444-4444-444444444003', 'Dock USB-C com HDMI e Ethernet', NOW() - INTERVAL '75 days'),
    ('77777777-7777-7777-7777-777777777007', '55555555-5555-5555-5555-555555555007', 'Notebook Lenovo E15 Gen 4',   'INV-NTB-0203', '44444444-4444-4444-4444-444444444001', 'CPU i7, 16GB RAM, SSD 512GB, Windows 11', NOW() - INTERVAL '120 days'),
    ('77777777-7777-7777-7777-777777777008', '55555555-5555-5555-5555-555555555007', 'Headset Jabra Evolve 20',     'INV-ACC-0204', '44444444-4444-4444-4444-444444444003', 'Headset USB para atendimento e reunioes', NOW() - INTERVAL '60 days'),
    ('77777777-7777-7777-7777-777777777009', '55555555-5555-5555-5555-555555555008', 'Desktop Dell OptiPlex 7010',  'INV-DESK-0205','44444444-4444-4444-4444-444444444002', 'CPU i5, 16GB RAM, SSD 256GB', NOW() - INTERVAL '110 days'),
    ('77777777-7777-7777-7777-777777777010', '55555555-5555-5555-5555-555555555008', 'Monitor Samsung 27pol',       'INV-MON-0206', '44444444-4444-4444-4444-444444444003', 'Monitor 27 polegadas Full HD', NOW() - INTERVAL '95 days'),
    ('77777777-7777-7777-7777-777777777011', '55555555-5555-5555-5555-555555555003', 'Notebook HP ProBook 440',     'INV-NTB-0207', '44444444-4444-4444-4444-444444444001', 'CPU i5, 8GB RAM, SSD 256GB', NOW() - INTERVAL '140 days'),
    ('77777777-7777-7777-7777-777777777012', '55555555-5555-5555-5555-555555555004', 'Monitor Dell P2422H',         'INV-MON-0208', '44444444-4444-4444-4444-444444444003', 'Monitor 24 polegadas IPS', NOW() - INTERVAL '85 days'),
    ('77777777-7777-7777-7777-777777777013', '55555555-5555-5555-5555-555555555005', 'Desktop Lenovo Neo 50s',      'INV-DESK-0209','44444444-4444-4444-4444-444444444002', 'CPU i5, 8GB RAM, SSD 512GB', NOW() - INTERVAL '70 days'),
    ('77777777-7777-7777-7777-777777777014', '55555555-5555-5555-5555-555555555006', 'Monitor LG UltraWide 29pol',  'INV-MON-0210', '44444444-4444-4444-4444-444444444003', 'Monitor ultrawide para analise financeira', NOW() - INTERVAL '55 days');

-- 15 chamados adicionais para aumentar a disputa do Top 5 por solicitante e setor
INSERT INTO tickets (id, title, description, status, priority, requester_id, assigned_to_id, category_id, sla_deadline, created_at, closed_at) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa029', 'Chamado #29 - Financeiro sem acesso ao BI',           'Painel financeiro retorna erro 403 apos troca de senha',     'OPEN',        'HIGH',   '55555555-5555-5555-5555-555555555006', NULL,                                     '22222222-2222-2222-2222-222222222004', NOW() - INTERVAL '9 days'  + INTERVAL '24 hours', NOW() - INTERVAL '9 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa030', 'Chamado #30 - RH com travamento no Outlook',          'Outlook fecha ao anexar curriculos',                         'OPEN',        'NORMAL', '55555555-5555-5555-5555-555555555007', NULL,                                     '22222222-2222-2222-2222-222222222002', NOW() - INTERVAL '8 days'  + INTERVAL '24 hours', NOW() - INTERVAL '8 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa031', 'Chamado #31 - Operacoes com impressora parada',       'Impressora Zebra nao responde para etiquetas',               'OPEN',        'HIGH',   '55555555-5555-5555-5555-555555555008', NULL,                                     '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '7 days'  + INTERVAL '48 hours', NOW() - INTERVAL '7 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa032', 'Chamado #32 - Financeiro sem VPN',                    'VPN falha ao conectar durante trabalho remoto',              'OPEN',        'URGENT', '55555555-5555-5555-5555-555555555006', NULL,                                     '22222222-2222-2222-2222-222222222003', NOW() - INTERVAL '2 days'  + INTERVAL '12 hours', NOW() - INTERVAL '2 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa033', 'Chamado #33 - RH sem permissao na pasta de beneficios','Acesso negado ao compartilhamento de beneficios',            'OPEN',        'NORMAL', '55555555-5555-5555-5555-555555555007', NULL,                                     '22222222-2222-2222-2222-222222222004', NOW() - INTERVAL '6 days'  + INTERVAL '24 hours', NOW() - INTERVAL '6 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa034', 'Chamado #34 - Operacoes com lente do leitor quebrada', 'Leitor de codigo de barras nao reconhece etiquetas',        'IN_PROGRESS', 'HIGH',   '55555555-5555-5555-5555-555555555008', '55555555-5555-5555-5555-555555555009', '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '5 days'  + INTERVAL '48 hours', NOW() - INTERVAL '5 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa035', 'Chamado #35 - Financeiro com planilha lenta',         'Arquivo macroeconomico demora mais de 10 minutos para abrir','IN_PROGRESS', 'NORMAL', '55555555-5555-5555-5555-555555555006', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222002', NOW() - INTERVAL '4 days'  + INTERVAL '24 hours', NOW() - INTERVAL '4 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa036', 'Chamado #36 - RH com falha no Teams',                 'Microfone nao funciona em entrevistas online',              'IN_PROGRESS', 'LOW',    '55555555-5555-5555-5555-555555555007', '55555555-5555-5555-5555-555555555009', '22222222-2222-2222-2222-222222222002', NOW() - INTERVAL '3 days'  + INTERVAL '24 hours', NOW() - INTERVAL '3 days',  NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa037', 'Chamado #37 - Operacoes sem rede no coletor',         'Coletor de dados perdeu acesso ao Wi-Fi do armazem',        'IN_PROGRESS', 'URGENT', '55555555-5555-5555-5555-555555555008', '55555555-5555-5555-5555-555555555009', '22222222-2222-2222-2222-222222222003', NOW() - INTERVAL '1 day'   + INTERVAL '12 hours', NOW() - INTERVAL '1 day',   NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa038', 'Chamado #38 - Financeiro com MFA desalinhado',        'Aplicativo autenticador gera codigo invalido',              'IN_PROGRESS', 'HIGH',   '55555555-5555-5555-5555-555555555006', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222004', NOW() - INTERVAL '11 hours' + INTERVAL '24 hours', NOW() - INTERVAL '11 hours', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa039', 'Chamado #39 - RH com desktop reiniciando',            'Desktop reinicia durante admissao de novos colaboradores',  'RESOLVED',    'HIGH',   '55555555-5555-5555-5555-555555555007', '55555555-5555-5555-5555-555555555009', '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '18 days' + INTERVAL '48 hours', NOW() - INTERVAL '18 days', NOW() - INTERVAL '17 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa040', 'Chamado #40 - Operacoes com lentidao no ERP',         'ERP demora para salvar pedidos de expedicao',               'RESOLVED',    'NORMAL', '55555555-5555-5555-5555-555555555008', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222002', NOW() - INTERVAL '16 days' + INTERVAL '24 hours', NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa041', 'Chamado #41 - Financeiro com impressao cortando',     'Notas fiscais impressas saem truncadas na margem direita',  'RESOLVED',    'LOW',    '55555555-5555-5555-5555-555555555006', '55555555-5555-5555-5555-555555555009', '22222222-2222-2222-2222-222222222001', NOW() - INTERVAL '14 days' + INTERVAL '48 hours', NOW() - INTERVAL '14 days', NOW() - INTERVAL '13 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa042', 'Chamado #42 - RH com acesso negado ao portal',        'Portal interno nao reconhece perfil de RH apos onboarding', 'RESOLVED',    'NORMAL', '55555555-5555-5555-5555-555555555007', '55555555-5555-5555-5555-555555555002', '22222222-2222-2222-2222-222222222004', NOW() - INTERVAL '12 days' + INTERVAL '24 hours', NOW() - INTERVAL '12 days', NOW() - INTERVAL '11 days'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa043', 'Chamado #43 - Operacoes sem acesso ao coletor web',   'Tela inicial do coletor retorna timeout apos login',        'RESOLVED',    'URGENT', '55555555-5555-5555-5555-555555555008', '55555555-5555-5555-5555-555555555009', '22222222-2222-2222-2222-222222222003', NOW() - INTERVAL '10 days' + INTERVAL '12 hours', NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days' + INTERVAL '5 hours');