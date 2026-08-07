-- Adiciona a coluna google_review_url na tabela doctor_configurations
ALTER TABLE doctor_configurations ADD COLUMN google_review_url VARCHAR(500);

-- Adiciona a coluna review_requested_at na tabela appointment_sessions
ALTER TABLE appointment_sessions ADD COLUMN review_requested_at TIMESTAMP;
