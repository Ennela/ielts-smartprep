# Security

This document records the security decisions in this codebase and the reasoning behind
them — what was chosen, what was traded away, and what is still open. Every figure here is
taken from the source it names.

Related: [ARCHITECTURE.md](ARCHITECTURE.md) for the request pipeline, and the
[README](../README.md) for the environment variables these settings read.

---

## 1. A default admin credential lives in git history

`V11__add_role_to_users.sql` seeds an administrator and prints its password in a comment
one line above the hash:

```sql
-- Seed default admin account (password: admin123 hashed with BCrypt 12 rounds)
INSERT IGNORE INTO users (email, username, password_hash, display_name, role)
VALUES ('admin@smartprep.local', 'admin', '$2a$12$byPbRBX6...', 'Administrator', 'ADMIN');
```

**The risk.** Anyone who has ever cloned this repository knows a working administrator
password for any deployment that ran V11 and never rotated it. The BCrypt cost of 12 is
beside the point — nobody has to crack anything. This is a published-credential problem,
not a hashing problem.

**What was done.** `V40__disable_legacy_default_admin.sql` neutralises it:

```sql
UPDATE users
SET password_hash = '!disabled-legacy-default-admin!'
WHERE email = 'admin@smartprep.local'
  AND username = 'admin'
  AND SHA2(password_hash, 256) = '8ea4f8a3468a0ebeae4b2eda95137c88f3eeb3c6a5970047a9a8e25f23ba9519';
```

Three details are deliberate:

- The sentinel is **not a valid BCrypt hash**, so `passwordEncoder.matches(...)` in
  `UserService.login` can never return true against it. The account cannot be logged into.
- The `SHA2` guard restricts the update to the *unchanged* seed. An operator who already
  rotated that password keeps their working account.
- The digest is compared instead of copying the compromised hash into a second migration,
  so V40 does not republish the value it is retiring.

**What was not done, and why.** The hash remains permanently in git history. Rewriting
history would invalidate every existing clone and fork in order to hide a credential that
is already dead — the cost outweighs the benefit. The mitigation is that the credential no
longer works, not that it is concealed.

**If you operate a database that applied V11 before V40**, verify the outcome:

```sql
SELECT username, email, role, password_hash FROM users WHERE username = 'admin';
```

| Result | Meaning |
|---|---|
| `password_hash` is `!disabled-legacy-default-admin!` | V40 matched. Nothing to do. |
| `password_hash` is still a BCrypt string | The password was changed before V40 ran. Confirm a real person owns the account and that it is not still `admin123`. |
| No row | V11 never ran against this database. |

