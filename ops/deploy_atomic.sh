#!/usr/bin/env bash
set -euo pipefail

# Script simples de deploy atômico para builds estáticas geradas em `front/dist`
# Uso: ./deploy_atomic.sh ./front/dist user@host:/var/www/itsm /var/www/itsm/current
# Exemplo:
#   ./deploy_atomic.sh ./front/dist web@example.com:/var/www/itsm /var/www/itsm/current

SRC=$1
DEST=$2
CURRENT_SYMLINK=${3:-/var/www/itsm/current}

TIMESTAMP=$(date +%Y%m%d%H%M%S)
RELEASE_DIR="${DEST%/}/releases/${TIMESTAMP}"

echo "Criando release remoto: ${RELEASE_DIR}"
ssh "${DEST%%:*}" "mkdir -p '${RELEASE_DIR}'"

echo "Sincronizando arquivos para release remoto..."
rsync -avz --delete "${SRC%/}/" "${DEST%/}:${RELEASE_DIR}/"

echo "Atualizando symlink atual de forma atômica..."
ssh "${DEST%%:*}" "ln -sfn '${RELEASE_DIR}' '${CURRENT_SYMLINK}' && chown -R www-data:www-data '${RELEASE_DIR}' || true"

echo "Deploy concluído. Release ativa: ${RELEASE_DIR}"
echo "Recomenda-se invalidar CDN e reiniciar/recarregar o Nginx se necessário." 

exit 0
