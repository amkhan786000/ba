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
curl -fsSL https://raw.githubusercontent.com/amkhan786000/ba/main/deploy/server-setup.sh | bash
```

This installs Docker, creates a `deploy` user, turns on the firewall (SSH, 80, 443), fail2ban and automatic
security updates, prepares `/opt/rahbar`, schedules a nightly database backup at 02:30 (kept 14 days), and
**creates the SSH key GitHub uses**. At the end it prints exactly what to paste into the GitHub secrets.

### 2. (Nothing to do: the key is made by step 1)

### 3. Server settings: nothing to do

The first deploy creates `/opt/rahbar/.env` by itself, with new random database passwords and JWT secret
and `PUBLIC_URL=http://<server IP>`. Mail settings come from the GitHub secrets below. You can still edit
`.env` on the server later; deploys never overwrite your values (only the `MAIL_*` lines, when those secrets are set).

### 4. GitHub secrets

In the repository: *Settings → Secrets and variables → Actions → New repository secret*:

| Secret | Value |
| --- | --- |
| `CONTABO_HOST` | the server IP |
| `CONTABO_USER` | `deploy` |
| `CONTABO_SSH_KEY` | the private key printed by step 1 (including the BEGIN/END lines) |
| `CONTABO_SSH_PORT` | only if SSH isn't on port 22 |
| `MAIL_USERNAME` | optional: the Gmail address that sends e-mails (OTP codes, notifications) |
| `MAIL_PASSWORD` | optional: its 16-letter Google app password |
| `MAIL_FROM` | optional: sender address, defaults to `MAIL_USERNAME` |

If a deploy fails, the reason is shown at the top of the run page under *Annotations*.

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
