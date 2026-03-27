-- =============================================================================
-- V3__report_schedules.sql - Tabela para agendamentos de relatórios
-- =============================================================================

CREATE TABLE report_schedules (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    report_type varchar(60) NOT NULL,
    target_user_id uuid,
    send_email boolean NOT NULL DEFAULT true,
    send_discord boolean NOT NULL DEFAULT false,
    schedule_day integer NOT NULL DEFAULT 12,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_report_schedules PRIMARY KEY (id),
    CONSTRAINT fk_report_schedules_user FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_report_schedules_active ON report_schedules (is_active);
