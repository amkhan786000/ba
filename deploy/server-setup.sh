#!/usr/bin/env bash
# One-time setup of a fresh Contabo VPS (Ubuntu 22.04 / 24.04 or Debian 12). Run as root:
#   curl -fsSL https://raw.githubusercontent.com/amkhan786000/ba/main/deploy/server-setup.sh | bash
# or copy this file to the server and run:  sudo bash server-setup.sh
#
# It installs Docker, creates a "deploy" user that GitHub Actions signs in as, opens the firewall
# for SSH / HTTP / HTTPS, prepares /opt/rahbar and schedules a nightly database backup.
set -euo pipefail

APP_DIR=/opt/rahbar
DEPLOY_USER=deploy

[ "$(id -u)" -eq 0 ] || { echo "Run as root (sudo bash server-setup.sh)"; exit 1; }

echo "==> Updating packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get upgrade -y
apt-get install -y ca-certificates curl gnupg ufw fail2ban unattended-upgrades

echo "==> Installing Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

echo "==> Creating the $DEPLOY_USER user"
if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "Rahbar deploy" "$DEPLOY_USER"
fi
usermod -aG docker "$DEPLOY_USER"
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"
touch "/home/$DEPLOY_USER/.ssh/authorized_keys"
chmod 600 "/home/$DEPLOY_USER/.ssh/authorized_keys"
chown "$DEPLOY_USER:$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh/authorized_keys"

echo "==> Preparing $APP_DIR"
install -d -m 750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR" "$APP_DIR/backups"

echo "==> Firewall: allow SSH, HTTP, HTTPS"
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
systemctl enable --now fail2ban

echo "==> Automatic security updates"
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> Nightly database backup at 02:30 (keeps 14 days)"
cat > /etc/cron.d/rahbar-backup <<'CRON'
30 2 * * * deploy docker ps --format '{{.Names}}' | grep -qx rahbar-mysql && docker exec rahbar-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers "$MYSQL_DATABASE"' | gzip > /opt/rahbar/backups/nightly-$(date +\%F).sql.gz && find /opt/rahbar/backups -name 'nightly-*.sql.gz' -mtime +14 -delete
CRON
chmod 644 /etc/cron.d/rahbar-backup

cat <<NEXT

Server is ready. Next steps (details in deploy/README.md):
  1. Add the GitHub Actions public key to /home/$DEPLOY_USER/.ssh/authorized_keys
  2. Create $APP_DIR/.env from deploy/.env.production.example (chmod 600, owner $DEPLOY_USER)
  3. Add the CONTABO_HOST / CONTABO_USER / CONTABO_SSH_KEY secrets in GitHub and click "Run workflow"
NEXT
