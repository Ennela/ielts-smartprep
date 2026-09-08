#!/usr/bin/env bash
#
# Deploy a published image tag to this server.
#
#   ./scripts/deploy.sh <commit-sha>
#
# Runs on the production VPS, from the repository root, next to a filled-in .env.prod.
# The CI workflow invokes it over SSH; running it by hand does exactly the same thing.
#
# What it does, in order: validate, pull, restart, wait for health, verify. It stops at
# the first failure and says which step failed, rather than leaving a half-deployed stack
# and reporting success.
#
# What it deliberately does NOT do: roll the database back. Flyway migrations run when the
# backend starts, and this project has migrations that drop and rewrite data. Reverting to
# the previous image after they have applied would point old code at a newer schema, which
# is a worse failure than the one being recovered from. On failure the script prints the
# previous tag and leaves the decision to a human.

set -Eeuo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly COMPOSE_FILE="${REPO_ROOT}/docker-compose.prod.yml"
readonly ENV_FILE="${REPO_ROOT}/.env.prod"

# How long to wait for every container to report healthy. The backend dominates this: on a
# first boot it applies 46 Flyway migrations before it will answer the liveness probe.
readonly HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-300}"

readonly SERVICES=(mysql redis minio edge-tts backend frontend caddy)

step()  { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
info()  { printf '    %s\n' "$*"; }
ok()    { printf '\033[1;32m    OK\033[0m %s\n' "$*"; }
fail()  { printf '\n\033[1;31mDEPLOYMENT FAILED\033[0m: %s\n' "$*" >&2; }

compose() {
  docker compose --file "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

# Set by step 2 so the failure handler can name the tag that was running before.
PREVIOUS_TAG=""

on_failure() {
  local line=$1
  fail "step at line ${line} did not succeed."
  cat >&2 <<EOF

  The stack is in whatever state that step left it. Nothing has been rolled back.

  Look at what broke:
    docker compose -f docker-compose.prod.yml --env-file .env.prod ps
    docker compose -f docker-compose.prod.yml --env-file .env.prod logs --tail=200 backend

EOF
  if [[ -n "$PREVIOUS_TAG" ]]; then
    cat >&2 <<EOF
  The previous release was ${PREVIOUS_TAG}. To go back to it:
    ./scripts/deploy.sh ${PREVIOUS_TAG}

  Read this first if any migration applied during this deploy: the database is NOT
  reverted by that command. Flyway has no down-migrations here, and several migrations in
  this project rewrite or drop data. Running older code against the newer schema can fail
  at startup on ddl-auto=validate, or worse, appear to work. Check which version applied:
    docker compose -f docker-compose.prod.yml --env-file .env.prod exec mysql \\
      mysql -u root -p"\$MYSQL_ROOT_PASSWORD" -e \\
      'SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;' \\
      "\$MYSQL_DATABASE"
EOF
  fi
  exit 1
}
trap 'on_failure $LINENO' ERR

# ---------------------------------------------------------------------------
# Step 1 — validate before touching anything
# ---------------------------------------------------------------------------
step "1/7  Validating configuration"

IMAGE_TAG="${1:-${IMAGE_TAG:-}}"
if [[ -z "$IMAGE_TAG" ]]; then
  fail "no image tag given. Usage: ./scripts/deploy.sh <commit-sha>"
  exit 1
fi
if [[ "$IMAGE_TAG" == "latest" ]]; then
  fail "refusing to deploy 'latest'. A moving tag cannot answer what is running, which is
      the question a rollback starts from. Pass the commit SHA."
  exit 1
fi
export IMAGE_TAG

[[ -f "$COMPOSE_FILE" ]] || { fail "$COMPOSE_FILE not found. Run this from the repository."; exit 1; }
[[ -f "$ENV_FILE" ]] || { fail ".env.prod not found. Copy .env.prod.example and fill it in."; exit 1; }

# Refuses and names the variable if anything required is missing, rather than starting a
# half-configured stack.
compose config --quiet
ok "compose configuration is valid"

if ! compose config | grep -q ":${IMAGE_TAG}"; then
  fail "the resolved configuration does not reference tag ${IMAGE_TAG}."
  exit 1
fi
ok "images resolve to tag ${IMAGE_TAG}"

# ---------------------------------------------------------------------------
# Step 2 — record what is running now
# ---------------------------------------------------------------------------
step "2/7  Recording the current release"

current_container="$(compose ps --quiet backend 2>/dev/null || true)"
if [[ -n "$current_container" ]]; then
  PREVIOUS_TAG="$(docker inspect --format '{{.Config.Image}}' "$current_container" | sed 's/.*://')"
  info "currently running: ${PREVIOUS_TAG}"
else
  info "no backend container running -- this looks like a first deployment"
fi

# ---------------------------------------------------------------------------
# Step 3 — pull
# ---------------------------------------------------------------------------
step "3/7  Pulling images"

# Pulled before anything is stopped, so a registry problem or a typo in the tag costs no
# downtime at all: the running stack is still untouched when this fails.
if ! compose pull --quiet; then
  fail "could not pull images for tag ${IMAGE_TAG}.

      If the packages are private, this server needs to authenticate first:
        echo \$GHCR_TOKEN | docker login ghcr.io -u <github-username> --password-stdin

      Nothing was changed. The previous release is still running."
  exit 1
fi
ok "images pulled"

# ---------------------------------------------------------------------------
# Step 4 — start, which is also when migrations run
# ---------------------------------------------------------------------------
step "4/7  Starting services (Flyway migrations run during backend startup)"

# Only containers whose image or configuration actually changed are recreated, so Caddy
# keeps serving -- and keeps its certificates -- across a deploy that only moves the
# application images.
compose up --detach --remove-orphans
ok "containers started"

# ---------------------------------------------------------------------------
# Step 5 — wait for health
# ---------------------------------------------------------------------------
step "5/7  Waiting for health checks (timeout ${HEALTH_TIMEOUT_SECONDS}s)"

deadline=$(( $(date +%s) + HEALTH_TIMEOUT_SECONDS ))
for service in "${SERVICES[@]}"; do
  container="$(compose ps --quiet "$service")"
  if [[ -z "$container" ]]; then
    fail "service ${service} has no container. See: docker compose -f docker-compose.prod.yml ps"
    exit 1
  fi

  while :; do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container")"
    state="$(docker inspect --format '{{.State.Status}}' "$container")"

    if [[ "$status" == "healthy" ]]; then
      ok "${service} healthy"
      break
    fi
    # Every service in docker-compose.prod.yml declares a health check. If one ever loses
    # it, fall back to "running" rather than spinning here until the timeout and reporting
    # a failure that is really a missing probe.
    if [[ "$status" == "none" && "$state" == "running" ]]; then
      info "${service} running (no health check declared)"
      break
    fi
    if [[ "$state" == "exited" || "$state" == "dead" ]]; then
      fail "service ${service} exited while starting up."
      echo >&2
      compose logs --tail=100 "$service" >&2
      exit 1
    fi
    if (( $(date +%s) >= deadline )); then
      fail "service ${service} did not become healthy within ${HEALTH_TIMEOUT_SECONDS}s (last status: ${status})."
      echo >&2
      if [[ "$service" == "backend" ]]; then
        echo "      The backend runs Flyway on startup, so a failed migration looks exactly" >&2
        echo "      like this. The error will be in the log below." >&2
        echo >&2
      fi
      compose logs --tail=100 "$service" >&2
      exit 1
    fi
    sleep 5
  done
done

# ---------------------------------------------------------------------------
# Step 6 — verify the API answers, from inside
# ---------------------------------------------------------------------------
step "6/7  Verifying the API"

# The readiness group, deliberately not the /actuator/health aggregate.
#
# The aggregate includes Spring's mail indicator, which tries to reach smtp.gmail.com. Mail
# is optional in this application -- everything except verification and password-reset
# email works without it -- but the indicator does not know that, so an unconfigured
# MAIL_USERNAME makes the aggregate return 503 forever. Gating on it would mean a stack
# that is running perfectly could never be deployed.
#
# Not reachable from the internet by design: Caddy only routes /api.
health_body="$(compose exec -T backend wget -q -O- http://localhost:8080/actuator/health/readiness)"
if ! grep -q '"status":"UP"' <<<"$health_body"; then
  fail "the backend is running but not ready to serve traffic: ${health_body}"
  exit 1
fi
ok "backend reports READY"

# This is the migration gate, and the reason it is a gate rather than a note: the
# readiness probe above does not touch the database, so on its own it cannot tell a
# successful migration from one that never ran. Reading the Flyway history proves three
# things at once -- MySQL is reachable, it accepted a credential, and the migrations
# recorded themselves as successful.
migration_count="$(compose exec -T mysql sh -c \
  'mysql -u root -p"$MYSQL_ROOT_PASSWORD" -N -B -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1" "$MYSQL_DATABASE"' \
  2>/dev/null | tr -d '\r' || true)"
if ! [[ "$migration_count" =~ ^[0-9]+$ ]] || (( migration_count == 0 )); then
  fail "could not read a successful Flyway history from the database (got: '${migration_count}').
      The backend is up, but its schema state cannot be confirmed. Do not assume this
      deployment migrated cleanly."
  exit 1
fi
ok "${migration_count} Flyway migrations applied successfully"

# A failed migration leaves a row with success = 0, and Flyway then refuses to start again
# until it is repaired. Surfacing it here beats discovering it on the next deploy.
failed_migrations="$(compose exec -T mysql sh -c \
  'mysql -u root -p"$MYSQL_ROOT_PASSWORD" -N -B -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0" "$MYSQL_DATABASE"' \
  2>/dev/null | tr -d '\r' || echo 0)"
if [[ "$failed_migrations" =~ ^[0-9]+$ ]] && (( failed_migrations > 0 )); then
  fail "${failed_migrations} migration(s) are recorded as failed. Flyway will refuse to run
      again until the history is repaired. Do not deploy over this."
  exit 1
fi

# ---------------------------------------------------------------------------
# Step 7 — verify what a real browser would get
# ---------------------------------------------------------------------------
step "7/7  Verifying the public site"

# Read rather than source: .env.prod holds secrets, and sourcing it would let a password
# containing a backtick or $(...) run as a command.
app_domain="$(grep -m1 -E '^APP_DOMAIN=' "$ENV_FILE" | cut -d= -f2-)"
app_domain="${app_domain%$'\r'}"
app_domain="${app_domain%\"}"; app_domain="${app_domain#\"}"
app_domain="${app_domain%\'}"; app_domain="${app_domain#\'}"
public_url="${DEPLOY_PUBLIC_URL:-https://${app_domain}}"

if ! command -v curl >/dev/null 2>&1; then
  fail "curl is not installed on this server, so the public site cannot be verified.
      The stack is up and the backend reports healthy -- only this last check is missing.
      Install curl (apt install curl) and re-run, or check ${public_url} by hand."
  exit 1
fi

curl_opts=(--silent --show-error --location --max-time 20)
if [[ "$app_domain" == "localhost" || "$app_domain" == "127.0.0.1" ]]; then
  # Caddy signs these with its own internal CA, which this host has no reason to trust.
  curl_opts+=(--insecure)
fi

frontend_body="$(curl "${curl_opts[@]}" "${public_url}/")"
if ! grep -q '<div id="root"' <<<"$frontend_body"; then
  fail "${public_url}/ did not return the SPA. Caddy may not have a certificate yet, or
      the DNS record for ${app_domain} does not point at this server."
  exit 1
fi
ok "frontend served over HTTPS at ${public_url}"

# A path that no controller maps. Spring Security's anyRequest().authenticated() rejects it
# before routing, so an unauthenticated caller gets a deterministic 403 -- no controller, no
# request body and no rate-limit budget involved. Two distinct failures are worth telling
# apart here, and the status code alone cannot do it:
#
#   5xx or 000  -- Caddy cannot reach the backend at all
#   the SPA     -- Caddy is routing /api to nginx instead, whose try_files falls back to
#                  index.html and returns a cheerful 200 for an API path
probe_body="$(curl "${curl_opts[@]}" --write-out '\n%{http_code}' "${public_url}/api/v1/__deploy-probe" || echo $'\n000')"
api_status="$(tail -n1 <<<"$probe_body")"
if [[ "$api_status" == "000" || "$api_status" =~ ^5 ]]; then
  fail "${public_url}/api/ returned ${api_status}; Caddy is not reaching the backend."
  exit 1
fi
if grep -q '<div id="root"' <<<"$probe_body"; then
  fail "${public_url}/api/ served the single-page app instead of reaching the backend.
      Caddy's /api route is not matching, so every API call will silently return HTML."
  exit 1
fi
ok "API reachable same-origin at ${public_url}/api/ (HTTP ${api_status} from Spring)"

trap - ERR
printf '\n\033[1;32mDEPLOYED\033[0m  %s is live at %s\n\n' "$IMAGE_TAG" "$public_url"
