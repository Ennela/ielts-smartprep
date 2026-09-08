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

V40 left the row itself in place, still carrying `role = 'ADMIN'`. That was its own problem
— a disabled credential is not a retired identity — and it is closed separately by
`V46__retire_legacy_default_admin_identity.sql`, which renames the account to an
unresolvable address. See [§5.4](#5-known-gaps-and-accepted-risks) for why it renames
instead of deleting.

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
- A CSP restricts what an injected script could do. Note carefully *which* CSP: the one in
  `application-prod.yml` is emitted by Spring and therefore lands only on API responses,
  where it protects nothing, because a policy on a JSON body has no document to govern.
  The policy that matters is the one on the HTML document, which is served by nginx and
  was **missing entirely** until §5.8 was closed. It now sets `script-src 'self'` plus a
  hash for the one inline theme script. Its `style-src` keeps `'unsafe-inline'`, because
  40 components style themselves with React inline style attributes and a strict style-src
  would render the app unusable — a deliberate weakening of a far less dangerous directive.
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

Documented rather than hidden. Every entry below was found by auditing this codebase and
written down before it was fixed; all eight have since been closed. They are kept rather
than deleted because the record of what was wrong, and why it was wrong, is worth more
than a section saying nothing is. One of them (§5.7) is closed by a deliberate decision
not to apply a limit, with the reasoning recorded.

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

**5.3 — Login allowed user enumeration — fixed.**
`UserService.login` threw for an unknown username *before* calling `recordFailedAttempt`,
and appended `N attempt(s) remaining` only for accounts that existed. Either signal
separated a real account from a fabricated one, and unknown usernames were never subject to
lockout, so probing for valid names was unlimited.

Three things had to change together, because fixing only the visible one would have left the
hole open:

- The two failures now throw the identical message. A test asserts they are equal rather
  than asserting either one's wording, since equality is the actual property.
- A failed attempt is recorded for unknown usernames too. That subjects username probing to
  the same 5-per-15-minutes lockout, and it is also what lets the remaining-attempts figure
  appear in both messages instead of only one.
- The supplied password is hashed against a placeholder when no account exists. Without
  this the cases stay separable by response time no matter how well the messages match:
  BCrypt at cost 12 takes on the order of 250ms, while a missing row returns in about 1ms.
  The placeholder is derived from the injected encoder at startup, so it always carries the
  configured cost factor.

**5.4 — The legacy admin identity was still live — retired.**
V40 killed the password but left the identity in place, including `role = 'ADMIN'` and the
address `admin@smartprep.local`. `PasswordResetService` looks users up by email, so anyone
able to receive mail at that published address could set a new password and obtain an
administrator account. Disabling a credential is not the same as retiring an identity.

`V46__retire_legacy_default_admin_identity.sql` renames it to `legacy-admin@invalid`.
`.invalid` is reserved by RFC 2606 as permanently unresolvable, so the password-reset route
is closed by construction rather than by hoping nobody registers the domain.

It renames rather than deletes deliberately. Almost every foreign key into `users` is
`ON DELETE CASCADE`, so deleting the row would take everything the account owns with it —
and it was not dormant: on the database this was written against it held 45 vocabulary
items, 22 reading quizzes, 16 score-history rows, 6 essays and 2 listening tests. The
account keeps its role, its password and its history; only the published identity goes. The
migration carries the `UPDATE` to substitute a real address.

**5.5 — The per-IP limit was spoofable — fixed.**
`AuthRateLimitInterceptor.resolveClientIp` took the **leftmost** value of `X-Forwarded-For`
while nginx used `$proxy_add_x_forwarded_for`, which *appends* to the incoming header rather
than replacing it. A client-supplied value therefore survived on the left and became the
bucket key, so one host could vary it per request and bypass the per-IP limits entirely.

Fixed on both sides. nginx now overwrites the header with `$remote_addr`, so a caller cannot
contribute to it at all; and the interceptor reads the **rightmost** entry, which is written
by the proxy nearest the application and is correct whether or not a proxy appends. Both are
needed: the nginx change fixes the deployment, the interceptor change means the application
is not relying on one line of proxy config to be safe.

This assumes exactly one trusted proxy. Exposing the backend port directly to untrusted
clients would let a caller pick its own key again.

**5.6 — Unauthenticated requests bypassed the AI limiter — fixed.**
`RateLimitInterceptor` returned `true` when it could not identify a user, deferring to
Spring Security. That was correct only as long as every path stayed authenticated: the
limiter contributed nothing the moment one was opened up, on endpoints that spend money per
call. It now fails closed and answers 401, on the reasoning that a caller with no identity
has no quota rather than an unlimited one.

**5.7 — Sensitive endpoints missing from the limiters — fixed, with one deliberate omission.**
`/auth/reset-password` is now covered by the per-IP auth limiter: it consumes a reset token,
and unmetered it was the one public endpoint where guessing cost an attacker nothing.
`/listening/generate-mock` is now covered by the per-user AI limiter — it generates four
listening parts in a single call, making it the most expensive request in the application,
and it was not metered at all.

`/auth/refresh` is deliberately left out. The auth limiter keys on IP, and every user behind
one NAT — an office, a school, a university campus, which is precisely this product's
audience — shares that key. Browsers refresh on 401, so bursts are normal rather than
suspicious, and the failure mode is logging out groups of legitimate users at once. The
abuse it would prevent is already bounded: a refresh needs a valid token whose JTI is
rotated and revoked on use.

**5.8 — No security headers from the reverse proxy — fixed.**
`frontend/nginx.conf` added none, so the HTML document — the only response where a CSP
actually constrains anything — was served with no CSP, no `X-Frame-Options`, no
`X-Content-Type-Options` and no `Referrer-Policy`. It now sets all four, and repeats them
inside the static-asset location because an nginx location that declares any `add_header`
of its own stops inheriting the server-level ones.

No `Strict-Transport-Security`. Nothing terminates TLS at this nginx, so it is not the
component that knows whether HTTPS is in use; HSTS belongs at the TLS edge.

Closing this surfaced a related deployment bug, fixed at the same time: the SPA called the
backend at an absolute `http://localhost:8080`, so the nginx `/api/` proxy was dead code,
the browser needed CORS, the `X-Forwarded-For` handling in §5.5 never applied to real
traffic, and the app only worked when opened on the machine running it. Both the vite dev
server and this nginx already proxy `/api`, so the SPA now uses a relative `/api/v1` and
every request is same-origin.

**5.9 — Every backing service was published to the network — fixed.**
`docker-compose.yml` published MySQL (3306), Redis (6379), MinIO (9000/9001), edge-tts
(8000) and the backend (8080) with bare mappings such as `"6379:6379"`. A bare mapping
binds `0.0.0.0`, and Docker implements it as a DNAT rule that `ufw` does not filter — so
on any host with a routable address these were reachable from the network, including a
Redis with no `requirepass` holding every live refresh token.

Two changes, because the two files have different jobs. `docker-compose.prod.yml` publishes
nothing except Caddy's 80/443; MySQL, Redis, MinIO and edge-tts sit on an internal Docker
network with no host binding at all. `docker-compose.yml` keeps its ports for local tooling
but binds them to `127.0.0.1`, which leaves every local workflow working and removes the
network exposure.

**5.10 — Redis was unauthenticated, and its eviction policy could sign users out — fixed.**
Redis holds refresh-token JTIs, the access-token blacklist, login lockout counters, the
rate-limit buckets and the AI content cache. It ran with no password.

`requirepass` is now set in production and reaches both Redis clients — Spring's, through
`spring.data.redis.password`, and the separate Lettuce client `RateLimitConfig` builds for
Bucket4j. Both had to change together: the limiter fails closed, so authenticating one and
not the other would have turned every rate-limited endpoint into an error. A blank password
still means no authentication, because a Redis without `requirepass` rejects `AUTH`
outright and local development runs without one.

The eviction policy changed from `allkeys-lru` to `volatile-ttl`, which is a security
property rather than a tuning preference. Every key this application writes carries a TTL,
so under LRU the ranking is by how recently a key was *read* — and a refresh token is
written once at login and then not touched for hours, which makes it one of the best LRU
eviction candidates, while a busy AI cache entry looks hot. Under memory pressure LRU would
therefore have signed users out to preserve regenerable cache. `volatile-ttl` evicts the key
expiring soonest instead, which puts one-minute rate-limit buckets and 24-hour cache ahead
of 7-day token state.

**5.11 — Outbound calls had no timeout, one of them literally none — fixed.**
`TtsService` built its client with `new RestTemplate()`. `SimpleClientHttpRequestFactory`
defaults both connect and read timeouts to zero, meaning wait forever. That client calls the
edge-tts sidecar, which streams from Microsoft's speech endpoint, so an unresponsive
upstream blocked the calling thread permanently — and audio generation runs on a five-thread
executor, so five such calls ended audio generation for the lifetime of the process, silently.

Also closed: the Gemini client bounded its response and pool-lease waits but not the TCP
connect, which falls back to the operating system's retry behaviour and can hold a thread
for minutes past every configured timeout; the S3 client had no `apiCallTimeout`, so retries
could extend one upload without any ceiling; and the Redis command timeout was Lettuce's
60-second default on a service answering in under a millisecond, on the request path for
token checks.

**5.12 — AI calls held a database connection — all fixed.**
Gemini is allowed 65 seconds per attempt with up to three attempts and exponential backoff.
Any transaction wrapping such a call holds a pooled database connection for that whole time,
so enough concurrent AI work exhausts the pool and blocks every unrelated request in the
application — a denial of service reachable through ordinary use of the product, needing no
attacker at all.

`MockTestAsyncGrader` was the worst: one transaction spanning **two** Gemini calls, running
on an executor with ten threads, against a pool that then had ten connections. It could take
the entire pool on its own.

Twelve entry points had the pattern. A reflective sweep over the service layer found four
that manual review had missed, including two `@Transactional(readOnly = true)` methods —
read-only still checks a connection out — and two reached only indirectly, through a caller
whose own transaction propagated into them. `WritingAssemblyService.submitFullWriting` was
one of those: fixing the method it called would have achieved nothing, because the caller's
transaction still enclosed both Gemini calls.

All of them are now read → commit → call the AI holding nothing → write in a second short
transaction. Where rows had to land together they still do; only the network call left the
transaction. The transactional writes sit on dedicated beans because Spring applies
`@Transactional` through a proxy, so a call between two methods of the same bean would
bypass it and the annotation would read correctly while doing nothing.

Two tests keep it that way, because this failure is invisible: re-adding `@Transactional`
breaks no behaviour and passes every functional test, it just reinstates the outage under
load. `AiTransactionBoundaryTest` asserts no Gemini-calling method is transactional and that
the persistence beans still are; `ConnectionHoldingIntegrationTest` asserts the property the
approach rests on — that a non-transactional read hands its connection straight back, while
a transactional block holds one until commit.

One related fix fell out of it. `ReviewService.explainAnswer` catches a Gemini failure and
sets a placeholder message without saving it — but under a transaction, dirty checking
flushed that placeholder anyway, and the cache check at the top of the method then returned
it forever. A single transient AI failure permanently denied that answer a real explanation.
With no transaction, the code now does what it was written to do.

**5.13 — MinIO runs as its root account — accepted, with the reasoning.**
The backend authenticates to MinIO with `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`, the
instance's administrative credentials, when all it needs is read and write on one bucket.
A scoped service account with a bucket-limited policy would be the correct shape.

It is accepted for now rather than hidden, because the exposure is bounded by things that
*are* enforced: MinIO has no published port and sits on an internal Docker network that the
SPA's nginx cannot even resolve, the web console is switched off outright
(`MINIO_BROWSER=off` — omitting `--console-address` does not disable it, it merely moves it
to a port of MinIO's choosing), and the bucket is private by default, with audio and avatars
served through the backend at `/api/v1/...` rather than directly from storage. So reaching
these credentials requires code execution inside the backend container, at which point the
credentials are readable from its environment regardless of their scope.

Closing it properly means provisioning a service account at deploy time (`mc admin user
add` plus a bucket policy), which is operational setup rather than a code change.

**Object lifecycle: deliberately none.** Generated listening audio accumulates without
expiry, and that is intentional — `listening_parts.audio_url` rows reference these objects
indefinitely, so a lifecycle rule that expired them would leave working exam content
pointing at missing audio. Storage growth is bounded by content volume, not by traffic, and
the volume is a named Docker volume that survives container replacement.
