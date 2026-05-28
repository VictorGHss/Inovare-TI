-- Purga a antiga tabela de relacionamento de texto para dar lugar a estrutura rica
DROP TABLE IF EXISTS ticket_tags CASCADE;

-- Criação da tabela mestre de tags corporativas
CREATE TABLE ticket_tags (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    name               VARCHAR(100)  NOT NULL,
    color              VARCHAR(20)   NOT NULL,
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    default_resolution TEXT,
    CONSTRAINT pk_ticket_tags      PRIMARY KEY (id),
    CONSTRAINT uq_ticket_tags_name UNIQUE      (name)
);

-- Criação da tabela de junção para relacionamento Many-to-Many entre tickets e tags
CREATE TABLE ticket_tag_relations (
    ticket_id UUID NOT NULL,
    tag_id    UUID NOT NULL,
    CONSTRAINT pk_ticket_tag_relations PRIMARY KEY (ticket_id, tag_id),
    CONSTRAINT fk_ttr_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ttr_tag    FOREIGN KEY (tag_id)    REFERENCES ticket_tags (id) ON DELETE CASCADE
);

-- Adiciona a coluna is_critical na tabela de ativos
ALTER TABLE assets ADD COLUMN is_critical BOOLEAN NOT NULL DEFAULT FALSE;

-- Adiciona a coluna asset_id na tabela de chamados (tickets) para permitir associar ativos a chamados
ALTER TABLE tickets ADD COLUMN asset_id UUID;
ALTER TABLE tickets ADD CONSTRAINT fk_tickets_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE SET NULL;
