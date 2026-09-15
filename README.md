# IELTS SmartPrep

An IELTS practice platform covering **Listening, Reading and Writing** — timed practice,
full mock tests, AI-assisted writing evaluation, and progress tracking.

> **Speaking is deliberately out of scope.** The platform is a three-skill product. A
> Speaking module would need audio recording, storage and a separate grading pipeline;
> shipping three skills that work well was chosen over four that work partly.

---

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite, TailwindCSS, React Router 7, TanStack Query |
| Backend | Java 17, Spring Boot 3.2.5, Spring Security, JPA/Hibernate 6.4 |
| Database | MySQL 8.0, Flyway migrations (`backend/src/main/resources/db/migration`) |
| Cache / rate limiting | Redis (Bucket4j, distributed) |
| Object storage | MinIO (S3-compatible) — listening audio, avatars |
| Text-to-speech | edge-tts, as a Python FastAPI sidecar |
| AI | Google Gemini (`gemini-2.5-flash`), behind Resilience4j retry + circuit breaker |
| Observability | Sentry (both tiers), structured JSON logs with trace IDs |

---

## Running it

### Prerequisites

Only **Docker** and **Docker Compose**. Everything else — the JDK, Maven, Node — runs
inside containers. A JDK is needed only if you want to run the backend outside Docker.

### 1. Create your environment file

```bash
cp .env.example .env
```

Then fill in these six values in `.env` — the application will not start without them:

| Variable | What it is |
|---|---|
| `MYSQL_ROOT_PASSWORD` | Any local password |
| `SPRING_DATASOURCE_USERNAME` | The application's own account. **Must not be `root`** — the MySQL image rejects `MYSQL_USER=root` and aborts initialisation |
| `SPRING_DATASOURCE_PASSWORD` | Password for that account, separate from the root one |
| `JWT_SECRET` | Random string, **at least 32 bytes** — startup fails below that |
| `GEMINI_API_KEY` | From Google AI Studio. Without it, AI generation and writing grading fail; everything else works |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | Any local credentials |

Leave `SPRING_PROFILES_ACTIVE=prod` as it is. The prod profile is what disables Swagger,
sets the `Secure` flag on the refresh cookie, applies the strict CSP and turns on
`ddl-auto=validate`. Set `dev` only when running the backend directly on your machine.

Check nothing is missing:

```bash
python scripts/check_env.py
```

### 2. Start the stack

```bash
docker compose up -d --build
```

Flyway applies every migration on first boot, including the Cambridge 19 seed content, so
there is content to practise with immediately. Wait for the backend to report healthy:

```bash
docker compose ps
```

| Service | URL |
|---|---|
| Application | http://localhost |
| Backend API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| MinIO console | http://localhost:9001 |

Everything except the application itself is published on `127.0.0.1` rather than every
interface, so these URLs work from this machine and nowhere else. That is deliberate: a bare
port mapping binds `0.0.0.0` and Docker implements it with a DNAT rule that `ufw` does not
filter, which put an unauthenticated Redis — holding every live refresh token — on the
network of any machine with a routable address.

Swagger is intentionally **not** reachable under the prod profile. To browse the API, start
the backend with `SPRING_PROFILES_ACTIVE=dev` and open http://localhost:8080/swagger-ui/index.html

---

## Tests

The backend uses the Maven wrapper, so no local Maven install is required.

```bash
cd backend
./mvnw verify
```

That runs the unit tests (everything not tagged `integration`) and the JaCoCo coverage gate.

Integration tests are **excluded from the default run** — they are tagged `integration` and
need Docker for Testcontainers, which starts a real MySQL:

```bash
cd backend
./mvnw -Pintegration-tests verify
```

That runs every class tagged `@Tag("integration")` under `backend/src/test/java` — the
repository tests, the migration tests (`V43…`, `V48…`), the MockMvc tests for the history
and analytics endpoints, the mock test lifecycle tests, the connection-holding tests and the
Redis cache test, which starts a Redis container as well. They boot Spring against a real
database with `ddl-auto=validate`, so they are the only tests that catch drift between the
JPA entities and the Flyway schema.

Counts are deliberately not written down here: they changed on four of the last five pull
requests and were wrong every time. Maven prints them.

**The `integration-tests` profile requires a running Docker daemon.** If Docker Desktop is
not running, the suite fails before any container starts, with one line naming the cause:

```
[integration-tests] Docker daemon is not running -- start Docker Desktop and run the integration-tests profile again.
```

It fails rather than skipping on purpose: skipping would let a CI run whose Docker was
broken finish green with the schema unchecked.

Frontend:

