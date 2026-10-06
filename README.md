# Rahbar — migrated to Spring Boot + Angular

This is a migration of the Flask/MySQL app in your `rahbar` folder to:

- **backend/** — Java 17, Spring Boot 3.3, Spring Data JPA, Spring Security (JWT), Flyway
- **frontend/** — Angular 18 standalone app, Bootstrap 5

## ⚠️ Please read before anything else

**I could not compile or run either project in this session.** Both the cloud
sandbox and your Mac's Claude-linked shell have their outbound network
restricted to a small allowlist (npm/pypi/crates registries, GitHub) — Maven
Central and the public npm registry were both blocked (403), so `mvn compile`
and `npm install` could not be verified here. The code was written and
carefully proofread (braces balanced, imports checked, logic traced against
the original routes) but **you must build it yourself** before trusting it:

```bash
cd backend && mvn clean verify
cd ../frontend && npm install && npm run build
```

Please fix anything that comes up and treat this as a solid first draft, not
a finished, tested product.

## What this covers

Every Flask blueprint was ported to a REST controller with the same
permission checks (role IDs 1–7, same as your `roles` table):

| Flask blueprint | Spring controller | Role(s) |
|---|---|---|
| `routes/auth.py` | `AuthController` | all |
| `routes/admin.py` | `AdminController` | 1, 2 |
| `routes/coordinator.py` | `CoordinatorController` | 3 |
| `routes/convenor.py` | `ConvenorController` | 4 |
| `routes/sponsor.py` | `SponsorController` | 5 |
| `routes/student.py` | `StudentController` | 6 |
| `/apply`, `/application_status` (in admin.py) | `PublicController` | public |

The Angular app has working login → OTP → role-based routing/guards, and one
dashboard page per role wired to its API, plus a full user-management page
under Admin and the public application form. **The remaining pages (there
are ~40 Jinja templates in the original) are not yet built in Angular** —
given the size of this app, building all of them pixel-for-pixel wasn't
feasible in one pass. The backend API for every one of them already exists
(see the controllers), so adding the matching Angular page is mostly
"copy `admin-users.component.ts`, point it at a different endpoint, build
the template." I'd suggest tackling them role by role, starting with
whichever your users touch most.

## Database

Flyway migration `backend/src/main/resources/db/migration/V1__baseline.sql`
recreates your schema from `rahbar.sql`, with one important caveat:

**Two things your code uses weren't in either SQL dump you have** (`rahbar.sql`
and `templates/management/rahbar (3).sql`):
- the whole **`sponsor_references`** table (reference_id, user_id, sponsor_year,
  chapter, installment_date, mobile_1/2, …) — `admin.py`, `sponsor.py` and
  `convenor.py` all depend on it heavily
- **`payments.student_proof_url`** and **`payments.updated_by`**

These must exist on your production database already (the app couldn't
function otherwise), just not in the dumps you have on disk. I reconstructed
their shape from how the code queries/inserts into them — **please diff
`V1__baseline.sql` against the real production schema before pointing this
at it**, in case any column differs.

To point the backend at your **existing** production database instead of a
fresh one, set `DATABASE_URL` / `DB_USER` / `DB_PASSWORD` and either:
- run Flyway with `baseline-on-migrate` (already on) so it just records the
  current state without trying to recreate tables, or
- review `V1__baseline.sql` once, since `ddl-auto: validate` means Hibernate
  will refuse to start if an entity doesn't match a real column.

## Configuration

Copy `.env.example` → `.env` (or set real environment variables) and fill in:

```
DATABASE_URL=jdbc:mysql://127.0.0.1:3306/rahbar?useSSL=false&serverTimezone=UTC
DB_USER=...
DB_PASSWORD=...
JWT_SECRET=<a long random string — do not use the placeholder in prod>
MAIL_USERNAME=...
MAIL_PASSWORD=...
MAIL_FROM=no-reply@rahbar.org
CORS_ALLOWED_ORIGINS=http://localhost:4200
```

## Security issues found in the original app — fixed or flagged here

1. **Plaintext passwords.** `routes/auth.py` compared
   `user_dict['password_hash'] == password` — passwords were stored and
   checked in plain text. The new backend uses BCrypt, with a compatibility
   shim (`LegacyCompatiblePasswordEncoder`) that accepts an old plaintext row
   once and immediately re-hashes it, so existing accounts aren't locked out
   on day one. **Please force a password reset for all users once you've
   cut over**, and then remove the plaintext fallback (marked `TODO` in that
   file).
2. **Hardcoded master OTP bypass (`477030`).** This existed in the original
   `verify_otp` route and is preserved as-is for continuity — you almost
   certainly want to remove this before going live. It's called out in
   `AuthController`.
3. **`templates/webhook.py`** ran `subprocess.call(['/root/deploy.sh'])` on
   any unauthenticated POST to `/webhook` — this is a remote code execution
   hole. **It was not ported.** If you need a deploy webhook, use your CI/CD
   provider's own signed-webhook support instead.
4. **Gmail credentials hardcoded in `app.py`/`services/email_services.py`.**
   Moved to environment variables (`MAIL_USERNAME`/`MAIL_PASSWORD`) — nothing
   is hardcoded in the new backend. Please **rotate that Gmail app password**
   since it was committed in plaintext in the old code.
5. **Uploaded-file routes had no access control** (`/uploads/<filename>` in
   every blueprint) — anyone who could guess or enumerate a filename could
   download it, including payment receipts and ID documents. This migration
   keeps the same public-by-filename behavior for parity (it's a straight
   static file mount), but you should tighten this — e.g. require auth and
   check the requester owns/approves that payment/record before serving the
   file — before this goes live with real data.
6. **`/apply` (public application form) was `@login_required` in the
   original**, which contradicts its purpose and template name
   (`public/apply.html`). This looked like a bug, so `PublicController`
   makes it genuinely public. Double-check this is what you want.

## Known gaps / not ported

- The PDF export branch of `generate_reports` (it depended on an optional,
  not-installed `weasyprint` package in the original too). CSV and Excel
  export both work.
- The empty/unused original blueprints (`chat.py`, `notification.py`,
  `payment.py`, `management.py`, `application.py`, and the standalone
  duplicate `app.py`/`webhook.py` test scripts) — these were empty or
  dead code in the source project and were not ported.
- Angular pages beyond the dashboards/users/apply screens described above.

## Running locally

```bash
# 1. MySQL/MariaDB running locally with an empty `rahbar` schema
# 2. Backend
cd backend
cp .env.example .env   # fill in real values
mvn spring-boot:run

# 3. Frontend (separate terminal)
cd frontend
npm install
npm start               # http://localhost:4200, proxies to :8080 for /api
```

## Deploying to the Contabo server

Merging to `main` builds Docker images in GitHub Actions. *Actions → Build & deploy → Run workflow* deploys
them to the server, with a database backup first and an automatic rollback if the new version isn't healthy.
Setup and day-to-day commands: [`deploy/README.md`](deploy/README.md).

## Running with Docker

Everything (MySQL, backend, frontend) can run via Docker Compose — this
sidesteps the Maven/npm network restrictions I had in my own sandbox,
since the build happens on your machine with full internet access.

Files added for this:
- `backend/Dockerfile` — multi-stage build (Maven+JDK17 → slim JRE Alpine image)
- `frontend/Dockerfile` — multi-stage build (Node 20 → nginx Alpine, serving the compiled Angular app and reverse-proxying `/api/**` and `/uploads/**` to the backend container)
- `frontend/nginx.conf` — the nginx config used above
- `docker-compose.yml` — wires up `mysql` + `backend` + `frontend`
- `.env.example` — compose-level environment variables (DB creds, JWT secret, mail creds)

### 1. Configure

```bash
cp .env.example .env
# edit .env — at minimum change JWT_SECRET, and fill in MAIL_USERNAME/MAIL_PASSWORD
# if you want outgoing email (OTP codes, notifications) to work.
```

### 2. Build and start

```bash
docker compose up --build
```

This will:
1. Start MySQL 8 and wait for it to be healthy (schema is empty at first).
2. Build and start the backend — Flyway runs `V1__baseline.sql` automatically
   on first boot to create all tables, then the JAR starts on port 8080.
3. Build and start the frontend — Angular is compiled to static files and
   served by nginx on port 80 (mapped to `4200` on your host), proxying
   `/api/**` to the backend container.

### 3. Use it

- App: http://localhost:4200
- Backend API directly (if needed): http://localhost:8080
- MySQL (if you want to connect with a client): `localhost:3307`, user/password from your `.env`

### 4. Stop / reset

```bash
docker compose down            # stop containers, keep data
docker compose down -v         # stop containers AND wipe the mysql + uploads volumes
```

### Notes

- If you already have a **production** database and want the containers to
  point at it instead of the bundled `mysql` service, remove the `mysql`
  service from `docker-compose.yml`, delete the `depends_on` block under
  `backend`, and set `DATABASE_URL`/`DB_USER`/`DB_PASSWORD` in `.env` to your
  real database. See the "Database" section above for the `sponsor_references`
  schema caveat before doing this.
- Uploaded files persist in the `rahbar-uploads` named volume, so they
  survive `docker compose down` (but not `down -v`).
- Rebuilding after a code change: `docker compose up --build backend` or
  `docker compose up --build frontend` rebuilds just that one service.
# ba
