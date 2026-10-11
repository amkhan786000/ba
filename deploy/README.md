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
| `ADMIN_EMAIL` | optional: who becomes the first Super Admin on an empty database, defaults to `MAIL_USERNAME` |

If a deploy fails, the reason is shown at the top of the run page under *Annotations*.

Optional: under *Settings → Environments → production* you can require an approval before each deploy.

### 5. First deploy

*Actions → Build & deploy → Run workflow*. When it's green, open `http://YOUR_SERVER_IP`.

The first start creates the database with the built-in roles and a **first Super Admin** for `ADMIN_EMAIL`
(or `MAIL_USERNAME`). Its temporary password is e-mailed to that address and also printed once in the backend log:

```bash
docker logs rahbar-backend 2>&1 | grep "first Super Admin"
```

Sign in with it; you then get a one-time code by e-mail and must choose a new password. This only happens while
there is no Super Admin, so it never touches a database that is in use.

**Start over with a brand-new, empty database** (deletes all data on the server!):

```bash
cd /opt/rahbar
docker compose -f docker-compose.prod.yml --env-file .env down
docker volume rm rahbar_mysql-data
```

then *Run workflow* again.

To bring over existing data instead, restore a dump once:

```bash
gunzip -c rahbar.sql.gz | docker exec -i rahbar-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

---

## Being told when something breaks

**Error emails (built in).** When Rahbar hits an unexpected error, users see "Something went wrong ... (reference
K7Q2ZP)" and the people below get an email listing what failed, how often and the reference (at most one email
every 15 minutes; nothing when all is well). Background jobs that crash (reminders, scheduled broadcasts) are
reported the same way. Search the backend log for the reference to see the full details:
`docker compose -f docker-compose.prod.yml logs backend | grep K7Q2ZP`.

Who gets them: every active Super Admin with a real email address, or, to choose, add a line to `/opt/rahbar/.env`
and redeploy (or `docker compose -f docker-compose.prod.yml --env-file .env up -d`):

```
ALERT_EMAILS=you@example.com,colleague@example.com
```

Emails need the `MAIL_*` settings (step 4); without them the errors are only in the log.

**Uptime check (free, outside the server).** Errors can't be emailed when the whole server is down, so let an outside
service watch it. With [UptimeRobot](https://uptimerobot.com) (free plan, checks every 5 minutes):

1. Sign up and add your email as an alert contact.
2. *Add New Monitor* → type **Keyword**, URL `http://YOUR_SERVER_IP/api/public/health` (or `https://your-domain/...`),
   keyword **UP**, alert when the keyword is **not** found. This is red when the backend or the database is down
   (`/api/public/health` answers `{"status":"UP"}` only when the database answers too).
3. *Add New Monitor* → type **HTTP(s)**, URL `http://YOUR_SERVER_IP:8090/` for the Bihar Anjuman website.

You get an email when either goes down and when it is back.

## HTTPS and the website's domain

With `COMPOSE_PROFILES=https`, Caddy becomes the server's front door on ports 80 and 443 (see `deploy/Caddyfile`):

| Opened as | Goes to |
| --- | --- |
| `http://YOUR_SERVER_IP` (any name not listed below) | Rahbar, plain HTTP as before |
| `https://biharanjuman.com`, `https://www.biharanjuman.com` | the Bihar Anjuman website (`biharanjuman-frontend`) |
| `https://$DOMAIN` (optional, Rahbar's own domain) | Rahbar |

Caddy gets and renews the Let's Encrypt certificates by itself.

1. Add to `/opt/rahbar/.env`:
   ```
   COMPOSE_PROFILES=https
   HTTP_BIND=127.0.0.1:8081
   # optional, only when Rahbar gets its own domain:
   # DOMAIN=rahbar.example.org
   # PUBLIC_URL=https://rahbar.example.org
   # WEBSITE_DOMAINS=biharanjuman.com, www.biharanjuman.com   (this is the default)
   ```
2. Open the HTTPS port: `sudo ufw allow 443/tcp`.
3. Run the deploy again (Actions → Deploy Latest Tag). Rahbar keeps working on `http://YOUR_SERVER_IP`.
4. The website joins Caddy's network and its DNS is switched over: see "Domain and HTTPS" in the biharanjuman.com
   repository's `deploy/README.md`.

Until a domain's DNS points to this server, Caddy just retries its certificate in the background; nothing breaks.

## Looking at the database with DBeaver (or another client)

MySQL is only open on the server itself, at `127.0.0.1:3307`. Connect through SSH:

- **SSH tab**: *Use SSH Tunnel*, host = server IP, your SSH user and password / key.
- **Main tab**: host `localhost`, port `3307`, database = `DB_NAME` from `.env`, user = `DB_USER`, password = `DB_PASSWORD`
  (or `root` with `DB_ROOT_PASSWORD`). See them with `grep '^DB_' /opt/rahbar/.env`.

## Everyday commands on the server (`ssh deploy@YOUR_SERVER_IP`, then `cd /opt/rahbar`)

| What | Command |
| --- | --- |
| Status | `docker compose -f docker-compose.prod.yml ps` |
| Backend log | `docker logs -f --tail 200 rahbar-backend` |
| Running version | `cat .deployed-tag` |
| Backups | `ls -lh backups/` |
| Restart everything | `docker compose -f docker-compose.prod.yml --env-file .env restart` |

Database upgrade scripts (`backend/src/main/resources/db/migration/V*.sql`) **run automatically** when the
backend starts (Flyway), before the app touches the schema, each one only once. Nothing has to be run by hand:

- A new, empty database gets every script in order (V1, V2, ...), i.e. the full schema with all foreign keys.
- A database that existed before Flyway was added (no `flyway_schema_history` table yet) is marked as being
  at version 7 and gets V8 onwards. Those scripts are safe to repeat, so it does not matter whether some of
  them were already run by hand. Override with `FLYWAY_BASELINE_VERSION` only if a database is older than V7.
- If a script fails, the backend does not start; `docker logs rahbar-backend` shows the script, line and
  MySQL error. The deploy script takes a database backup before every deploy (`backups/`).

Rows that point at users that don't exist are copied into `orphans_<table>_<column>` tables before they are
removed (required links) or cleared (optional links), so nothing is lost. Never edit a script that has already
been released: add a new `V<next>__description.sql` instead (Flyway refuses to start if a released one changes).
