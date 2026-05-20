-- V7__add_solution_text_to_tickets.sql
-- Adiciona a coluna solution_text para armazenar o texto da solução do chamado
ALTER TABLE tickets ADD COLUMN solution_text text;
