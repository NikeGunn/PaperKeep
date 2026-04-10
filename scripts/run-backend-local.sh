#!/usr/bin/env bash
# =============================================================================
# ScanVault — run-backend-local.sh
# Starts the full backend stack for local development.
#
# Usage:
#   ./scripts/run-backend-local.sh           # start everything
#   ./scripts/run-backend-local.sh --help    # show this message
#   ./scripts/run-backend-local.sh --migrate-only  # run migrations and exit
#   ./scripts/run-backend-local.sh --no-migrate    # skip migrations
#
# What it does:
#   1. Starts docker-compose (Postgres + Redis) if not already running
#   2. Waits for Postgres to be healthy (up to 60s)
#   3. Waits for Redis to be healthy (up to 30s)
#   4. Runs goose migrations
#   5. Runs sqlc generate if schema files changed
#   6. Starts the Go API with air (hot-reload)
#   7. Prints LAN IP + port so the Android phone can connect
#
# On Ctrl+C:
#   Gracefully stops the Go process + air, but LEAVES docker-compose running
#   so the next start-up is fast (no container rebuild).
#   To stop containers: docker compose -f backend/docker-compose.yml down
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
run-backend-local.sh — Start the ScanVault backend for local development

USAGE:
  ./scripts/run-backend-local.sh [OPTIONS]

OPTIONS:
  --help, -h        Show this help message and exit
  --migrate-only    Run migrations then exit (no server start)
  --no-migrate      Skip migrations (use if DB is already up to date)
  --port PORT       Override port (default: 8080)
  --env FILE        Path to .env file (default: backend/.env)

DEPENDENCIES (all must be installed):
  docker, docker compose, go, goose, sqlc, air
  Run ./scripts/bootstrap.sh to install missing tools.

STOPPING:
  Ctrl+C stops the Go server but leaves Postgres + Redis running.
  To stop everything: docker compose -f backend/docker-compose.yml down

CONNECTING FROM PHONE:
  The script prints the LAN IP after startup.
  In run-phone.sh use: --backend http://<LAN-IP>:8080
EOF
  exit 0
fi

# -----------------------------------------------------------------------------
# Parse flags
# -----------------------------------------------------------------------------
MIGRATE_ONLY=false
SKIP_MIGRATE=false
PORT="${PORT:-8080}"
ENV_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --migrate-only) MIGRATE_ONLY=true; shift ;;
    --no-migrate)   SKIP_MIGRATE=true;  shift ;;
    --port)         PORT="$2"; shift 2  ;;
    --env)          ENV_FILE="$2"; shift 2 ;;
    *) echo "Unknown flag: $1 (use --help)"; exit 1 ;;
  esac
done

# -----------------------------------------------------------------------------
# Colours & helpers
# -----------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT/backend"

