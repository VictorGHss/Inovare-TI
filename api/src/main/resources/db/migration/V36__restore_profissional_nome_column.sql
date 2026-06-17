-- V36__restore_profissional_nome_column.sql
-- Restaura a coluna profissional_nome na tabela appointment_doctor_mapping
-- e atualiza os registros dos médicos de teste que ficaram com dados incorretos.

ALTER TABLE appointment_doctor_mapping
    ADD COLUMN IF NOT EXISTS profissional_nome varchar(255);

-- Atualiza os registros dos médicos de teste com seus nomes descritivos correspondentes
UPDATE appointment_doctor_mapping SET profissional_nome = 'Dr. Carlos Heidi Koga' WHERE profissional_id = '8';
UPDATE appointment_doctor_mapping SET profissional_nome = 'Dr. Alisson Vinicius Emerique Fucio' WHERE profissional_id = '6';
UPDATE appointment_doctor_mapping SET profissional_nome = 'Dr. Eduardo Bisinella' WHERE profissional_id = '7';
UPDATE appointment_doctor_mapping SET profissional_nome = 'Dr. Magno Zanellato' WHERE profissional_id = '13';
UPDATE appointment_doctor_mapping SET profissional_nome = 'Dr. Marcelo Tessari' WHERE profissional_id = '14';
UPDATE appointment_doctor_mapping SET profissional_nome = 'Dr. Cesar Toshio Oda' WHERE profissional_id = '12';
