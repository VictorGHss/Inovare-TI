CREATE TABLE appointment_template_mapping (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    template_name varchar(120) NOT NULL,
    placeholder_index integer NOT NULL,
    feegow_field_name varchar(120) NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_appointment_template_mapping PRIMARY KEY (id),
    CONSTRAINT uq_appointment_template_mapping_template_placeholder UNIQUE (template_name, placeholder_index)
);

CREATE INDEX idx_appointment_template_mapping_template_name
    ON appointment_template_mapping(template_name);