info()    { printf "${BLUE}[backend]${NC} %s\n" "$*"; }
ok()      { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
warn()    { printf "  ${YELLOW}!${NC} %s\n" "$*"; }
die()     { printf "  ${RED}✗${NC} %s\n" "$*"; exit 1; }

# -----------------------------------------------------------------------------
# Prerequisite checks
# -----------------------------------------------------------------------------
for cmd in docker go goose; do
  command -v "$cmd" &>/dev/null || die "$cmd not found — run ./scripts/bootstrap.sh"
done

if ! docker info &>/dev/null 2>&1; then
  die "Docker daemon not running — start Docker Desktop first"
fi

if [[ ! -d "$BACKEND_DIR" ]]; then
  die "backend/ directory not found (have you created it yet?)"
fi

# Determine compose file
COMPOSE_FILE="$BACKEND_DIR/docker-compose.yml"
if [[ ! -f "$COMPOSE_FILE" ]]; then
  die "backend/docker-compose.yml not found (created in task 1A.21)"
fi

# Load .env
if [[ -z "$ENV_FILE" ]]; then
  ENV_FILE="$BACKEND_DIR/.env"
fi
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  ok "Loaded $ENV_FILE"
else
  warn ".env not found at $ENV_FILE — using environment variables as-is"
fi

# -----------------------------------------------------------------------------
# 1. Start docker-compose
# -----------------------------------------------------------------------------
info "Starting Postgres + Redis via docker compose..."

cd "$BACKEND_DIR"
docker compose up -d --remove-orphans 2>&1 | grep -v '^#' | grep -v '^$' | sed 's/^/  /' || true
ok "docker compose started"

# -----------------------------------------------------------------------------
# 2. Wait for Postgres to be healthy
# -----------------------------------------------------------------------------
info "Waiting for Postgres to be healthy..."
PG_RETRIES=60
PG_READY=false
for i in $(seq 1 $PG_RETRIES); do
  if docker compose exec -T db pg_isready -q 2>/dev/null; then
    PG_READY=true
    ok "Postgres ready after ${i}s"
    break
  fi
  sleep 1
done
if ! $PG_READY; then
  die "Postgres not ready after ${PG_RETRIES}s — check: docker compose logs db"
fi

# -----------------------------------------------------------------------------
# 3. Wait for Redis to be healthy
# -----------------------------------------------------------------------------
info "Waiting for Redis to be healthy..."
REDIS_RETRIES=30
REDIS_READY=false
for i in $(seq 1 $REDIS_RETRIES); do
  if docker compose exec -T redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
    REDIS_READY=true
    ok "Redis ready after ${i}s"
    break
  fi
  sleep 1
done
if ! $REDIS_READY; then
  warn "Redis not ready after ${REDIS_RETRIES}s — continuing anyway (check: docker compose logs redis)"
fi

# -----------------------------------------------------------------------------
# 4. Run goose migrations
# -----------------------------------------------------------------------------
if ! $SKIP_MIGRATE; then
  info "Running goose migrations..."
  if [[ -n "${DATABASE_URL:-}" ]]; then
    cd "$BACKEND_DIR"
    if [[ -d "db/migrations" ]]; then
      goose -dir db/migrations postgres "$DATABASE_URL" up \
        && ok "Migrations applied"
    else
      warn "db/migrations/ not found yet (created in task 1A.5) — skipping"
    fi
  else
    warn "DATABASE_URL not set — skipping migrations"
  fi
fi

if $MIGRATE_ONLY; then
  ok "Migration-only mode — done."
  exit 0
fi

# -----------------------------------------------------------------------------
# 5. sqlc generate if schema changed
# -----------------------------------------------------------------------------
info "Checking sqlc..."
if command -v sqlc &>/dev/null; then
  if [[ -f "$BACKEND_DIR/sqlc.yaml" ]]; then
    cd "$BACKEND_DIR"
    sqlc generate 2>&1 | sed 's/^/  /' || warn "sqlc generate had warnings"
    ok "sqlc up to date"
  else
    warn "sqlc.yaml not found yet (created in task 1A.6) — skipping"
  fi
else
  warn "sqlc not found — run ./scripts/bootstrap.sh"
fi

# -----------------------------------------------------------------------------
# 6. Print LAN IP for phone connectivity
# -----------------------------------------------------------------------------
LAN_IP=""
if command -v ip &>/dev/null; then
  LAN_IP=$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K[^\s]+' | head -1)
elif command -v ifconfig &>/dev/null; then
  LAN_IP=$(ifconfig | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1)
fi
LAN_IP="${LAN_IP:-localhost}"

# -----------------------------------------------------------------------------
# 7. Start Go API with air hot-reload
# -----------------------------------------------------------------------------
info "Starting Go API with air hot-reload..."
echo ""
printf "${CYAN}══════════════════════════════════════════${NC}\n"
printf "${CYAN}  Backend URL (localhost):${NC} http://localhost:${PORT}\n"
if [[ "$LAN_IP" != "localhost" ]]; then
  printf "${CYAN}  Backend URL (phone/LAN): ${NC} http://${LAN_IP}:${PORT}\n"
  printf "${CYAN}  Run phone with:${NC}          ./scripts/run-phone.sh --backend http://${LAN_IP}:${PORT}\n"
fi
printf "${CYAN}══════════════════════════════════════════${NC}\n"
printf "${YELLOW}  Press Ctrl+C to stop the API (containers stay running)${NC}\n"
printf "${YELLOW}  To stop containers: docker compose -f backend/docker-compose.yml down${NC}\n"
echo ""

# Trap Ctrl+C — stop air gracefully but leave docker-compose running
cleanup() {
  echo ""
  info "Shutting down Go API (containers stay running)..."
  kill "$AIR_PID" 2>/dev/null || true
  wait "$AIR_PID" 2>/dev/null || true
  ok "API stopped. Postgres + Redis still running."
  info "To stop containers: docker compose -f backend/docker-compose.yml down"
}
trap cleanup INT TERM

cd "$BACKEND_DIR"
if command -v air &>/dev/null; then
  AIR_ARGS=""
  if [[ -f "$BACKEND_DIR/.air.toml" ]]; then
    AIR_ARGS="-c .air.toml"
  fi
  PORT="$PORT" air $AIR_ARGS &
  AIR_PID=$!
  wait "$AIR_PID"
else
  warn "air not found — falling back to plain 'go run'"
  warn "Install air: go install github.com/air-verse/air@latest"
  PORT="$PORT" go run ./cmd/api &
  AIR_PID=$!
  wait "$AIR_PID"
fi
