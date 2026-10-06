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

echo "==> Prefer IPv4 for outgoing connections (Contabo IPv6 to GitHub can reset image downloads)"
grep -qE '^precedence ::ffff:0:0/96 +100' /etc/gai.conf 2>/dev/null || echo 'precedence ::ffff:0:0/96  100' >> /etc/gai.conf

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

echo "==> Freeing port 80/443 (stopping a preinstalled web server, if any)"
for svc in apache2 nginx httpd lighttpd caddy; do
  if systemctl list-unit-files "$svc.service" >/dev/null 2>&1 && systemctl is-enabled --quiet "$svc" 2>/dev/null; then
    systemctl disable --now "$svc" && echo "    stopped $svc"
  fi
done

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

echo "==> SSH key for GitHub Actions"
KEY_FILE=/root/rahbar_deploy_key
if [ ! -f "$KEY_FILE" ]; then
  ssh-keygen -q -t ed25519 -N "" -C "github-actions-rahbar" -f "$KEY_FILE"
fi
grep -qF "$(cat "$KEY_FILE.pub")" "/home/$DEPLOY_USER/.ssh/authorized_keys" \
  || cat "$KEY_FILE.pub" >> "/home/$DEPLOY_USER/.ssh/authorized_keys"
SERVER_IP="$(curl -fsS4 https://ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')"
SSH_PORT="$(sshd -T 2>/dev/null | awk '/^port /{print $2; exit}')"

cat <<NEXT

==================================================================================
 Server is ready. Now add these in GitHub:
 github.com/amkhan786000/ba -> Settings -> Secrets and variables -> Actions
 -> New repository secret (one per row):

   CONTABO_HOST     = ${SERVER_IP}
   CONTABO_USER     = ${DEPLOY_USER}
   CONTABO_SSH_KEY  = everything between the two lines below, including BEGIN/END
NEXT
if [ -n "$SSH_PORT" ] && [ "$SSH_PORT" != 22 ]; then echo "   CONTABO_SSH_PORT = ${SSH_PORT}"; fi
echo "----------------------------------------------------------------------------------"
cat "$KEY_FILE"
echo "----------------------------------------------------------------------------------"
cat <<NEXT
 Optional, for e-mails: MAIL_USERNAME (Gmail address), MAIL_PASSWORD (app password)

 Then: Actions -> Build & deploy -> Run workflow.
 (Show the key again any time with: cat ${KEY_FILE})
==================================================================================
NEXT
