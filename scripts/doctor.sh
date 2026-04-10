#!/usr/bin/env bash
# =============================================================================
# ScanVault — doctor.sh
# Health-checks the development environment and reports status.
# Does NOT install anything — only reports what's missing or broken.
#
# Usage:
#   ./scripts/doctor.sh          # full health check
#   ./scripts/doctor.sh --help   # show this message
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
doctor.sh — ScanVault dev environment health check

USAGE:
  ./scripts/doctor.sh [OPTIONS]

OPTIONS:
  --help, -h    Show this help message and exit

CHECKS:
  Tool versions:
    • JDK 21+
    • Android SDK  platform 35, build-tools 35.0.0, NDK 27.x
    • Go 1.23+
    • Docker daemon running
    • docker compose available
    • Python 3.12+
    • pre-commit hooks installed
    • goose, sqlc, golangci-lint, air, gosec, govulncheck
    • ADB (and connected device count)

  Runtime health:
    • Postgres container reachable (via docker compose)
    • Redis container reachable (via docker compose)
    • All required backend .env vars present

  Repository state:
    • pre-commit hooks installed in .git/hooks
    • VERSION file readable

EXIT CODE:
  0  All checks passed
  1  One or more checks failed
EOF
  exit 0
fi

# -----------------------------------------------------------------------------
# Colours & counters
# -----------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PASS=0
FAIL=0
WARN=0

pass() { PASS=$((PASS+1)); printf "  ${GREEN}✓${NC} %s\n" "$*"; }
fail() { FAIL=$((FAIL+1)); printf "  ${RED}✗${NC} %s\n" "$*"; }
warn() { WARN=$((WARN+1)); printf "  ${YELLOW}!${NC} %s\n" "$*"; }
section() { echo ""; printf "${BLUE}── %s ──${NC}\n" "$*"; }

# -----------------------------------------------------------------------------
# JDK 21+
# -----------------------------------------------------------------------------
section "JDK"
if command -v java &>/dev/null; then
  JAVA_VER=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1)
  if [[ "$JAVA_VER" -ge 21 ]]; then
    pass "JDK $JAVA_VER ($(java -version 2>&1 | head -1))"
  else
    fail "JDK $JAVA_VER found, need ≥ 21  →  sdk install java 21.0.3-tem"
  fi
else
  fail "java not found  →  install JDK 21 via SDKMAN or brew"
fi

# -----------------------------------------------------------------------------
# Android SDK
# -----------------------------------------------------------------------------
section "Android SDK"
ANDROID_HOME="${ANDROID_HOME:-${HOME}/Android/Sdk}"
if [[ -d "$ANDROID_HOME" ]]; then
  pass "ANDROID_HOME = $ANDROID_HOME"
else
  fail "ANDROID_HOME not set or $ANDROID_HOME doesn't exist"
fi

if [[ -d "${ANDROID_HOME:-}/platforms/android-35" ]]; then
  pass "Platform android-35 installed"
else
  fail "Platform android-35 missing  →  sdkmanager 'platforms;android-35'"
fi

if ls "${ANDROID_HOME:-}/build-tools/35"* &>/dev/null 2>&1; then
  pass "Build-tools 35.x installed"
else
  fail "Build-tools 35.x missing  →  sdkmanager 'build-tools;35.0.0'"
fi

NDK_DIR="${ANDROID_HOME:-}/ndk"
if ls "$NDK_DIR"/27.* &>/dev/null 2>&1; then
  NDK_VER=$(ls "$NDK_DIR" | grep '^27\.' | head -1)
  pass "NDK $NDK_VER installed"
else
  fail "NDK 27.x missing  →  sdkmanager 'ndk;27.0.12077973'"
fi

if command -v adb &>/dev/null; then
  DEVICE_COUNT=$(adb devices 2>/dev/null | grep -c 'device$' || true)
  if [[ "$DEVICE_COUNT" -gt 0 ]]; then
    pass "ADB: $DEVICE_COUNT device(s) connected"
  else
    warn "ADB found but no devices connected (OK if not testing on device)"
  fi
else
  warn "adb not in PATH (add Android/Sdk/platform-tools to PATH)"
fi

# -----------------------------------------------------------------------------
# Go
# -----------------------------------------------------------------------------
section "Go"
if command -v go &>/dev/null; then
  GO_VER=$(go version | grep -oE 'go[0-9]+\.[0-9]+' | grep -oE '[0-9]+\.[0-9]+')
  GO_MAJ=$(echo "$GO_VER" | cut -d. -f1)
  GO_MIN=$(echo "$GO_VER" | cut -d. -f2)
  if [[ "$GO_MAJ" -gt 1 ]] || [[ "$GO_MAJ" -eq 1 && "$GO_MIN" -ge 23 ]]; then
    pass "Go $GO_VER ($(go env GOPATH))"
  else
    fail "Go $GO_VER found, need ≥ 1.23  →  https://go.dev/dl/"
  fi
else
  fail "go not found  →  https://go.dev/dl/"
fi

# Go tools
for tool in goose sqlc golangci-lint air gosec govulncheck; do
  if command -v "$tool" &>/dev/null; then
    pass "$tool found"
  else
    fail "$tool not found  →  run ./scripts/bootstrap.sh"
  fi
done

