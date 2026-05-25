-- Adiciona coluna last_notification_sent_at na tabela appointment_sessions
ALTER TABLE appointment_sessions 
    ADD COLUMN IF NOT EXISTS last_notification_sent_at timestamp;