The row is **not deleted** and still carries `role = 'ADMIN'`. See [§5.4](#5-known-gaps-and-accepted-risks)
for the consequence of that.

---

## 2. Access tokens are kept in `localStorage`

A deliberate choice, not an oversight. The trade-off is written down here so it can be
argued with.

| Token | Lifetime | Storage |
|---|---|---|
| Access | **15 minutes** (`app.jwt.expiration-ms: 900000`) | `localStorage`, sent as a bearer header (`frontend/src/api/axiosClient.ts`) |
| Refresh | **7 days** (`app.jwt.refresh-expiration-ms: 604800000`) | `httpOnly` cookie, unreachable from JavaScript (`AuthController.buildRefreshCookie`) |

The refresh cookie is built with `httpOnly(true)`, `path=/api/v1/auth`, `SameSite=Lax`, and
`secure` bound to `app.security.secure-cookies` — `false` in the base profile so local HTTP
works, `true` in `application-prod.yml`.

**The exposure.** An XSS bug anywhere in the SPA can read the access token and use it for
up to 15 minutes. That is real, and it is the price of this design.

**Why it was accepted.** Moving the access token into a cookie does not remove the risk, it
relocates it. A cookie is sent ambiently on every request, which reintroduces CSRF as a
threat this application currently does not have to defend against: the API is stateless
(`SessionCreationPolicy.STATELESS`) and CSRF protection is disabled precisely because no
ambient credential is used for authorisation. Trading XSS exposure for CSRF exposure is not
self-evidently a win, and it is a larger change than it appears.

**What limits the blast radius instead:**

- The access token expires in 15 minutes, so a stolen one has a short window.
- The refresh token — the credential that grants *renewed* access for 7 days — is outside
  JavaScript's reach entirely. An XSS payload cannot exfiltrate a long-lived credential.
- Production applies a strict CSP with **no `unsafe-inline`** and no localhost origins
  (`application-prod.yml`), against a dev policy that does permit inline styles. This is
  the main structural defence against the XSS that the attack requires in the first place.
- Every authenticated request re-reads the user's `role` from the database rather than
  trusting the claim (`JwtAuthenticationFilter`), so a token minted before a role change
  cannot be replayed with the old role.
- Refresh rotates the JTI and revokes the previous one; access-token JTIs can be
  blacklisted (`TokenService`, Redis keys `refresh:jti:` and `blacklist:jti:`).
- A `JWT_SECRET` shorter than 32 bytes fails startup (`JwtTokenProvider`).
- Passwords are hashed with BCrypt at cost 12 (`SecurityConfig.passwordEncoder`).

---

## 3. Ownership checks, endpoint by endpoint

Every endpoint that accepts a resource id has to prove the caller owns that resource. There
are three patterns here, and the third is intentional.

### Scoped at the repository layer

Ownership is part of the `WHERE` clause, so an unowned id simply does not come back. This
is the default and the preferred pattern.

| Endpoint | Repository method | Message on miss |
|---|---|---|
| `GET /reading/{quizId}` | `findByQuizIdAndUserUserId` | `Quiz not found` |
| `GET /reading/{quizId}/result` | `findByQuizIdAndUserUserId` | `Quiz not found` |
| `POST /reading/{quizId}/submit` | `findByQuizIdAndUserUserId` | `Quiz not found` |
| `GET /listening/{testId}/result` | `findByTestIdAndUserUserId` | `Listening test not found` |
| `GET /writing/submissions/{id}` | `findBySubmissionIdAndUserUserId` | `Submission not found` |
| `GET /writing/full-submissions/{id}` | `findByIdAndUserUserId` | `Full writing submission not found` |
| `GET /attempts/{attemptId}` | `findByAttemptIdAndUserUserId` | `Attempt not found` |
| `POST /attempts/{attemptId}/complete` | `findByAttemptIdAndUserUserId` | `Attempt not found` |

`GET /listening/{testId}/result` was **missing this check** and has been patched. A bare
`findById` there let any authenticated user read another user's answers, band score and
transcript by incrementing `testId`. The reason is recorded in the code itself, in
`ListeningQueryService.getTestResult`.

### Checked manually, with a deliberately indistinguishable error

`MockTestService` loads by id and then compares the owner. The point is that **both
branches throw the same exception type with the same message**, so a caller cannot tell
"exists but is not yours" from "does not exist". The code says so:

```java
// Same exception and message as a genuine miss: a caller must not be able to tell
// "exists but is not yours" from "does not exist". Matches ReviewService.
```

This covers `getSessionById`, `saveProgress`, `nextSection`, `submitExam`, `regradeWriting`
and `getSubmission`. `ReviewService.getHistoryDetail` follows the same pattern with
`Score history not found`, and so do `ReviewService.explainAnswer` (`Answer not found`) and
`VocabularyService.reviewVocabulary` / `deleteVocabulary` (`Vocabulary item not found`) —
the last three were brought into line after §5 first flagged them as leaks.

Leaking existence is a smaller problem than leaking content, but it still tells an attacker
which ids are worth attacking and roughly how much data the system holds.

### No ownership check, by design

These serve shared content, so there is no owner to check against:

- `GET /listening/parts/{partId}` — shared question bank; answers are not included
- `GET /writing/prompts/{promptId}` — shared prompt catalogue
- `GET /listening/audio/{fileName}`, `GET /auth/avatar/{fileName}` — `permitAll`, and
  skipped by the JWT filter
- everything under `/api/v1/admin/**` — gated by `hasRole('ADMIN')`, not by ownership
- `POST /listening/{partId}/generate-audio` — an authoring operation, restricted to admins
  after students turned out to be able to reach it with any authenticated token

---

## 4. Rate limiting

Three independent limiters. All state lives in Redis, so the limits hold across multiple
application instances rather than being per-process. Buckets are Bucket4j token buckets
over a Lettuce `ProxyManager` (`RateLimitConfig`); the login lockout uses plain Redis
counters. Registration and the exact path lists are in `WebMvcConfig.addInterceptors`.

### Per user — AI endpoints

`RateLimitInterceptor`, order 1.

| Limit | Value | Redis key |
|---|---|---|
| Burst | **10 requests / minute** | `rate-limit:{userId}` |
| Quota | **50 requests / day** | `ai-limit:daily:{userId}:{date}`, TTL 1 day |

The minute bucket refills *intervally* — all 10 tokens return at once at the end of the
minute rather than trickling back. Applied to `/reading/generate`, `/listening/generate`,
`/listening/*/generate-audio`, `/writing/grade`, `/listening/ai-analyze/**`,
`/listening/vocabulary/**` and `/vocab/ai-suggest`.

Keying on user id rather than IP is what makes this a *cost* control: every one of these
calls spends money at Gemini, and money is spent per account, not per network location.

### Per IP — public auth endpoints

`AuthRateLimitInterceptor`, order 2. Keyed by client IP because there is no authenticated
user yet.

| Endpoint | Limit | Redis key |
|---|---|---|
| `POST /auth/register` | **5 / minute** | `auth-rate-limit:register:{ip}` |
| `POST /auth/login` | **10 / minute** | `auth-rate-limit:login:{ip}` |
| `POST /auth/forgot-password` | **10 / minute** | `auth-rate-limit:login:{ip}` |

`/login` and `/forgot-password` deliberately share one bucket and the more lenient policy.

### Per username — login lockout

`LoginLockoutService` runs alongside the per-IP limit and defends something different: the
per-IP bucket stops one host hammering the endpoint, while the lockout stops a *distributed*
guessing attack against a single account.

- **5 failed attempts** (`app.security.max-login-attempts`)
- **15 minute** lockout (`app.security.lockout-duration-minutes`)
- Redis key `login-fail:{username}`, TTL set on the first failure

The TTL is a fixed window measured from the first failure and is not extended by later
ones, so an attacker gets 5 guesses per 15 minutes rather than being locked out for good —
which also means a third party cannot lock a real user out indefinitely. A successful login
deletes the key.

### Related

Password reset tokens live under the `pwd-reset:` prefix with a **15 minute** TTL
(`PasswordResetService`).

---

## 5. Known gaps and accepted risks

Documented rather than hidden. Entries marked *fixed* were closed after this document
first listed them; the rest are open.

**5.1 and 5.2 — resource-existence leaks on vocabulary and answer ids — fixed.**
`VocabularyService.reviewVocabulary` and `deleteVocabulary` used to throw
`ResourceNotFoundException` for a missing item but `IllegalArgumentException` ("You are not
authorized to…") for one owned by someone else. The two map to different HTTP statuses, so
`POST /vocab/{id}/review` and `DELETE /vocab/{id}` could be used to enumerate other users'
vocabulary ids. `ReviewService.explainAnswer` leaked the same way, through
`Answer does not belong to this history` being distinguishable from `Answer not found`.

All three now throw the same exception with the same message as a genuine miss, matching the
pattern in `MockTestService` (§3), and the tests assert the messages are identical rather
than merely that something was thrown.

**5.3 — Login allows user enumeration.**
`UserService.login` throws for an unknown username *before* calling `recordFailedAttempt`,
and appends `N attempt(s) remaining` only for accounts that exist. Either signal separates a
real account from a fabricated one, and unknown usernames are never subject to lockout.

**5.4 — The legacy admin row still exists.**
V40 kills the password but keeps the row, including `role = 'ADMIN'` and the address
`admin@smartprep.local`. `PasswordResetService` looks users up by email, so whoever controls
mail delivery to that address can set a new password and obtain an administrator account.
Deleting or renaming the row would close this.

**5.5 — The per-IP limit is spoofable.**
`AuthRateLimitInterceptor.resolveClientIp` takes the **leftmost** value of `X-Forwarded-For`.
nginx is configured with `$proxy_add_x_forwarded_for`, which *appends* to the incoming
header rather than replacing it, so a client-supplied value survives and becomes the bucket
key. An attacker can vary it per request and bypass the per-IP limits entirely. The
per-username lockout (§4) is what limits the damage.

**5.6 — Unauthenticated requests bypass the AI limiter.**
`RateLimitInterceptor` returns `true` when it cannot identify a user, deferring to Spring
Security. That is correct for endpoints requiring authentication, but it means the limiter
contributes nothing on any path that is ever made public.

**5.7 — Some sensitive endpoints are not rate limited.**
`/auth/reset-password`, `/auth/refresh` and `/listening/generate-mock` appear in neither
interceptor's path list.

**5.8 — No security headers from the reverse proxy.**
`frontend/nginx.conf` adds none. Spring Security's defaults supply `X-Frame-Options` and
`X-Content-Type-Options`, but HSTS is only emitted over HTTPS and no `Referrer-Policy` is
set anywhere.