```bash
cd frontend
npm ci
npm run lint && npm test && npm run build
```

---

## Architecture

```
                        ┌──────────────┐
                        │   Browser    │
                        └──────┬───────┘
                               │ :80
                    ┌──────────▼───────────┐
                    │  frontend (nginx)    │  React SPA
                    │  proxies /api/ ──────┼──────────┐
                    └──────────────────────┘          │ :8080
                    ┌─────────────────────────────────▼──────────────────┐
                    │  backend — Spring Boot                              │
                    │  TraceIdFilter → JwtAuthenticationFilter →          │
                    │  RateLimitInterceptor → Controller → Service →      │
                    │  Repository                                          │
                    └──┬────────┬─────────┬──────────┬──────────┬─────────┘
                       │        │         │          │          │
                 ┌─────▼──┐ ┌───▼───┐ ┌───▼────┐ ┌───▼─────┐ ┌──▼──────┐
                 │ MySQL  │ │ Redis │ │ MinIO  │ │edge-tts │ │ Gemini  │
                 │ Flyway │ │refresh│ │ audio  │ │ FastAPI │ │ (ext.)  │
                 │        │ │+limit │ │+avatar │ │         │ │         │
                 └────────┘ └───────┘ └────────┘ └─────────┘ └─────────┘
```

**Authentication.** Login returns a 15-minute access token (held in `localStorage`) plus a
7-day refresh token in an httpOnly cookie, with its JTI in Redis. Refresh rotates the token
and revokes the old JTI. Every request re-checks the `role` claim against the database, so a
token issued before a role change cannot be used with the old role.

**Exam timing is server-authoritative.** Deadlines are derived from persisted state, never
from what the client reports. A client that posts an inflated remaining time has no effect.

**Scoring has one source of truth.** All band conversion, rounding and answer normalisation
lives in `IeltsScoringUtils`; grading services delegate to it rather than keeping their own
tables.

---

## Documentation

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Detailed architecture with file-level references |
| [docs/SECURITY.md](docs/SECURITY.md) | Security decisions, ownership checks, rate limits, known gaps |
| [docs/AUDIT.md](docs/AUDIT.md) | Earlier system audit |
| [docs/STUDY_GUIDE.md](docs/STUDY_GUIDE.md) | Domain notes on IELTS scoring |

---

## Repository layout

```
backend/     Spring Boot application, Flyway migrations, tests
frontend/    React SPA
edge-tts/    Python FastAPI text-to-speech sidecar
scripts/     Content import tooling and check_env.py
docs/        Architecture and domain documentation
```

## CI/CD

```
  push / PR ──► CI (ci-cd.yml)                    ──► Deploy (deploy.yml)
                ├─ backend unit tests + coverage       ├─ gate: is a server configured?
                ├─ backend integration tests           ├─ ssh + docker login ghcr.io
                ├─ frontend lint/typecheck/test/build  ├─ git checkout <sha> on server
                └─ docker images ──► ghcr.io           └─ scripts/deploy.sh <sha>
                     (push to main only)                    └─ health gate + verify
```

### On every pull request

`.github/workflows/ci-cd.yml` runs backend unit tests with the JaCoCo coverage gate,
backend integration tests against a real MySQL via Testcontainers, frontend lint →
typecheck → tests → build plus `npm audit`, and then builds all three Docker images
**without pushing them**. A pull request proves the images still build; it never publishes.

### On a push to `main`

The same checks run, and then the images are pushed to the GitHub Container Registry,
tagged with the **full commit SHA**:

```
ghcr.io/ennela/ielts-smartprep/backend:<sha>
ghcr.io/ennela/ielts-smartprep/frontend:<sha>
ghcr.io/ennela/ielts-smartprep/edge-tts:<sha>
```

A `latest` tag moves as well, but only as a convenience for humans. **Nothing deploys
`latest`** — `scripts/deploy.sh` refuses it outright, because a moving tag cannot answer
"what is running right now?", which is the question every rollback starts from.

The image job depends on the test jobs rather than running beside them: an image built
from a commit whose tests failed would sit in the registry under a SHA that looks exactly
as legitimate as any other.

The frontend image takes `VITE_API_URL` and `VITE_SENTRY_DSN` as **build arguments**. Vite
inlines those at build time, so they cannot be supplied when the container starts.

---

## Production deployment

One Linux VPS running Docker Compose. No Kubernetes, no cloud control plane.

