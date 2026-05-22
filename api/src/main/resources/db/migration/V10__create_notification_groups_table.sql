-- Criar tabela de acúmulo de notificações para múltiplos agendamentos
CREATE TABLE IF NOT EXISTS notification_groups (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    group_id uuid NOT NULL,
    session_id uuid NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification_groups PRIMARY KEY (id),
    CONSTRAINT fk_notification_groups_session FOREIGN KEY (session_id) REFERENCES appointment_sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notification_groups_group_id 
    ON notification_groups(group_id);
