#!/usr/bin/env bash
# Deploys one image version on the server. Called by GitHub Actions over SSH:
#   /opt/rahbar/deploy.sh <tag>
# Steps: check settings -> back up the database -> pull images -> restart -> health check.
# If the new version doesn't come up healthy, the previous version is started again.
set -euo pipefail

APP_DIR=/opt/rahbar
TAG="${1:?usage: deploy.sh <image-tag>}"
COMPOSE=(docker compose -f "$APP_DIR/docker-compose.prod.yml" --env-file "$APP_DIR/.env")
STATE_FILE="$APP_DIR/.deployed-tag"
BACKUP_DIR="$APP_DIR/backups"
HEALTH_URL="http://127.0.0.1:${HEALTH_PORT:-80}/api/public/application-form-options"

cd "$APP_DIR"
log() { echo "[deploy $(date '+%F %T')] $*"; }

# ---- 1. Settings sanity checks ----
[ -f .env ] || { log "Missing $APP_DIR/.env (copy .env.production.example and fill it in)."; exit 1; }
# Read single values from .env without executing it (values may contain spaces).
envval() { grep -E "^$1=" .env | tail -n1 | cut -d= -f2- | sed -e 's/^["'"'"']//' -e 's/["'"'"']$//'; }
for v in DB_PASSWORD DB_ROOT_PASSWORD JWT_SECRET; do
  [ -n "$(envval "$v")" ] || { log "$v is empty in .env"; exit 1; }
done
JWT="$(envval JWT_SECRET)"
if [ "${#JWT}" -lt 32 ] || [[ "$JWT" == *change-this* ]]; then
  log "JWT_SECRET must be a long random value (openssl rand -base64 48)."; exit 1
fi
HTTP_BIND="$(envval HTTP_BIND)"
if [ -n "$HTTP_BIND" ] && [ "$HTTP_BIND" != "80" ]; then
  HEALTH_URL="http://${HTTP_BIND}/api/public/application-form-options"
fi

PREVIOUS_TAG="$(cat "$STATE_FILE" 2>/dev/null || true)"
log "Deploying $TAG (currently running: ${PREVIOUS_TAG:-nothing})"

# ---- 2. Database backup (only when MySQL is already running) ----
mkdir -p "$BACKUP_DIR"
if docker ps --format '{{.Names}}' | grep -qx rahbar-mysql; then
  FILE="$BACKUP_DIR/rahbar-$(date +%Y%m%d-%H%M%S)-before-${TAG:0:12}.sql.gz"
  log "Backing up the database to $FILE"
  docker exec rahbar-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
    | gzip > "$FILE"
  # Keep the 20 most recent pre-deploy backups.
  ls -1t "$BACKUP_DIR"/rahbar-*-before-*.sql.gz 2>/dev/null | tail -n +21 | xargs -r rm -f
fi

# ---- 3. Pull and start the new version ----
export TAG
log "Pulling images"
"${COMPOSE[@]}" pull --quiet backend frontend
log "Starting containers"
"${COMPOSE[@]}" up -d --remove-orphans

# ---- 4. Health check (the backend can take a minute to start) ----
healthy=false
for i in $(seq 1 36); do
  if curl -fsS -o /dev/null --max-time 5 "$HEALTH_URL"; then healthy=true; break; fi
  sleep 5
done

if $healthy; then
  echo "$TAG" > "$STATE_FILE"
  log "Version $TAG is up and healthy."
  docker image prune -af --filter "until=168h" >/dev/null 2>&1 || true
  exit 0
fi

# ---- 5. Roll back ----
log "Health check failed. Recent backend log:"
docker logs --tail 80 rahbar-backend 2>&1 || true
if [ -n "$PREVIOUS_TAG" ] && [ "$PREVIOUS_TAG" != "$TAG" ]; then
  log "Rolling back to $PREVIOUS_TAG"
  export TAG="$PREVIOUS_TAG"
  "${COMPOSE[@]}" up -d --remove-orphans
fi
exit 1
