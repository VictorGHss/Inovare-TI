-- V3__seed_test_data.sql
-- Popula dados de teste mínimos: usuários, itens, lotes e chamados de exemplo
-- Esta migration é escrita para ser idempotente: usa SELECT ... WHERE NOT EXISTS

-- -----------------------------
-- 1) Usuários (Admin, Técnico, Usuário)
-- -----------------------------
INSERT INTO users (id, name, email, password_hash, role, sector_id, location)
SELECT gen_random_uuid(), 'Administrador', 'admin@inovare.med.br', '$2a$10$Y5baEu7GNo3.H/2.Yc3jUeF6zYc9F6zYc9F6zYc9F6zYc9F6zYc9F6', 'ADMIN',
       (SELECT id FROM sectors WHERE name = 'TI' LIMIT 1), 'Sede'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@inovare.med.br');

INSERT INTO users (id, name, email, password_hash, role, sector_id, location)
SELECT gen_random_uuid(), 'Técnico Suporte', 'tecnico@inovare.med.br', '$2a$10$Y5baEu7GNo3.H/2.Yc3jUeF6zYc9F6zYc9F6zYc9F6zYc9F6zYc9F6', 'TECHNICIAN',
       (SELECT id FROM sectors WHERE name = 'TI' LIMIT 1), 'Sede'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'tecnico@inovare.med.br');

INSERT INTO users (id, name, email, password_hash, role, sector_id, location)
SELECT gen_random_uuid(), 'João Silva', 'joao.silva@inovare.med.br', '$2a$10$Y5baEu7GNo3.H/2.Yc3jUeF6zYc9F6zYc9F6zYc9F6zYc9F6zYc9F6', 'USER',
       (SELECT id FROM sectors WHERE name = 'Financeiro' LIMIT 1), 'Andar 3'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'joao.silva@inovare.med.br');

-- -----------------------------
-- 2) Itens de inventário (alguns periféricos)
-- -----------------------------
INSERT INTO items (id, item_category_id, name, current_stock)
SELECT gen_random_uuid(), (SELECT id FROM item_categories WHERE name = 'Perifericos' LIMIT 1), 'Mouse Wireless Logitech', 15
WHERE NOT EXISTS (SELECT 1 FROM items WHERE name = 'Mouse Wireless Logitech');

INSERT INTO items (id, item_category_id, name, current_stock)
SELECT gen_random_uuid(), (SELECT id FROM item_categories WHERE name = 'Perifericos' LIMIT 1), 'Teclado Mecanico RGB', 8
WHERE NOT EXISTS (SELECT 1 FROM items WHERE name = 'Teclado Mecanico RGB');

INSERT INTO items (id, item_category_id, name, current_stock)
SELECT gen_random_uuid(), (SELECT id FROM item_categories WHERE name = 'Monitor' LIMIT 1), 'Monitor Dell 24"', 3
WHERE NOT EXISTS (SELECT 1 FROM items WHERE name = 'Monitor Dell 24"');

-- -----------------------------
-- 3) Lotes de estoque (um por item acima)
-- -----------------------------
INSERT INTO stock_batches (id, item_id, original_quantity, remaining_quantity, unit_price, brand, supplier, purchase_reason, entry_date)
SELECT gen_random_uuid(), i.id, i.current_stock, i.current_stock, 49.90::numeric(12,2), 'Logitech', 'Kabum', 'Reposição inicial', now()
FROM items i
WHERE i.name = 'Mouse Wireless Logitech'
  AND NOT EXISTS (SELECT 1 FROM stock_batches sb WHERE sb.item_id = i.id);

INSERT INTO stock_batches (id, item_id, original_quantity, remaining_quantity, unit_price, brand, supplier, purchase_reason, entry_date)
SELECT gen_random_uuid(), i.id, i.current_stock, i.current_stock, 180.00::numeric(12,2), 'Generic', 'Amazon', 'Reposição inicial', now()
FROM items i
WHERE i.name = 'Teclado Mecanico RGB'
  AND NOT EXISTS (SELECT 1 FROM stock_batches sb WHERE sb.item_id = i.id);

