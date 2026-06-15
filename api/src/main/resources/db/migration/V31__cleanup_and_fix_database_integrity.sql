-- V31__cleanup_and_fix_database_integrity.sql
-- Saneamento Relacional e Correção de Integridade ACID no Banco de Dados

-- 1. Remoção de Resíduos (Tabelas Órfãs e Vazias)
-- Remove tabelas obsoletas identificadas no dump que não possuem mais utilidade ou dependências no sistema.
DROP TABLE IF EXISTS appointment_template_variable_mapping;
DROP TABLE IF EXISTS appointment_variable_logs;
DROP TABLE IF EXISTS processing_attempts;

-- 2. Correção de Integridade ACID (itsm_user_id)
-- Converte o campo itsm_user_id da tabela appointment_doctor_mapping para o tipo UUID.
-- Valores que não seguem o padrão de formato UUID ou que não possuam correspondência na tabela public.users
-- são definidos como NULL de forma a manter a consistência e integridade referencial dos dados.
ALTER TABLE appointment_doctor_mapping
    ALTER COLUMN itsm_user_id TYPE UUID USING (
        CASE 
            WHEN itsm_user_id ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' 
                 AND EXISTS (SELECT 1 FROM public.users u WHERE u.id = itsm_user_id::uuid)
            THEN itsm_user_id::uuid 
            ELSE NULL 
        END
    );

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
