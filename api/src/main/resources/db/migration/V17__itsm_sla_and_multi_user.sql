-- V17: Categorias ITSM com SLA configurável e tabela de usuários adicionais afetados por chamado

-- Tabela de categorias ITSM com prazo de SLA em horas
CREATE TABLE itsm_categories (
    id        SERIAL PRIMARY KEY,
    name      VARCHAR(150) NOT NULL UNIQUE,
    sla_hours INT          NOT NULL
);

-- Sementes: categorias padrão da TI Inovare
INSERT INTO itsm_categories (name, sla_hours) VALUES
    ('Internet e Links',        2),
    ('Impressoras e Etiquetas', 4),
    ('Instabilidade Feegow',    1),
    ('Hardware e Periféricos',  8),
    ('Dúvidas Operacionais',   24);

-- Tabela de junção: usuários adicionais afetados por um chamado
-- Permite vincular múltiplos colaboradores impactados pelo mesmo incidente
CREATE TABLE ticket_additional_users (
    ticket_id UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    PRIMARY KEY (ticket_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_ticket_additional_users_ticket_id
    ON ticket_additional_users(ticket_id);

CREATE INDEX IF NOT EXISTS idx_ticket_additional_users_user_id
    ON ticket_additional_users(user_id);
