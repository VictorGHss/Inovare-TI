#!/usr/bin/env bash
# ==============================================================================
# INOVARE-TI: SCRIPT DE LIMPEZA E RETENÇÃO DE FICHEIROS NO HDD SECUNDÁRIO (/mnt/data)
# ==============================================================================
set -euo pipefail

# Determinar a pasta do script e carregar as variáveis de ambiente a partir do ficheiro .env
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../.env"

if [ -f "$ENV_FILE" ]; then
    # Exporta as variáveis omitindo as linhas comentadas
    export $(grep -v '^#' "$ENV_FILE" | xargs)
else
    echo "Ficheiro .env não encontrado em ${ENV_FILE}. Certifique-se de que o script está na localização correta."
    exit 1
fi

BACKUPS_DIR="/mnt/data/backups"
UPLOADS_DIR="/mnt/data/uploads"

echo "=== Início da rotina de limpeza do HDD secundário ($(date)) ==="

# ------------------------------------------------------------------------------
# REGRA A: Limpeza de ficheiros de backup (.tar.gz, .sql ou .zip) com mais de 30 dias
# ------------------------------------------------------------------------------
if [ -d "$BACKUPS_DIR" ]; then
    echo "A analisar diretório de backups: $BACKUPS_DIR"
    find "$BACKUPS_DIR" -type f \( -name "*.tar.gz" -o -name "*.sql" -o -name "*.zip" \) -mtime +30 | while read -r ficheiro; do
        dias=$(( ( $(date +%s) - $(date -r "$ficheiro" +%s) ) / 86400 ))
        echo "A eliminar ficheiro de backup: $(basename "$ficheiro") com $dias dias de antiguidade..."
        rm -f "$ficheiro"
    done
else
    echo "Diretório de backups $BACKUPS_DIR não existe. Ignorado."
fi

# ------------------------------------------------------------------------------
# REGRA B: Limpeza de ficheiros em uploads com mais de 90 dias não referenciados na BD
# ------------------------------------------------------------------------------
if [ -d "$UPLOADS_DIR" ]; then
    echo "A analisar diretório de uploads: $UPLOADS_DIR"
    DB_REF_FILE="/tmp/db_references.txt"

    # Executa a query de forma segura no container docker inovareti_db
    # e exporta as referências para um ficheiro de texto temporário
    docker exec -i inovareti_db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -c "
    SELECT COALESCE(invoice_file_path, '') FROM assets WHERE invoice_file_path IS NOT NULL
    UNION
    SELECT COALESCE(invoice_file_path, '') FROM stock_batches WHERE invoice_file_path IS NOT NULL
    UNION
    SELECT COALESCE(stored_filename, '') FROM ticket_attachments WHERE stored_filename IS NOT NULL
    UNION
    SELECT COALESCE(content, '') FROM articles WHERE content IS NOT NULL;
    " > "$DB_REF_FILE" 2>/dev/null || true

    # Guarda de segurança: se o ficheiro de referências estiver vazio, não efetuamos a remoção cega
    if [ ! -s "$DB_REF_FILE" ]; then
        echo "AVISO: Não foi possível ler referências da base de dados ou a lista está vazia. Limpeza de uploads abortada por segurança."
    else
        # Procura ficheiros em uploads criados há mais de 90 dias
        find "$UPLOADS_DIR" -type f -mtime +90 | while read -r ficheiro; do
            nome_base=$(basename "$ficheiro")
            # Se o nome básico do ficheiro não estiver contido nas referências da base de dados, remove
            if ! grep -q "$nome_base" "$DB_REF_FILE"; then
                dias=$(( ( $(date +%s) - $(date -r "$ficheiro" +%s) ) / 86400 ))
                echo "A eliminar ficheiro de upload não referenciado: $(basename "$ficheiro") com $dias dias de antiguidade..."
                rm -f "$ficheiro"
            fi
        done
    fi

    # Limpeza do ficheiro temporário
    rm -f "$DB_REF_FILE"
else
    echo "Diretório de uploads $UPLOADS_DIR não existe. Ignorado."
fi

# ------------------------------------------------------------------------------
# VALIDAÇÃO: Alerta de espaço em disco inferior a 10% livre
# ------------------------------------------------------------------------------
if [ -d "/mnt/data" ]; then
    # Obtém a percentagem de uso do volume /mnt/data
    USO_DISCO=$(df /mnt/data --output=pcent | tail -n 1 | tr -d ' %')

    if [ "$USO_DISCO" -ge 90 ]; then
        echo "ALERTA CRÍTICO: O espaço livre no HDD secundário (/mnt/data) caiu abaixo de 10%! Uso atual: ${USO_DISCO}%."
        
        # Envia a notificação para o canal do Discord através do webhook configurado no .env
        if [ -n "${DISCORD_WEBHOOK_URL:-}" ]; then
            payload="{\"content\": \"⚠️ **ALERTA CRÍTICO DE ARMAZENAMENTO** ⚠️\\nO espaço livre no HDD secundário de 500GB (**/mnt/data**) está abaixo de 10%!\\n**Uso atual:** ${USO_DISCO}%\\nPor favor, realize a limpeza manual de ficheiros desnecessários ou aumente o volume.\"}"
            curl -s -H "Content-Type: application/json" -X POST -d "$payload" "$DISCORD_WEBHOOK_URL" > /dev/null || true
        fi
    fi
else
    echo "Ponto de montagem /mnt/data não localizado para validação de espaço."
fi

echo "=== Fim da rotina de limpeza do HDD secundário ==="
exit 0
