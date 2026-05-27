#!/usr/bin/env bash
# =====================================================================
# rotate-secrets.sh — rotation JWT secret + Postgres password
#
# Usage:
#   ./scripts/rotate-secrets.sh demo
#   ./scripts/rotate-secrets.sh prod
#
# Prérequis:
#   - Lancé sur le serveur de déploiement, depuis le dossier qui contient
#     docker-compose.<env>.yml et .env.<env>
#   - docker compose v2 installé
#   - L'utilisateur a accès au socket Docker
#
# Effets:
#   1. Backup .env.<env> dans .env.<env>.backup.<timestamp>
#   2. Génère nouveau JWT_SECRET (openssl rand -hex 64)
#   3. Génère nouveau POSTGRES_PASSWORD (openssl rand, alphanumérique)
#   4. ALTER USER dans Postgres avec le nouveau password
#   5. Met à jour .env.<env>
#   6. Redémarre uniquement tracker_app (pas tracker_db)
#   7. Healthcheck après redémarrage
#
# Rollback:
#   En cas d'échec, restaure automatiquement .env.<env>.backup.<timestamp>
#   et restaure l'ancien password en BD.
#
# Effet utilisateur:
#   Tous les JWT actifs deviennent invalides → utilisateurs déconnectés,
#   doivent se reconnecter. À planifier hors heures de pointe.
# =====================================================================

set -euo pipefail

# ---------- Args ----------
ENV_NAME="${1:-}"
if [[ "$ENV_NAME" != "demo" && "$ENV_NAME" != "prod" ]]; then
  echo "Usage: $0 {demo|prod}" >&2
  exit 1
fi

ENV_FILE=".env.${ENV_NAME}"
COMPOSE_FILE="docker-compose.${ENV_NAME}.yml"
APP_CONTAINER="lst_${ENV_NAME}_app"
DB_CONTAINER="lst_${ENV_NAME}_db"
HEALTH_URL_INSIDE="http://localhost:9200/actuator/health"

# ---------- Pre-flight ----------
echo "==> Pre-flight checks ($ENV_NAME)"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: $ENV_FILE not found in $(pwd)" >&2
  exit 1
fi
if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: $COMPOSE_FILE not found in $(pwd)" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: cannot access Docker daemon. Add user to docker group :" >&2
  echo "  sudo usermod -aG docker \$USER && newgrp docker" >&2
  exit 1
fi

# Containers must be running
if ! docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
  echo "ERROR: $DB_CONTAINER is not running" >&2
  exit 1
fi
if ! docker ps --format '{{.Names}}' | grep -q "^${APP_CONTAINER}$"; then
  echo "WARN: $APP_CONTAINER is not running — will start it after rotation"
fi

# openssl required
command -v openssl >/dev/null || { echo "ERROR: openssl required" >&2; exit 1; }

# ---------- Load current values ----------
# Use a subshell to avoid polluting parent env permanently.
OLD_DB_PWD=$(grep '^POSTGRES_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
OLD_JWT=$(grep '^JWT_SECRET=' "$ENV_FILE" | cut -d= -f2-)
PG_USER=$(grep '^POSTGRES_USER=' "$ENV_FILE" | cut -d= -f2-)
PG_DB=$(grep '^POSTGRES_DB=' "$ENV_FILE" | cut -d= -f2-)

if [[ -z "$OLD_DB_PWD" || -z "$OLD_JWT" || -z "$PG_USER" || -z "$PG_DB" ]]; then
  echo "ERROR: missing required vars in $ENV_FILE (POSTGRES_PASSWORD, JWT_SECRET, POSTGRES_USER, POSTGRES_DB)" >&2
  exit 1
fi

# ---------- Backup ----------
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="${ENV_FILE}.backup.${TIMESTAMP}"
cp "$ENV_FILE" "$BACKUP_FILE"
echo "==> Backup created: $BACKUP_FILE"

# ---------- Generate new values ----------
NEW_JWT=$(openssl rand -hex 64)
# Alphanumeric only — avoids issues with shell quoting and JDBC URL parsing
NEW_DB_PWD=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)