INSERT INTO stock_batches (id, item_id, original_quantity, remaining_quantity, unit_price, brand, supplier, purchase_reason, entry_date)
SELECT gen_random_uuid(), i.id, i.current_stock, i.current_stock, 899.00::numeric(12,2), 'Dell', 'Dell Store', 'Reposição inicial', now()
FROM items i
WHERE i.name = 'Monitor Dell 24"'
  AND NOT EXISTS (SELECT 1 FROM stock_batches sb WHERE sb.item_id = i.id);

-- -----------------------------
-- 4) Chamados de exemplo (pelo menos 3)
-- -----------------------------
-- Chamado 1: Solicitação de Acesso (aberto)
INSERT INTO tickets (id, title, description, status, priority, requester_id, assigned_to_id, category_id, requested_item_id, requested_quantity, sla_deadline, created_at)
SELECT gen_random_uuid(), 'Solicitação de Acesso ao Sistema', 'Solicitação para acesso ao painel administrativo', 'OPEN', 'NORMAL',
       (SELECT id FROM users WHERE email = 'joao.silva@inovare.med.br' LIMIT 1),
       (SELECT id FROM users WHERE email = 'tecnico@inovare.med.br' LIMIT 1),
       (SELECT id FROM ticket_categories WHERE name = 'Acessos' LIMIT 1),
       NULL, NULL, now() + interval '24 hours', now()
WHERE NOT EXISTS (SELECT 1 FROM tickets t WHERE t.title = 'Solicitação de Acesso ao Sistema' AND t.requester_id = (SELECT id FROM users WHERE email = 'joao.silva@inovare.med.br' LIMIT 1));

-- Chamado 2: Hardware - Monitor com defeito (em andamento)
INSERT INTO tickets (id, title, description, status, priority, requester_id, assigned_to_id, category_id, requested_item_id, requested_quantity, sla_deadline, created_at)
SELECT gen_random_uuid(), 'Monitor com tela quebrada', 'Monitor Dell 24" apresentando tela quebrada', 'IN_PROGRESS', 'HIGH',
       (SELECT id FROM users WHERE email = 'joao.silva@inovare.med.br' LIMIT 1),
       (SELECT id FROM users WHERE email = 'tecnico@inovare.med.br' LIMIT 1),
       (SELECT id FROM ticket_categories WHERE name = 'Hardware' LIMIT 1),
       (SELECT id FROM items WHERE name = 'Monitor Dell 24"' LIMIT 1), 1, now() + interval '48 hours', now()
WHERE NOT EXISTS (SELECT 1 FROM tickets t WHERE t.title = 'Monitor com tela quebrada' AND t.requester_id = (SELECT id FROM users WHERE email = 'joao.silva@inovare.med.br' LIMIT 1));

-- Chamado 3: Solicitação de Item (resolvido)
INSERT INTO tickets (id, title, description, status, priority, requester_id, assigned_to_id, category_id, requested_item_id, requested_quantity, sla_deadline, created_at, closed_at)
SELECT gen_random_uuid(), 'Solicitação de Mouse', 'Solicitação automática de reposição de mouse', 'RESOLVED', 'NORMAL',
       (SELECT id FROM users WHERE email = 'joao.silva@inovare.med.br' LIMIT 1),
       (SELECT id FROM users WHERE email = 'tecnico@inovare.med.br' LIMIT 1),
       (SELECT id FROM ticket_categories WHERE name = 'Hardware' LIMIT 1),
       (SELECT id FROM items WHERE name = 'Mouse Wireless Logitech' LIMIT 1), 1, now() + interval '24 hours', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM tickets t WHERE t.title = 'Solicitação de Mouse' AND t.requester_id = (SELECT id FROM users WHERE email = 'joao.silva@inovare.med.br' LIMIT 1));

-- Fim da migration V3