# -----------------------------------------------------------------------------
# Docker
# -----------------------------------------------------------------------------
section "Docker"
if command -v docker &>/dev/null; then
  if docker info &>/dev/null 2>&1; then
    DOCKER_VER=$(docker --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
    pass "Docker $DOCKER_VER daemon running"
  else
    fail "Docker installed but daemon not running  →  start Docker Desktop"
  fi
else
  fail "docker not found  →  https://docs.docker.com/get-docker/"
fi

if docker compose version &>/dev/null 2>&1; then
  pass "docker compose (v2) available"
elif command -v docker-compose &>/dev/null; then
  pass "docker-compose (v1) available"
else
  fail "docker compose not found  →  update Docker Desktop"
fi

# -----------------------------------------------------------------------------
# Python 3.12+
# -----------------------------------------------------------------------------
section "Python"
PY_OK=false
for cmd in python3.12 python3 python; do
  if command -v "$cmd" &>/dev/null; then
    PY_VER=$("$cmd" --version 2>&1 | grep -oE '[0-9]+\.[0-9]+' | head -1)
    PY_MAJ=$(echo "$PY_VER" | cut -d. -f1)
    PY_MIN=$(echo "$PY_VER" | cut -d. -f2)
    if [[ "$PY_MAJ" -ge 3 && "$PY_MIN" -ge 12 ]]; then
      pass "Python $PY_VER ($cmd)"
      PY_OK=true
      break
    fi
  fi
done
if ! $PY_OK; then
  fail "Python 3.12+ not found  →  https://www.python.org/downloads/"
fi

# pre-commit
if command -v pre-commit &>/dev/null; then
  PC_VER=$(pre-commit --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
  pass "pre-commit $PC_VER"
else
  fail "pre-commit not found  →  pip install pre-commit"
fi

# -----------------------------------------------------------------------------
# pre-commit hooks installed in repo
# -----------------------------------------------------------------------------
section "Repository hooks"
HOOK_FILE="$ROOT/.git/hooks/commit-msg"
if [[ -f "$HOOK_FILE" ]]; then
  pass "commit-msg hook installed"
else
  fail "commit-msg hook not installed  →  pre-commit install --hook-type commit-msg"
fi

PRE_COMMIT_HOOK="$ROOT/.git/hooks/pre-commit"
if [[ -f "$PRE_COMMIT_HOOK" ]]; then
  pass "pre-commit hook installed"
else
  warn "pre-commit hook not installed  →  pre-commit install"
fi

# -----------------------------------------------------------------------------
# Container health (Postgres + Redis)
# -----------------------------------------------------------------------------
section "Backend containers (docker compose)"
COMPOSE_FILE="$ROOT/backend/docker-compose.yml"
if [[ -f "$COMPOSE_FILE" ]]; then
  cd "$ROOT/backend"
  PG_RUNNING=$(docker compose ps --status running 2>/dev/null | grep -c 'postgres\|db' || true)
  REDIS_RUNNING=$(docker compose ps --status running 2>/dev/null | grep -c 'redis' || true)

  if [[ "$PG_RUNNING" -gt 0 ]]; then
    pass "Postgres container running"
  else
    warn "Postgres container not running (start with: ./scripts/run-backend-local.sh)"
  fi

  if [[ "$REDIS_RUNNING" -gt 0 ]]; then
    pass "Redis container running"
  else
    warn "Redis container not running (start with: ./scripts/run-backend-local.sh)"
  fi
  cd "$ROOT"
else
  warn "backend/docker-compose.yml not found yet (created in task 1A.21)"
fi

# -----------------------------------------------------------------------------
# backend .env vars
# -----------------------------------------------------------------------------
section "Backend environment"
ENV_FILE="$ROOT/backend/.env"
if [[ -f "$ENV_FILE" ]]; then
  pass "backend/.env exists"
  REQUIRED_VARS=(
    "DATABASE_URL"
    "REDIS_URL"
    "JWT_SECRET"
    "PORT"
  )
  for var in "${REQUIRED_VARS[@]}"; do
    if grep -qE "^${var}=.+" "$ENV_FILE" 2>/dev/null; then
      pass "  $var set"
    else
      warn "  $var not set in backend/.env"
    fi
  done
else
  warn "backend/.env not found — run ./scripts/bootstrap.sh to create it"
fi

# -----------------------------------------------------------------------------
# VERSION file
# -----------------------------------------------------------------------------
section "Repository"
if [[ -f "$ROOT/VERSION" ]]; then
  VER=$(cat "$ROOT/VERSION")
  pass "VERSION = $VER"
else
  fail "VERSION file missing"
fi

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------
TOTAL=$((PASS + FAIL + WARN))
echo ""
echo "══════════════════════════════════════════════"
if [[ "$FAIL" -eq 0 && "$WARN" -eq 0 ]]; then
  printf "${GREEN}ALL $TOTAL CHECKS PASSED${NC}\n"
elif [[ "$FAIL" -eq 0 ]]; then
  printf "${YELLOW}$PASS passed, $WARN warnings (no failures)${NC}\n"
else
  printf "${RED}$FAIL failed${NC}, ${YELLOW}$WARN warnings${NC}, ${GREEN}$PASS passed${NC} ($TOTAL total)\n"
  printf "Run ${YELLOW}./scripts/bootstrap.sh${NC} to fix missing tools.\n"
fi
echo "══════════════════════════════════════════════"

[[ "$FAIL" -eq 0 ]]
