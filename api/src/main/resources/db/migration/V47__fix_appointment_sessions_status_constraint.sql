-- Migração V47: Atualização da Constraint de Status em appointment_sessions para suporte a todos os status do ciclo de vida
ALTER TABLE appointment_sessions
    DROP CONSTRAINT IF EXISTS ck_appointment_sessions_status;

ALTER TABLE appointment_sessions
    ADD CONSTRAINT ck_appointment_sessions_status
    CHECK (status IN (
        'PENDING', 
        'NUDGE_1_SENT', 
        'NUDGE_FINAL_SENT', 
        'CONFIRMED', 
        'CANCELED_NO_RESPONSE', 
        'CANCELED', 
        'CANCELLED', 
        'CLOSED', 
        'ERROR_DELIVERY', 
        'ALTERATION_REQUESTED', 
        'EXPIRED'
    ));
