ALTER TABLE doctor_email_mapping
    ADD COLUMN IF NOT EXISTS doctor_name varchar(160);

UPDATE doctor_email_mapping
SET doctor_name = COALESCE(NULLIF(TRIM(doctor_name), ''), 'Médico não informado');

ALTER TABLE doctor_email_mapping
    ALTER COLUMN doctor_name SET NOT NULL;