echo "==> Generated new JWT_SECRET (128 hex chars)"
echo "==> Generated new POSTGRES_PASSWORD (32 alphanumeric chars)"

# ---------- Rollback function ----------
rollback() {
  echo ""
  echo "!!  Rotation FAILED — rolling back"
  cp "$BACKUP_FILE" "$ENV_FILE"
  echo "    Restored $ENV_FILE from $BACKUP_FILE"

  echo "    Attempting to restore DB password..."
  if docker exec -e PGPASSWORD="$NEW_DB_PWD" "$DB_CONTAINER" \
       psql -U "$PG_USER" -d "$PG_DB" \
       -c "ALTER USER \"$PG_USER\" WITH PASSWORD '$OLD_DB_PWD';" 2>/dev/null; then
    echo "    DB password restored"
  else
    # ALTER USER may have failed before being applied
    echo "    DB password rollback skipped (may not have been changed yet)"
  fi

  echo "    Restarting app with restored config..."
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d tracker_app
  exit 1
}
trap rollback ERR

# ---------- Step 1: ALTER USER in Postgres ----------
# Use the OLD password to authenticate, then change to NEW.
echo "==> Updating DB password in Postgres..."
docker exec -e PGPASSWORD="$OLD_DB_PWD" "$DB_CONTAINER" \
  psql -U "$PG_USER" -d "$PG_DB" \
  -c "ALTER USER \"$PG_USER\" WITH PASSWORD '$NEW_DB_PWD';" \
  >/dev/null
echo "    DB password updated"

# ---------- Step 2: Update .env file ----------
echo "==> Updating $ENV_FILE..."
# Use a temp file for atomic replacement.
TMP=$(mktemp)
# sed delimiter "|" because new values may contain "/" or "+"
sed -E \
  -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=${NEW_DB_PWD}|" \
  -e "s|^JWT_SECRET=.*|JWT_SECRET=${NEW_JWT}|" \
  "$ENV_FILE" > "$TMP"
mv "$TMP" "$ENV_FILE"
echo "    $ENV_FILE updated"

# Sanity check: confirm new values are present
if ! grep -q "^POSTGRES_PASSWORD=${NEW_DB_PWD}$" "$ENV_FILE"; then
  echo "ERROR: $ENV_FILE not updated correctly (POSTGRES_PASSWORD mismatch)" >&2
  exit 1
fi

# ---------- Step 3: Restart app (not DB) ----------
echo "==> Restarting $APP_CONTAINER..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d tracker_app
echo "    App restarted"

# ---------- Step 4: Healthcheck ----------
echo "==> Waiting for app to be healthy (max 90s)..."
HEALTHY=0
for i in $(seq 1 18); do
  sleep 5
  if docker exec "$APP_CONTAINER" curl -fsS "$HEALTH_URL_INSIDE" >/dev/null 2>&1; then
    HEALTHY=1
    echo "    Healthy after $((i*5))s"
    break
  fi
  echo "    ... still waiting ($((i*5))s)"
done

if [[ "$HEALTHY" -ne 1 ]]; then
  echo "ERROR: app did not become healthy in 90s" >&2
  echo "Last 30 log lines:" >&2
  docker logs "$APP_CONTAINER" --tail 30 >&2
  exit 1
fi

# Disarm rollback trap — we succeeded.
trap - ERR

# ---------- Done ----------
echo ""
echo "================================================================"
echo "  Rotation successful for env: $ENV_NAME"
echo "  Backup: $BACKUP_FILE"
echo ""
echo "  IMPORTANT:"
echo "  - All active JWTs are now invalid."
echo "  - Users will be logged out and must reconnect."
echo "  - Keep $BACKUP_FILE for ~1 week then delete:"
echo "      rm $BACKUP_FILE"
echo "================================================================"