```
                    Internet
                       │ :80 :443
              ┌────────▼─────────┐
              │  caddy           │  TLS termination, Let's Encrypt, HSTS
              │  (only published │
              │   ports on the   │
              │   whole host)    │
              └───┬──────────┬───┘
       /api/*     │          │   everything else
        ┌─────────▼──┐   ┌───▼──────────┐
        │  backend   │   │  frontend    │   ── network: edge
        └─────┬──────┘   │  (nginx SPA) │
              │          └──────────────┘
   ┌──────────┼──────────┬──────────┐         ── network: internal
┌──▼───┐  ┌───▼──┐  ┌────▼───┐  ┌───▼─────┐
│MySQL │  │Redis │  │ MinIO  │  │edge-tts │
└──────┘  └──────┘  └────────┘  └─────────┘
```

Two things about that diagram are load-bearing:

**Caddy proxies `/api/*` straight to the backend**, not through the SPA's nginx. `nginx.conf`
overwrites `X-Forwarded-For` with its own peer address, and the backend's auth rate limiter
keys on the rightmost entry of that header. Chained behind Caddy, nginx's peer is always
Caddy — so every login attempt on the internet would land in one shared bucket and one
caller could exhaust the login budget for everybody. It stays same-origin either way: the
browser only ever talks to one host.

**Only Caddy publishes ports.** This is not cosmetic — Docker's port publishing writes DNAT
rules that **bypass ufw**, so a published port on a VPS is on the internet whether or not
the firewall says otherwise.

| Service | Port | Production | Development |
|---|---|---|---|
| caddy | 80, 443 | **public** | not run |
| frontend (nginx) | 80 | internal (behind Caddy) | `0.0.0.0:80` — it is the app |
| backend | 8080 | internal | `127.0.0.1:8080` |
| mysql | 3306 | internal | `127.0.0.1:3306` |
| redis | 6379 | internal | `127.0.0.1:6379` |
| minio | 9000 | internal | `127.0.0.1:9000` |
| minio console | 9001 | **disabled** (`MINIO_BROWSER=off`) | `127.0.0.1:9001` |
| edge-tts | 8000 | internal | `127.0.0.1:8000` |

The development file keeps its ports for local tooling but binds them to the loopback
address. A bare `"6379:6379"` binds `0.0.0.0`, which put an unauthenticated Redis holding
every live refresh token on the network of any machine with a routable address; `python
scripts/check_env.py` and a MySQL client on `localhost` work exactly as before.

### First-time server setup

```bash
sudo mkdir -p /opt/ielts-smartprep && sudo chown "$USER" /opt/ielts-smartprep
git clone https://github.com/Ennela/ielts-smartprep.git /opt/ielts-smartprep
cd /opt/ielts-smartprep
cp .env.prod.example .env.prod
```

Fill in `.env.prod`, then check nothing is missing:

```bash
python scripts/check_env.py --example .env.prod.example --env .env.prod
```

Point `APP_DOMAIN`'s DNS record at this server and leave ports 80 and 443 reachable, or
Caddy cannot complete the ACME challenge and no certificate is issued.

If the GHCR packages are private, authenticate once:

```bash
echo "$GHCR_TOKEN" | docker login ghcr.io -u <github-username> --password-stdin
```

Then deploy a specific commit:

```bash
./scripts/deploy.sh <commit-sha>
```

### Automatic deployment

`.github/workflows/deploy.yml` runs after CI succeeds on `main`. It is **safe to have
merged before any server exists**: a gate job checks whether `DEPLOY_HOST` is configured
and skips cleanly when it is not, so `main` stays green until there is somewhere to deploy.

| Repository secret | What it is |
|---|---|
| `DEPLOY_HOST` | Hostname or IP. Its absence is what disables the deploy job |
| `DEPLOY_USER` | SSH user, a member of the `docker` group |
| `DEPLOY_SSH_KEY` | Private key for that user, no passphrase |
| `DEPLOY_KNOWN_HOSTS` | Output of `ssh-keyscan <host>`. Pinned rather than using `StrictHostKeyChecking=no`, which is what stops a spoofed host collecting the deploy key |
| `VITE_SENTRY_DSN` | Frontend Sentry DSN. Read by **CI**, not by the server — Vite inlines it into the bundle at build time |

| Repository variable | Default |
|---|---|
| `DEPLOY_PORT` | `22` |
| `DEPLOY_PATH` | `/opt/ielts-smartprep` |
| `APP_DOMAIN` | Only used to label the GitHub deployment |

`workflow_dispatch` deploys any published SHA by hand — that is also how you roll back.

### What a deployment does

`scripts/deploy.sh` runs seven steps and stops at the first failure, rather than leaving a
half-deployed stack and reporting success:

