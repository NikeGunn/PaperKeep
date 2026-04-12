#!/usr/bin/env bash
# =============================================================================
# ScanVault — dev-check.sh
# Fast pre-flight: are you ready to run dev.sh right now?
# Runs in ~3 seconds. Run this before starting a dev session.
#
# Usage:
#   ./scripts/dev-check.sh
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PASS=0; FAIL=0; WARN=0

pass() { PASS=$((PASS+1)); printf "  ${GREEN}✓${NC} %-36s %s\n" "$1" "${2:-}"; }
fail() { FAIL=$((FAIL+1)); printf "  ${RED}✗${NC} %-36s ${RED}%s${NC}\n" "$1" "${2:-FIX THIS}"; }
warn() { WARN=$((WARN+1)); printf "  ${YELLOW}⚠${NC}  %-36s ${YELLOW}%s${NC}\n" "$1" "${2:-}"; }
section() { echo ""; printf "${BLUE}${BOLD}── %s${NC}\n" "$*"; }

# ── ADB ───────────────────────────────────────────────────────────────────────
ADB_BIN=""
if command -v adb &>/dev/null; then ADB_BIN="adb";
elif [[ -f "/c/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools/adb.exe" ]]; then
  ADB_BIN="/c/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools/adb.exe"
fi

