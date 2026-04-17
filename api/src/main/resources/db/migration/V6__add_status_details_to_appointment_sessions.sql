ALTER TABLE appointment_sessions
    ADD COLUMN IF NOT EXISTS status_details varchar(500);