1. **Validate** — compose config resolves, every required variable is set, the tag is not `latest`
2. **Record** the currently running tag, so a failure can name it
3. **Pull** — before anything is stopped, so a bad tag or registry outage costs zero downtime
4. **Start** — `docker compose up -d`. Flyway migrations run here, during backend startup
5. **Wait for health** — every one of the seven services, with a 300 s budget
6. **Verify the API** — `/actuator/health` reports `UP`, and the applied-migration count is printed
7. **Verify the public site** — HTTPS returns the SPA, and `/api/` reaches Spring rather than 502

Only containers whose image or configuration actually changed are recreated, so Caddy keeps
serving — and keeps its certificates — across a deploy that only moves the application images.

**Downtime is not zero.** The single backend container is replaced in place, so the API is
unavailable for the length of a Spring Boot start plus Flyway (tens of seconds), during
which the SPA still loads but its API calls fail. Genuine zero-downtime needs a second
backend replica and a health-aware proxy; for a single-VPS MVP that is disproportionate,
and pre-pulling keeps the window as short as a restart.

### Health checks

Every service has one, and `depends_on` gates on them, so the backend never starts against
a MySQL that is still initialising.

| Service | Probe |
|---|---|
| backend | `wget` → `/actuator/health/liveness` (wget, not curl — the runtime image is `eclipse-temurin:17-jre-alpine`) |
| frontend | `wget` → `/index.html` |
| caddy | Caddy admin API on `127.0.0.1:2019` |
| mysql | `mysqladmin ping`, **authenticated** — the unauthenticated form answers "alive" while the server is still refusing logins |
| redis | `redis-cli ping` with the password |
| minio | `mc ready local` — **not** the widely copied curl probe; curl was removed from the MinIO image |
| edge-tts | `GET /health` via Python stdlib — `python:3.10-slim` has neither curl nor wget |

`/actuator` is not routed by Caddy — it falls through to the SPA — so health is reachable
to the deploy script over the internal Docker network and to nobody on the internet.

One caveat worth knowing before you point an uptime monitor at anything: **`/actuator/health`
returns 503 whenever SMTP is unconfigured.** Spring registers a mail health indicator that
dials `smtp.gmail.com` and has no idea mail is optional in this app. Nothing in the stack
gates on that endpoint — containers probe `/actuator/health/liveness` and `deploy.sh` checks
the readiness group plus the Flyway history — but the aggregate is misleading. Either
configure `MAIL_USERNAME`/`MAIL_PASSWORD`, or disable the indicator in
`application-prod.yml` (`management.health.mail.enabled: false`).

### Pools and timeouts

Sized against each other rather than left at framework defaults. The numbers live in
`application.yml` with the reasoning next to them; the short version:

| Pool | Setting | Why |
|---|---|---|
| Hikari | max 20, min-idle 5 | The async executors alone can want 15 connections (`taskExecutor` 10 + `ttsExecutor` 5) before a single HTTP request is served, so the default of 10 could be consumed entirely by background work |
| Hikari | connection-timeout 10 s | A request that has waited ten seconds for a connection has already failed for the user; the default 30 s only holds a Tomcat thread while it does |
| Hikari | leak-detection 30 s | Logs a stack trace for any connection held longer — a backstop against AI calls creeping back inside transactions |
| Tomcat | max 100 threads | ~1 MB of stack each against a 1.5 GB container cap; DB-touching requests queue on the 20-connection pool long before 100 threads run out |
| `taskExecutor` / `ttsExecutor` | drain 30 s on shutdown | Graceful shutdown drains HTTP requests but says nothing about these; without it a deploy kills async grading mid-flight and the submission stays `GRADING` forever, which only a `FAILED` one can be retried from |

Every outbound call is bounded. `TtsService` previously used a bare `new RestTemplate()`,
whose connect and read timeouts both default to **zero, meaning wait forever** — five hung
calls ended audio generation for the life of the process. Gemini gained a TCP connect
timeout (the response and pool-lease timeouts it already had do not cover establishing the
socket), the S3 client gained an `apiCallTimeout` ceiling across retries, and Redis dropped
from Lettuce's 60-second default to 5 s, since it answers in under a millisecond and sits on
the request path for token checks.

### AI calls are never inside a transaction

Gemini is allowed 65 seconds per attempt with up to three attempts, so a transaction around
an AI call holds a pooled database connection for that entire time. Enough concurrent
generation then exhausts the pool and blocks every unrelated request — a denial of service
reachable through ordinary use of the product. Twelve entry points had this shape; none do
now.

