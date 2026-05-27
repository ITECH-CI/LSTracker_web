#!/usr/bin/env bash
# =====================================================================
# restore-db.sh — restaure un dump dans la DB d'un env LSTracker
#
# Usage:
#   ./scripts/restore-db.sh demo /opt/lstracker/backups/demo/lstracker-demo-20250612-030000.sql.gz
#
# ATTENTION: écrase complètement la DB cible. Demande confirmation.
# =====================================================================

set -euo pipefail

ENV_NAME="${1:-}"
BACKUP_FILE="${2:-}"

if [[ "$ENV_NAME" != "demo" && "$ENV_NAME" != "prod" ]] || [[ -z "$BACKUP_FILE" ]]; then
  echo "Usage: $0 {demo|prod} <backup-file.sql.gz>" >&2
  exit 1
fi
[[ -f "$BACKUP_FILE" ]] || { echo "ERROR: $BACKUP_FILE not found" >&2; exit 1; }

# Vérifier l'accès au socket Docker (cause cryptique sinon : "permission denied
# while trying to connect to the Docker daemon socket").
if ! docker info >/dev/null 2>&1; then
  echo "ERROR: cannot access Docker daemon. Options :" >&2
  echo "  - Add your user to the docker group :  sudo usermod -aG docker \$USER && newgrp docker" >&2
  echo "  - Or run with sudo :  sudo APP_ROOT=. $0 $*" >&2
  exit 1
fi

# APP_ROOT = dossier contenant .env.<env> et docker-compose.<env>.yml.
# Défaut "." = CWD (cas typique : on lance depuis le dossier du bundle extrait).
# Override si besoin : APP_ROOT=/chemin/autre ./scripts/restore-db.sh ...
APP_ROOT="${APP_ROOT:-.}"
DB_CONTAINER="lst_${ENV_NAME}_db"
ENV_FILE="${APP_ROOT}/.env.${ENV_NAME}"

POSTGRES_USER=$(grep '^POSTGRES_USER=' "$ENV_FILE" | cut -d= -f2-)
POSTGRES_DB=$(grep '^POSTGRES_DB=' "$ENV_FILE" | cut -d= -f2-)
POSTGRES_PASSWORD=$(grep '^POSTGRES_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)

echo "================================================================"
echo "  RESTORE — env: $ENV_NAME"
echo "  Source : $BACKUP_FILE"
echo "  Target : $POSTGRES_DB on $DB_CONTAINER"
echo ""
echo "  This will OVERWRITE the current DB content."
echo "================================================================"
read -rp "Type 'restore $ENV_NAME' to confirm: " CONFIRM
if [[ "$CONFIRM" != "restore $ENV_NAME" ]]; then
  echo "Aborted."
  exit 1
fi

echo "Restoring..."
gunzip -c "$BACKUP_FILE" | \
  docker exec -i -e PGPASSWORD="$POSTGRES_PASSWORD" "$DB_CONTAINER" \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    --quiet --single-transaction --set ON_ERROR_STOP=1

echo "Done. Restart the app to refresh any cached state:"
echo "  docker compose --env-file $ENV_FILE -f docker-compose.${ENV_NAME}.yml up -d tracker_app"
