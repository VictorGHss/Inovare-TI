-- Criação da tabela de associação asset_users para relacionamento N:N entre ativos e usuários
CREATE TABLE asset_users (
    asset_id UUID NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT pk_asset_users PRIMARY KEY (asset_id, user_id),
    CONSTRAINT fk_asset_users_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_users_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Migração dos dados existentes da coluna user_id na tabela assets para a tabela asset_users
INSERT INTO asset_users (asset_id, user_id)
SELECT id, user_id 
FROM assets 
WHERE user_id IS NOT NULL;

-- Remoção da coluna user_id da tabela assets
ALTER TABLE assets DROP COLUMN user_id;
