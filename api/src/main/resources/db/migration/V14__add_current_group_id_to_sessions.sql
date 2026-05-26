-- Adiciona coluna current_group_id na tabela appointment_sessions
ALTER TABLE appointment_sessions
    ADD COLUMN IF NOT EXISTS current_group_id uuid;

CREATE INDEX IF NOT EXISTS idx_appointment_sessions_current_group_id
    ON appointment_sessions(current_group_id);
