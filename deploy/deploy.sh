#!/usr/bin/env bash
# Deploys one image version on the server. Called by GitHub Actions over SSH:
#   /opt/rahbar/deploy.sh <tag>
# Steps: check settings -> back up the database -> pull images -> restart -> health check.
# If the new version doesn't come up healthy, the previous version is started again.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/rahbar}"
TAG="${1:?usage: deploy.sh <image-tag>}"
COMPOSE=(docker compose -f "$APP_DIR/docker-compose.prod.yml" --env-file "$APP_DIR/.env")
STATE_FILE="$APP_DIR/.deployed-tag"
BACKUP_DIR="$APP_DIR/backups"
HEALTH_URL="http://127.0.0.1:${HEALTH_PORT:-80}/api/public/application-form-options"

cd "$APP_DIR"
log() { echo "[deploy $(date '+%F %T')] $*"; }
# Any unexpected failure says where it happened (instead of a silent exit code 1).
trap 'rc=$?; log "ERROR: step failed (exit $rc) at line $LINENO: $BASH_COMMAND"' ERR

# ---- 1. Settings: create .env on the first deploy, then sanity-check it ----
# Read single values from .env without executing it (values may contain spaces).
envval() { { grep -E "^$1=" .env 2>/dev/null || true; } | tail -n1 | cut -d= -f2- | sed -e 's/^["'"'"']//' -e 's/["'"'"']$//'; }
# Set KEY=VALUE in .env (replace the line, or add it).
setval() {
  local key="$1" value="$2" tmp
  tmp="$(mktemp)"
  grep -vE "^#?\s*$key=" .env > "$tmp" || true
  printf '%s=%s\n' "$key" "$value" >> "$tmp"
  cat "$tmp" > .env && rm -f "$tmp"
}
rand() { local s; s="$(openssl rand -base64 120 | tr -dc 'A-Za-z0-9')"; printf '%s' "${s:0:$1}"; }

if [ ! -f .env ]; then
  log "No .env yet: creating one with new random database passwords and JWT secret."
  umask 077
  SERVER_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
  cat > .env <<ENV
# Created automatically by deploy.sh on the first deploy. Safe to edit; keep it private.
DB_NAME=rahbar
DB_USER=rahbar
DB_PASSWORD=$(rand 32)
DB_ROOT_PASSWORD=$(rand 32)
JWT_SECRET=$(rand 64)
JWT_EXPIRATION_MS=28800000
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=
PUBLIC_URL=http://${SERVER_IP:-localhost}
JAVA_OPTS=-Xms256m -Xmx768m
ENV
  chmod 600 .env
fi

# Mail settings sent by GitHub Actions (from the MAIL_* repository secrets) replace the ones in .env.
if [ -f .incoming-secrets ]; then
  while IFS='=' read -r key value; do
    case "$key" in
      MAIL_USERNAME|MAIL_PASSWORD|MAIL_FROM|MAIL_HOST|MAIL_PORT) [ -n "$value" ] && setval "$key" "$value" ;;
    esac
  done < .incoming-secrets
  rm -f .incoming-secrets
  chmod 600 .env
fi
[ -n "$(envval MAIL_USERNAME)" ] || log "Note: MAIL_USERNAME / MAIL_PASSWORD are empty, so emails (sign-in codes!) can't be sent. Add the MAIL_USERNAME and MAIL_PASSWORD secrets in GitHub and deploy again."
[ -n "$(envval MAIL_FROM)" ] || { [ -n "$(envval MAIL_USERNAME)" ] && setval MAIL_FROM "$(envval MAIL_USERNAME)"; } || true
# Empty or example placeholder values are replaced with random ones. Database passwords only while the
# database has never been started (afterwards MySQL keeps the original password, so it must not change).
DB_EXISTS=no
docker volume inspect rahbar_mysql-data >/dev/null 2>&1 && DB_EXISTS=yes
for v in DB_PASSWORD DB_ROOT_PASSWORD JWT_SECRET; do
  cur="$(envval "$v")"
  if [ -z "$cur" ] || [[ "$cur" == *change* ]] || [[ "$cur" == *CHANGE* ]]; then
    if [ "$v" = JWT_SECRET ] || [ "$DB_EXISTS" = no ]; then
      log "$v was empty or a placeholder: setting a random value."
      if [ "$v" = JWT_SECRET ]; then setval "$v" "$(rand 64)"; else setval "$v" "$(rand 32)"; fi
    else
      log "ERROR: $v is empty in /opt/rahbar/.env but the database already exists. Put the original password back."; exit 1
    fi
  fi
done
chmod 600 .env
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
  ls -1t "$BACKUP_DIR"/rahbar-*-before-*.sql.gz 2>/dev/null | tail -n +21 | xargs -r rm -f || true
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
