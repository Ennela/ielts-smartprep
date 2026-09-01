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
| Database | MySQL 8.0, Flyway (45 migrations) |
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
| `SPRING_DATASOURCE_PASSWORD` | Must match the value above |
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

Flyway applies all 45 migrations on first boot, including the Cambridge 19 seed content, so
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

Swagger is intentionally **not** reachable under the prod profile. To browse the API, start
the backend with `SPRING_PROFILES_ACTIVE=dev` and open http://localhost:8080/swagger-ui/index.html

---

## Tests

The backend uses the Maven wrapper, so no local Maven install is required.

```bash
cd backend
./mvnw verify
```

That runs 385 unit tests and the JaCoCo coverage gate.

Integration tests are **excluded from the default run** — they are tagged `integration` and
need Docker for Testcontainers, which starts a real MySQL:

```bash
cd backend
./mvnw -Pintegration-tests verify
```

That runs 31 tests across `UserRepositoryTest`, `ReadingQuizRepositoryTest`,
`ListeningPartRepositoryTest`, `VocabularyRepositoryTest`,
`ContentDeletionSafetyRepositoryTest`, `V43MigrationIntegrationTest` and
`AuthRateLimitIntegrationTest`. They boot Spring against a real database with
`ddl-auto=validate`, so they are the only tests that catch drift between the JPA entities
and the Flyway schema.

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
                 │  ×45   │ │+limit │ │+avatar │ │         │ │         │
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

## Continuous integration

`.github/workflows/ci-cd.yml` runs on pushes to `main` and feature branches:

1. Backend unit tests **and the coverage gate** (`./mvnw verify`)
2. Backend integration tests against a real MySQL via Testcontainers
3. Frontend lint, tests and production build, plus `npm audit`
4. Docker image builds for all three services

There is **no deployment stage yet** — the file is named `ci-cd.yml` but currently performs
CI only.
