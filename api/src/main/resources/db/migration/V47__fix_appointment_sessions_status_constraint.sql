-- Migração V47: Atualização da Constraint de Status em appointment_sessions para suporte a ALTERATION_REQUESTED e EXPIRED
ALTER TABLE appointment_sessions
    DROP CONSTRAINT IF EXISTS ck_appointment_sessions_status;

ALTER TABLE appointment_sessions
    ADD CONSTRAINT ck_appointment_sessions_status
    CHECK (status IN (
        'PENDING', 
        'CONFIRMED', 
        'CANCELLED', 
        'CLOSED', 
        'ALTERATION_REQUESTED',
        'EXPIRED'
    ));
