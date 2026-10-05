# Deploying Rahbar to Contabo

How it works:

1. **Merge a PR into `main`.** GitHub Actions builds the backend and frontend Docker images and stores
   them in GitHub Container Registry, tagged with the commit SHA. Nothing changes on the server yet.
2. **Click Deploy.** Go to *Actions → Build & deploy → Run workflow* and leave *tag* empty. GitHub signs in to the
   server over SSH and runs `deploy.sh`, which:
   - backs up the database,
   - pulls the new images and restarts the containers,
   - checks the site answers,
   - **starts the previous version again if the new one doesn't come up.**
3. **Roll back** any time: *Run workflow* with *tag* set to an older commit SHA (from the commit list or
   an earlier run). That version is deployed without rebuilding.

The server only runs Docker: MySQL, the backend, the frontend (nginx) and, once you have a domain, Caddy
for HTTPS. Nothing is compiled there.

---

## One-time setup

### 1. Prepare the server (as root, once)

```bash
ssh root@YOUR_SERVER_IP
curl -fsSL https://raw.githubusercontent.com/amkhan786000/ba/main/deploy/server-setup.sh -o server-setup.sh
bash server-setup.sh
```

This installs Docker, creates a `deploy` user, turns on the firewall (SSH, 80, 443), fail2ban and automatic
security updates, prepares `/opt/rahbar`, and schedules a nightly database backup at 02:30 (kept 14 days).

### 2. Create the SSH key GitHub uses (on your laptop)

```bash
ssh-keygen -t ed25519 -f ~/.ssh/rahbar_deploy -N "" -C "github-actions-rahbar"
ssh-copy-id -i ~/.ssh/rahbar_deploy.pub deploy@YOUR_SERVER_IP   # or paste the .pub line into /home/deploy/.ssh/authorized_keys
ssh -i ~/.ssh/rahbar_deploy deploy@YOUR_SERVER_IP 'docker ps'      # should print an empty table
```

### 3. Server settings: `/opt/rahbar/.env`

```bash
ssh deploy@YOUR_SERVER_IP
cd /opt/rahbar
curl -fsSL https://raw.githubusercontent.com/amkhan786000/ba/main/deploy/.env.production.example -o .env
chmod 600 .env
nano .env
```

Fill in **new** values. Don't reuse the passwords from your laptop, because older ones are visible in the
public repository's history.

- `openssl rand -base64 32`: use it for `DB_PASSWORD` and `DB_ROOT_PASSWORD`.
- `openssl rand -base64 48`: use it for `JWT_SECRET`.
- `MAIL_*`: the Gmail address and its 16-letter app password.
- `PUBLIC_URL=http://YOUR_SERVER_IP`.

### 4. GitHub secrets

In the repository: *Settings → Secrets and variables → Actions → New repository secret*:

| Secret | Value |
| --- | --- |
| `CONTABO_HOST` | the server IP |
| `CONTABO_USER` | `deploy` |
| `CONTABO_SSH_KEY` | the whole content of `~/.ssh/rahbar_deploy` (the private key, including the BEGIN/END lines) |
| `CONTABO_SSH_PORT` | only if SSH isn't on port 22 |

Optional: under *Settings → Environments → production* you can require an approval before each deploy.

### 5. First deploy

*Actions → Build & deploy → Run workflow*. When it's green, open `http://YOUR_SERVER_IP`.

The first start creates an empty database. To bring over existing data, restore a dump once:

```bash
gunzip -c rahbar.sql.gz | docker exec -i rahbar-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

---

## HTTPS later (when a domain points to the server)

1. Create a DNS `A` record for your domain pointing to the server IP.
2. Add to `/opt/rahbar/.env`:
   ```
   DOMAIN=rahbar.example.org
   HTTP_BIND=127.0.0.1:8080
   COMPOSE_PROFILES=https
   PUBLIC_URL=https://rahbar.example.org
   ```
3. Run the deploy again. Caddy gets and renews the Let's Encrypt certificate by itself.

## Everyday commands on the server (`ssh deploy@YOUR_SERVER_IP`, then `cd /opt/rahbar`)

| What | Command |
| --- | --- |
| Status | `docker compose -f docker-compose.prod.yml ps` |
| Backend log | `docker logs -f --tail 200 rahbar-backend` |
| Running version | `cat .deployed-tag` |
| Backups | `ls -lh backups/` |
| Restart everything | `docker compose -f docker-compose.prod.yml --env-file .env restart` |

Database upgrade scripts (`backend/src/main/resources/db/migration/V*.sql`) are not run automatically, and
the app adds new tables and columns itself on start. If a release notes a script, run it once after the deploy:

```bash
docker exec -i rahbar-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < V6__platform_features.sql
```
