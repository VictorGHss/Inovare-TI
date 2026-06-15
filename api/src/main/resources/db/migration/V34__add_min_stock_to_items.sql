-- V34__add_min_stock_to_items.sql
-- Módulo de Alertas Preventivos de Estoque
-- Adiciona a coluna min_stock na tabela items para controle de alertas de estoque crítico.

ALTER TABLE items ADD COLUMN min_stock INTEGER NOT NULL DEFAULT 0;
