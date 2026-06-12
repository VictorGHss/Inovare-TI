-- =============================================================================
-- Migração V28: Adicionar coluna is_new_acquisition à tabela assets
-- =============================================================================
-- Contexto: A propriedade 'isNewAcquisition' foi adicionada à entidade Asset.java
-- para identificar equipamentos adquiridos recentemente que devem constar no
-- relatório de saídas. Esta coluna estava em falta no esquema de base de dados,
-- causando um PSQLException no arranque do Hibernate (column does not exist).
--
-- Padrão seguido: idêntico à adição de is_critical na migração V19.
-- DEFAULT FALSE: consistente com o valor por omissão definido na entidade Java
-- (@Builder.Default private boolean isNewAcquisition = false).
-- NOT NULL: garante integridade de dados sem necessitar de verificações nulas
-- na camada de domínio.
-- =============================================================================

-- Verifica se a coluna já existe antes de adicionar (segurança para re-execuções manuais)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name  = 'assets'
          AND column_name = 'is_new_acquisition'
    ) THEN
        ALTER TABLE assets
            ADD COLUMN is_new_acquisition BOOLEAN NOT NULL DEFAULT FALSE;

        RAISE NOTICE 'Coluna is_new_acquisition adicionada à tabela assets com DEFAULT FALSE.';
    ELSE
        RAISE NOTICE 'Coluna is_new_acquisition já existia na tabela assets. Migração ignorada.';
    END IF;
END;
$$;
