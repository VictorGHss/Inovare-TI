-- Módulo de confirmação automática de consultas

CREATE TABLE appointment_configs (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    category varchar(32) NOT NULL,
    template_id varchar(120) NOT NULL,
    timing_hours integer NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_appointment_configs PRIMARY KEY (id),
    CONSTRAINT uq_appointment_configs_category UNIQUE (category),
    CONSTRAINT ck_appointment_configs_category CHECK (category IN ('CONFIRMATION', 'NUDGE_1', 'NUDGE_FINAL')),
    CONSTRAINT ck_appointment_configs_timing CHECK (timing_hours >= 0)
);

CREATE TABLE appointment_template_variable_mapping (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    config_id uuid NOT NULL,
    placeholder_index integer NOT NULL,
    dictionary_key varchar(80) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_appointment_template_variable_mapping PRIMARY KEY (id),
    CONSTRAINT fk_appointment_template_variable_mapping_config FOREIGN KEY (config_id) REFERENCES appointment_configs(id) ON DELETE CASCADE,
    CONSTRAINT uq_appointment_template_variable_mapping_config_placeholder UNIQUE (config_id, placeholder_index)
);

CREATE TABLE appointment_sessions (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    feegow_appointment_id varchar(64) NOT NULL,
    patient_id varchar(64) NOT NULL,
    patient_phone varchar(40),
    doctor_profissional_id varchar(64),
    appointment_at timestamp NOT NULL,
    status varchar(32) NOT NULL,
    last_interaction_at timestamp NOT NULL,
    closed_at timestamp,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_appointment_sessions PRIMARY KEY (id),
    CONSTRAINT uq_appointment_sessions_feegow_appointment_id UNIQUE (feegow_appointment_id),
    CONSTRAINT ck_appointment_sessions_status CHECK (status IN ('PENDING', 'NUDGE_1_SENT', 'NUDGE_FINAL_SENT', 'CONFIRMED', 'CANCELED_NO_RESPONSE'))
);

CREATE INDEX idx_appointment_sessions_status_last_interaction
    ON appointment_sessions(status, last_interaction_at);

CREATE TABLE appointment_variable_logs (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    session_id uuid NOT NULL,
    category varchar(32) NOT NULL,
    placeholder_index integer NOT NULL,
    dictionary_key varchar(80) NOT NULL,
    resolved_value text NOT NULL,
    sent_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_appointment_variable_logs PRIMARY KEY (id),
    CONSTRAINT fk_appointment_variable_logs_session FOREIGN KEY (session_id) REFERENCES appointment_sessions(id) ON DELETE CASCADE,
    CONSTRAINT ck_appointment_variable_logs_category CHECK (category IN ('CONFIRMATION', 'NUDGE_1', 'NUDGE_FINAL'))
);

CREATE TABLE appointment_doctor_mapping (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    profissional_id varchar(64) NOT NULL,
    secretary_names varchar(255),
    blip_queue_id varchar(120) NOT NULL,
    is_external boolean NOT NULL DEFAULT false,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_appointment_doctor_mapping PRIMARY KEY (id),
    CONSTRAINT uq_appointment_doctor_mapping_profissional_id UNIQUE (profissional_id)
);

INSERT INTO appointment_configs(category, template_id, timing_hours)
VALUES
    ('CONFIRMATION', 'confirmacao_consulta_v1', 0),
    ('NUDGE_1', 'aviso_interacao_necessariav1', 4),
    ('NUDGE_FINAL', 'aviso_final_cancelamento', 4)
ON CONFLICT (category) DO NOTHING;

INSERT INTO appointment_template_variable_mapping(config_id, placeholder_index, dictionary_key)
SELECT cfg.id, map.placeholder_index, map.dictionary_key
FROM appointment_configs cfg
JOIN (
    VALUES
        ('CONFIRMATION', 1, 'PACIENTE_NOME'),
        ('CONFIRMATION', 2, 'AGENDAMENTO_DATA'),
        ('CONFIRMATION', 3, 'MEDICO_NOME'),
        ('NUDGE_1', 1, 'PACIENTE_NOME'),
        ('NUDGE_1', 2, 'AGENDAMENTO_DATA'),
        ('NUDGE_FINAL', 1, 'PACIENTE_NOME'),
        ('NUDGE_FINAL', 2, 'AGENDAMENTO_DATA')
) AS map(category, placeholder_index, dictionary_key)
    ON map.category = cfg.category
ON CONFLICT (config_id, placeholder_index) DO NOTHING;

INSERT INTO appointment_doctor_mapping(profissional_id, secretary_names, blip_queue_id, is_external)
VALUES ('EXTERNAL', 'Atendimento Externo', 'queue-external', true)
ON CONFLICT (profissional_id) DO NOTHING;
