-- V16: Criação de índices ausentes para otimização de buscas frequentes (patient_phone e session_id)

CREATE INDEX IF NOT EXISTS idx_appointment_sessions_patient_phone 
    ON appointment_sessions(patient_phone);

CREATE INDEX IF NOT EXISTS idx_notification_groups_session_id 
    ON notification_groups(session_id);
