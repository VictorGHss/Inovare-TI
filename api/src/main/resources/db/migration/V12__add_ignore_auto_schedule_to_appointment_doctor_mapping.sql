ALTER TABLE appointment_doctor_mapping
    ADD COLUMN IF NOT EXISTS ignore_auto_schedule boolean NOT NULL DEFAULT false;
