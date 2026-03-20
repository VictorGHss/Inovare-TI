-- 1. TICKET CATEGORIES (Categorias de Chamados com SLA)
INSERT INTO ticket_categories (id, name, base_sla_hours) VALUES
    ('22222222-2222-2222-2222-222222222001', 'Hardware', 48),
    ('22222222-2222-2222-2222-222222222002', 'Software', 24),
    ('22222222-2222-2222-2222-222222222003', 'Rede', 12),
    ('22222222-2222-2222-2222-222222222004', 'Acessos', 24)
ON CONFLICT (id) DO NOTHING;

-- 2. ITEM CATEGORIES (Estoque/Inventário)
INSERT INTO item_categories (id, name, is_consumable) VALUES
    (gen_random_uuid(), 'Consumiveis', true),
    (gen_random_uuid(), 'Ativos', false),
    (gen_random_uuid(), 'Componentes', false),
    (gen_random_uuid(), 'Perifericos', false),
    (gen_random_uuid(), 'Infra de Rede', false),
    (gen_random_uuid(), 'Ferramentas', false),
    (gen_random_uuid(), 'Software', false),
    (gen_random_uuid(), 'Telefone', false),
    (gen_random_uuid(), 'Monitor', false),
    (gen_random_uuid(), 'Impressora', false)
ON CONFLICT DO NOTHING;

-- 3. ASSET CATEGORIES (Patrimônio)
INSERT INTO asset_categories (id, name) VALUES
    ('44444444-4444-4444-4444-444444444001', 'Laptops'),
    ('44444444-4444-4444-4444-444444444002', 'Desktops'),
    ('44444444-4444-4444-4444-444444444003', 'Acessorios'),
    (gen_random_uuid(), 'Monitores'),
    (gen_random_uuid(), 'Impressoras'),
    (gen_random_uuid(), 'Telefonia')
ON CONFLICT DO NOTHING;

-- 4. SECTORS (Lista Completa Inovare)
INSERT INTO sectors (id, name) VALUES
    ('11111111-1111-1111-1111-111111111001', 'TI'),
    ('11111111-1111-1111-1111-111111111002', 'Financeiro'),
    ('11111111-1111-1111-1111-111111111003', 'Recursos Humanos'),
    ('11111111-1111-1111-1111-111111111004', 'Operacoes'),
    (gen_random_uuid(), 'Clinipon'),
    (gen_random_uuid(), 'Clinica da Imagem'),
    (gen_random_uuid(), 'Endoscopia'),
    (gen_random_uuid(), 'Anesthemed'),
    (gen_random_uuid(), 'Clinica Geral'),
    (gen_random_uuid(), 'Oftalmologia Cenovicz'),
    (gen_random_uuid(), 'Cardiologia'),
    (gen_random_uuid(), 'Ginecologia'),
    (gen_random_uuid(), 'Ortopedia'),
    (gen_random_uuid(), 'Urologia'),
    (gen_random_uuid(), 'Pediatria'),
    (gen_random_uuid(), 'Psiquiatria'),
    (gen_random_uuid(), 'Psicologia'),
    (gen_random_uuid(), 'Cirurgia Vascular'),
    (gen_random_uuid(), 'Neurologia'),
    (gen_random_uuid(), 'Cirurgia Do Aparelho Digestivo'),
    (gen_random_uuid(), 'FonoAudiologia'),
    (gen_random_uuid(), 'Dermatologista'),
    (gen_random_uuid(), 'Nefrologia'),
    (gen_random_uuid(), 'Odontologia'),
    (gen_random_uuid(), 'Cirurgia Plastica'),
    (gen_random_uuid(), 'Endocrinologia'),
    (gen_random_uuid(), 'Recepção'),
    (gen_random_uuid(), 'Cirurgia Geral'),
    (gen_random_uuid(), 'Medicina Interna'),
    (gen_random_uuid(), 'Administrativo'),
    (gen_random_uuid(), 'Indefinido'),
    (gen_random_uuid(), 'Alergologia'),
    (gen_random_uuid(), 'Eletrocardiograma'),
    (gen_random_uuid(), 'Mapa/Holter'),
    (gen_random_uuid(), 'Teste De Esforço'),
    (gen_random_uuid(), 'Segurança'),
    (gen_random_uuid(), 'Espirometria'),
    (gen_random_uuid(), 'Pneumologia')
ON CONFLICT DO NOTHING;