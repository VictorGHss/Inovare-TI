-- Adiciona a coluna 'active' na tabela de setores (caso não exista)
ALTER TABLE sectors ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT true;

-- Cria a tabela de junção 'asset_users' (caso não exista)
CREATE TABLE IF NOT EXISTS asset_users (
    asset_id UUID NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY (asset_id, user_id),
    CONSTRAINT fk_asset_users_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_users_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