# ── Network ───────────────────────────────────────────────────────────────────
section "Network"
LAN_IP="${BACKEND_HOST:-}"
if [[ -z "$LAN_IP" ]] && command -v cmd.exe &>/dev/null; then
  LAN_IP=$(timeout 5 cmd.exe //C ipconfig 2>/dev/null \
    | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' \
    | grep '^192\.168\.' | grep -v '\.64\.' \
    | head -1 || true)
  if [[ -z "$LAN_IP" ]]; then
    LAN_IP=$(timeout 5 cmd.exe //C ipconfig 2>/dev/null \
      | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' \
      | grep -v '^127\.' | grep -v '^169\.' | grep -v '^172\.' | grep -v '^192\.168\.64\.' \
      | head -1 || true)
  fi
fi
if [[ -z "$LAN_IP" ]]; then
  LAN_IP=$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K\S+' | head -1 || echo "")
fi

if [[ -n "$LAN_IP" && "$LAN_IP" != "127.0.0.1" ]]; then
  pass "LAN IP detected" "$LAN_IP"
else
  warn "No LAN IP found" "Connect Ethernet or set BACKEND_HOST"
fi

# ── Docker ────────────────────────────────────────────────────────────────────
section "Docker (backend)"
if command -v docker &>/dev/null; then
  if docker info &>/dev/null 2>&1; then
    pass "Docker daemon running"
    # Check containers
    if [[ -f "$ROOT/backend/docker-compose.yml" ]]; then
      cd "$ROOT/backend"
      PG_STATUS=$(docker compose ps --status running 2>/dev/null | { grep -c 'db\|postgres' || true; })
      RD_STATUS=$(docker compose ps --status running 2>/dev/null | { grep -c 'redis' || true; })
      cd "$ROOT"
      if [[ "$PG_STATUS" -gt 0 ]]; then
        pass "Postgres container" "running"
      else
        warn "Postgres container" "not running (dev.sh will start it)"
      fi
      if [[ "$RD_STATUS" -gt 0 ]]; then
        pass "Redis container" "running"
      else
        warn "Redis container" "not running (dev.sh will start it)"
      fi
    fi
  else
    fail "Docker daemon" "not running — start Docker Desktop"
  fi
else
  fail "Docker" "not installed"
fi

# ── Go / air ──────────────────────────────────────────────────────────────────
section "Backend (Go)"
if command -v go &>/dev/null; then
  GO_VER=$(go version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
  pass "Go" "$GO_VER"
else
  fail "Go" "not found — install from go.dev"
fi

AIR_OK=false
for p in air "$HOME/go/bin/air" "/c/Users/Nautilus/go/bin/air" "/c/Users/Nautilus/go/bin/air.exe"; do
  if command -v "$p" &>/dev/null || [[ -f "$p" ]]; then AIR_OK=true; break; fi
done
if $AIR_OK; then
  pass "air (hot-reload)" "installed"
else
  warn "air" "not found — go install github.com/air-verse/air@latest"
fi

if [[ -f "$ROOT/backend/.env" ]]; then
  DB_SET=$(grep -cE '^DATABASE_URL=.+' "$ROOT/backend/.env" 2>/dev/null || true)
  if [[ "$DB_SET" -gt 0 ]]; then
    pass "backend/.env" "DATABASE_URL set"
  else
    warn "backend/.env" "DATABASE_URL not set"
  fi
else
  fail "backend/.env" "missing — cp backend/.env.example backend/.env"
fi

# ── Android ───────────────────────────────────────────────────────────────────
section "Android"
if [[ -f "$ROOT/android/gradlew" ]]; then
  pass "Android project" "gradlew found"
else
  fail "android/gradlew" "missing — Phase 1B not complete"
fi

if [[ -n "$ADB_BIN" ]]; then
  DEVICE_LIST=$("$ADB_BIN" devices 2>/dev/null | grep -v '^List' | { grep 'device$' || true; } | awk '{print $1}')
  UNAUTH=$("$ADB_BIN" devices 2>/dev/null | { grep 'unauthorized' || true; } | awk '{print $1}')

  DEVICE_COUNT=$(echo "$DEVICE_LIST" | { grep -c '.' || true; })
  if [[ "$DEVICE_COUNT" -gt 0 ]]; then
    DEVICE_SERIAL=$(echo "$DEVICE_LIST" | head -1)
    DEVICE_MODEL=$("$ADB_BIN" -s "$DEVICE_SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "?")
    ANDROID_VER=$("$ADB_BIN" -s "$DEVICE_SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "?")
    pass "Phone connected" "$DEVICE_MODEL (Android $ANDROID_VER)"
    WIFI_CONNECTED=$("$ADB_BIN" devices 2>/dev/null | { grep -c '\.' || true; })
    if [[ "$WIFI_CONNECTED" -gt 0 ]]; then
      pass "Wi-Fi ADB" "active"
    fi
  elif [[ -n "$UNAUTH" ]]; then
    warn "Phone connected but unauthorized" "Accept debug prompt on phone"
  else
    warn "No phone detected" "USB: connect cable + enable USB debugging"
    WIFI_CONFIG="$HOME/.scanvault-wifi-adb"
    if [[ -f "$WIFI_CONFIG" ]]; then
      SAVED=$(grep '^WIFI_ADB_ADDRESS=' "$WIFI_CONFIG" | cut -d= -f2 | tr -d '[:space:]' || true)
      [[ -n "$SAVED" ]] && warn "Saved Wi-Fi address" "$SAVED (try: dev.sh --wifi)"
    fi
  fi
else
  fail "adb" "not found — add Android SDK platform-tools to PATH"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════"
TOTAL=$((PASS + FAIL + WARN))
if [[ "$FAIL" -eq 0 ]]; then
  printf "${GREEN}${BOLD}Ready!${NC} ${PASS}/${TOTAL} checks passed"
  [[ "$WARN" -gt 0 ]] && printf " (${YELLOW}${WARN} warnings${NC})"
  echo ""
  echo ""
  if [[ -n "$LAN_IP" ]]; then
    printf "Run now:  ${GREEN}${BOLD}./scripts/dev.sh${NC}\n"
    printf "Wi-Fi:    ${CYAN}./scripts/dev.sh --wifi${NC}\n"
    printf "No phone: ${CYAN}./scripts/dev.sh --backend-only${NC}\n"
  fi
else
  printf "${RED}${FAIL} check(s) failed.${NC} Fix them, then run: ${YELLOW}./scripts/dev.sh${NC}\n"
fi
echo "══════════════════════════════════════════════"
echo ""

[[ "$FAIL" -eq 0 ]]
