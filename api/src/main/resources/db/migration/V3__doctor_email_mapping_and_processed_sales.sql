CREATE TABLE doctor_email_mapping (
    id                     uuid         NOT NULL DEFAULT gen_random_uuid(),
    contaazul_customer_uuid varchar(64) NOT NULL,
    doctor_email           varchar(255) NOT NULL,
    created_at             timestamp    NOT NULL DEFAULT now(),
    updated_at             timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_doctor_email_mapping PRIMARY KEY (id),
    CONSTRAINT uq_doctor_email_mapping_customer_uuid UNIQUE (contaazul_customer_uuid)
);

CREATE TABLE processed_sales (
    id           uuid         NOT NULL DEFAULT gen_random_uuid(),
    sale_id      varchar(120) NOT NULL,
    processed_at timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_sales PRIMARY KEY (id),
    CONSTRAINT uq_processed_sales_sale_id UNIQUE (sale_id)
);

CREATE INDEX idx_processed_sales_sale_id ON processed_sales (sale_id);