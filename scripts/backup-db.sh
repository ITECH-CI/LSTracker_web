#!/usr/bin/env bash
# =====================================================================
# backup-db.sh — backup Postgres compressé d'un env LSTracker
#
# Usage:
#   ./scripts/backup-db.sh demo
#   ./scripts/backup-db.sh prod
#
# Comportement:
#   - pg_dump dans /opt/lstracker/backups/<env>/lstracker-<env>-<timestamp>.sql.gz
#   - garde les 30 derniers backups par env (configurable via RETENTION_DAYS)
#   - écrit un statut dans /opt/lstracker/logs/backup.log
#
# À planifier via cron:
#   0 3 * * * /opt/lstracker/scripts/backup-db.sh prod
# =====================================================================

set -euo pipefail

ENV_NAME="${1:-}"
if [[ "$ENV_NAME" != "demo" && "$ENV_NAME" != "prod" ]]; then
  echo "Usage: $0 {demo|prod}" >&2
  exit 1
fi

RETENTION_DAYS="${RETENTION_DAYS:-30}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/lstracker/backups}"
# APP_ROOT = dossier contenant .env.<env> et docker-compose.<env>.yml.
# Défaut "." = CWD (cas typique : on lance depuis le dossier du bundle extrait).
APP_ROOT="${APP_ROOT:-.}"

DB_CONTAINER="lst_${ENV_NAME}_db"
ENV_FILE="${APP_ROOT}/.env.${ENV_NAME}"
BACKUP_DIR="${BACKUP_ROOT}/${ENV_NAME}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/lstracker-${ENV_NAME}-${TIMESTAMP}.sql.gz"

# Pre-flight
[[ -f "$ENV_FILE" ]] || { echo "ERROR: $ENV_FILE not found" >&2; exit 1; }
mkdir -p "$BACKUP_DIR"

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: cannot access Docker daemon. Add user to docker group :" >&2
  echo "  sudo usermod -aG docker \$USER && newgrp docker" >&2
  exit 1
fi

# Load DB creds
POSTGRES_USER=$(grep '^POSTGRES_USER=' "$ENV_FILE" | cut -d= -f2-)
POSTGRES_DB=$(grep '^POSTGRES_DB=' "$ENV_FILE" | cut -d= -f2-)
POSTGRES_PASSWORD=$(grep '^POSTGRES_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)

# Verify container is up
if ! docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
  echo "[$(date -Iseconds)] ERROR: $DB_CONTAINER not running" >&2
  exit 1
fi

echo "[$(date -Iseconds)] Backing up $ENV_NAME → $BACKUP_FILE"

# Dump + gzip in one pipe (no tempfile).
# Flags choisis pour produire un dump PORTABLE entre environnements :
#   --no-owner       : pas de "ALTER ... OWNER TO appuser" (le user diffère par env)
#   --no-privileges  : pas de "GRANT ... TO appuser" (idem)
#   --clean --if-exists : DROP TABLE IF EXISTS avant CREATE → re-restore idempotent
docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$DB_CONTAINER" \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    --no-owner --no-privileges --clean --if-exists \
  | gzip -9 > "$BACKUP_FILE"

# Verify backup is non-empty
if [[ ! -s "$BACKUP_FILE" ]]; then
  echo "[$(date -Iseconds)] ERROR: backup file is empty, deleting" >&2
  rm -f "$BACKUP_FILE"
  exit 1
fi

SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "[$(date -Iseconds)] OK: $BACKUP_FILE ($SIZE)"

# Retention: delete backups older than RETENTION_DAYS
find "$BACKUP_DIR" -name "lstracker-${ENV_NAME}-*.sql.gz" -mtime "+${RETENTION_DAYS}" -delete
REMAINING=$(find "$BACKUP_DIR" -name "lstracker-${ENV_NAME}-*.sql.gz" | wc -l)
echo "[$(date -Iseconds)] Retention: kept $REMAINING backups (deleted > ${RETENTION_DAYS}d)"
