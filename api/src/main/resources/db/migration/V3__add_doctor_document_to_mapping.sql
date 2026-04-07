ALTER TABLE doctor_email_mapping
    ADD COLUMN IF NOT EXISTS doctor_cpf_cnpj varchar(20);