The pattern is always the same: **read and commit, call the AI holding nothing, then write
in a second short transaction.** Where several rows have to land together — a graded essay
and its score-history row, or a full writing sitting's two submissions plus its aggregate
row and completed attempt — that atomicity is preserved, in a transaction that no longer
spans the network call. The transactional writes live on their own beans
(`MockTestGradingPersistence`, `WritingGradingPersistence`, `WritingPromptPersistence`)
because Spring applies `@Transactional` through a proxy: a call between two methods of the
same bean bypasses it, so the annotation would read correctly and do nothing.

Two tests hold this in place, because the failure is silent — re-adding `@Transactional`
breaks no behaviour, it just quietly reinstates the outage under load:

- `AiTransactionBoundaryTest` asserts that no Gemini-calling method is transactional, and
  that the persistence beans still are.
- `ConnectionHoldingIntegrationTest` asserts the property the whole approach depends on:
  a non-transactional read returns its connection to the pool immediately, while a
  transactional block holds one until it commits.

`spring.jpa.open-in-view` is now **explicitly** `true` rather than inherited from Spring
Boot's default. Several of these methods walk lazy associations after their AI call — the
Reading fallback clones a template quiz, `suggestVocabulary` gathers transcripts across a
mock test — and with no transaction it is open-in-view that keeps an EntityManager available
for them. Turning it off would break those paths with `LazyInitializationException`. It keeps
an EntityManager open, not a connection — **provided** Hibernate releases connections when a
transaction ends. Spring's `HibernateJpaVendorAdapter` sets Hibernate's handling mode to
*hold until the session closes*, which under open-in-view is the whole request, so the
read-only transaction behind the first repository read in an AI method kept its connection
across the Gemini call. `application.yml` overrides the mode with
`hibernate.connection.handling_mode=DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION`, and
`AiCallConnectionHoldingIntegrationTest` drives a real request into a blocked Gemini stub and
asserts the pool has nothing checked out.

Hikari's `leak-detection-threshold` stays at 30 seconds as a backstop: if a connection is
ever held that long again, the stack trace naming the culprit appears in the log. That is how
the hold above was found, on a real full-writing submission.

### Monitoring

Sentry on both tiers. The backend reads `SENTRY_DSN` at runtime from `.env.prod`; the
frontend DSN is a **CI build argument**, because Vite inlines it into the bundle. Backend
logs are structured JSON with trace IDs (`logback-spring.xml`), rotated by Docker at 10 MB
× 3 files per service — without that the json-file driver grows until the disk fills, which
takes the database down with it.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f backend
docker compose -f docker-compose.prod.yml --env-file .env.prod ps
```

The MinIO web console is switched **off** in production (`MINIO_BROWSER=off`), not merely
unpublished — omitting `--console-address` does not disable it, MinIO just picks its own
port. Only the backend uses MinIO, over the S3 API. Administer it from inside:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod exec minio \
  mc admin info local
```

### Backup

MySQL is the only stateful thing that cannot be rebuilt. MinIO holds generated audio and
avatars (regenerable, slowly); Redis holds sessions and rate-limit counters (disposable);
the Caddy volume holds certificates (re-issuable, but rate limited — don't prune it casually).

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T mysql \
  sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction \
         --routines --triggers "$MYSQL_DATABASE"' \
  | gzip > "backup-$(date +%F).sql.gz"
```

`--single-transaction` so the dump does not lock the application out while it runs. Put
that on a cron job and copy the result off the server; a backup that lives only on the
machine it protects is not a backup.

### Rollback, and its limits

Redeploying an older tag rolls back **the code only**:

```bash
./scripts/deploy.sh <previous-sha>
```

**Flyway migrations are not reverted, and this is not an oversight.** There are no
down-migrations in this project, and several migrations rewrite or drop data — `V40` and
`V46` retire the legacy admin identity, `V42` and `V45` convert enum columns, `V41` changes
foreign-key behaviour. Reverting the image after those have applied points older code at a
newer schema, which under `ddl-auto=validate` either fails at startup or, worse, appears to
work.

So the safe rollback window is **any deploy that added no migration**. Past that, restore
the database from a backup first. Check what actually applied:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod exec mysql \
  sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" -e \
    "SELECT version, description, success, installed_on FROM flyway_schema_history \
     ORDER BY installed_rank DESC LIMIT 5" "$MYSQL_DATABASE"'
```

`scripts/deploy.sh` prints the previous tag and this same warning whenever it fails.

### If a deployment fails

The script exits non-zero, names the step, and dumps the last 100 log lines of the service
that broke. Nothing is rolled back automatically. A failed migration looks like the backend
never becoming healthy — the Flyway error will be in that log dump.

Because images are pulled before anything is stopped, a failure in steps 1–3 leaves the
previous release running and untouched.
