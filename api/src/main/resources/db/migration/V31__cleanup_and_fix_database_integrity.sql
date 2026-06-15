-- V31__cleanup_and_fix_database_integrity.sql
-- Saneamento Relacional e Correção de Integridade ACID no Banco de Dados

-- 1. Remoção de Resíduos (Tabelas Órfãs e Vazias)
-- Remove tabelas obsoletas identificadas no dump que não possuem mais utilidade ou dependências no sistema.
DROP TABLE IF EXISTS appointment_template_variable_mapping;
DROP TABLE IF EXISTS appointment_variable_logs;
DROP TABLE IF EXISTS processing_attempts;

-- 2. Correção de Integridade ACID (itsm_user_id)
-- Passo A: Limpeza prévia de registros inconsistentes usando UPDATE
-- Valores que não seguem o padrão de formato UUID ou que não possuam correspondência na tabela public.users
-- são definidos como NULL antes da conversão de tipo para evitar quebra de integridade referencial.
UPDATE appointment_doctor_mapping
SET itsm_user_id = NULL
WHERE itsm_user_id IS NOT NULL 
  AND (itsm_user_id !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
       OR itsm_user_id::uuid NOT IN (SELECT id FROM public.users));

-- Passo B: Alteração de tipo segura sem subquery na expressão
-- Agora que todos os dados inconsistentes foram sanitizados, a conversão para UUID é executada com segurança.
ALTER TABLE appointment_doctor_mapping 
    ALTER COLUMN itsm_user_id TYPE UUID USING itsm_user_id::uuid;

-- Adiciona a restrição de Chave Estrangeira (FOREIGN KEY) ligando o campo itsm_user_id diretamente à tabela public.users.
-- Configura a ação de exclusão do usuário pai como ON DELETE SET NULL.
ALTER TABLE appointment_doctor_mapping
    ADD CONSTRAINT fk_appointment_doctor_mapping_itsm_user
    FOREIGN KEY (itsm_user_id)
    REFERENCES public.users(id)
    ON DELETE SET NULL;

-- 3. Otimização de Normalização (Eliminação de Redundância)
-- Remove a coluna redundante profissional_nome da tabela appointment_doctor_mapping.
-- Restabelece a Terceira Forma Normal (3FN), delegando a resolução do nome do profissional de saúde
-- para que ocorra de forma dinâmica via API/Adapter do Feegow.
ALTER TABLE appointment_doctor_mapping
    DROP COLUMN IF EXISTS profissional_nome;
