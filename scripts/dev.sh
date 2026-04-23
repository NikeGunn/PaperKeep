#!/usr/bin/env bash
# =============================================================================
# ScanVault — dev.sh
# ONE command to run your entire dev environment and see the app on your phone.
#
# What it does:
#   1. Detects your LAN IP automatically (192.168.x.x)
#   2. Starts Postgres + Redis via docker compose
#   3. Runs Go migrations
#   4. Starts Go API with hot-reload (air) — auto-restarts on .go file changes
#   5. Builds the Android debug APK with your LAN IP baked in
#   6. Installs & launches on your connected phone (USB or Wi-Fi)
#   7. Streams colourised logcat in the same terminal
#
# Everything runs in ONE terminal. Ctrl+C stops all processes cleanly.
#
# Usage:
#   ./scripts/dev.sh               # full stack (backend + phone)
#   ./scripts/dev.sh --phone-only  # skip backend, just rebuild+deploy the app
#   ./scripts/dev.sh --backend-only # just the Go API, no Android build
#   ./scripts/dev.sh --wifi        # use Wi-Fi ADB instead of USB cable
#   ./scripts/dev.sh --no-install  # build APK but don't install (for testing)
#   ./scripts/dev.sh --watch       # rebuild APK on every Kotlin file save too
#   ./scripts/dev.sh --backend-exit-policy always
#   ./scripts/dev.sh --help
# =============================================================================

set -euo pipefail

# ── Help ──────────────────────────────────────────────────────────────────────
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
dev.sh — ScanVault full-stack dev loop

USAGE:
  ./scripts/dev.sh [OPTIONS]

OPTIONS:
  --help, -h          Show this message and exit
  --backend-only      Start backend only (Postgres + Redis + Go API hot-reload)
  --phone-only        Build + deploy Android app only (backend must already run)
  --staging           Build APK pointing at real AWS staging API (no local backend needed)
                      URL: https://4dbidumnq3.execute-api.ap-south-1.amazonaws.com/v1
                      Use this to test end-to-end on your phone against live AWS infra.
  --wifi              Connect phone via Wi-Fi ADB (Android 11+). Cable-free.
  --no-install        Build APK but do not install on device
  --watch             Watch Kotlin files and auto-redeploy on change (uses inotifywait)
  --clean             Clean Gradle build cache before building
  --device SERIAL     Target a specific ADB device serial
  --port PORT         Override backend port (default: 8080)
  --verbose           Show full Gradle output (default: quiet)
  --backend-exit-policy POLICY
                      Backend shutdown mode on script exit:
                        auto   (default): stop on Ctrl+C/SIGTERM only
                        always: always stop backend when script exits
                        never : never stop backend automatically

NETWORK:
  Your phone and laptop must be on the SAME network (LAN cable for laptop is fine,
  Wi-Fi for phone is fine — they route through the same router).

  LAN IP is auto-detected from your Ethernet adapter (192.168.1.x range).
  Override with: BACKEND_HOST=192.168.1.5 ./scripts/dev.sh

FIRST-TIME SETUP:
  1. Enable USB debugging on your phone
     Settings → About Phone → tap Build Number 7× → Developer Options → USB Debugging ON
  2. Connect USB cable, accept the "Allow USB debugging?" prompt on the phone
  3. Run: ./scripts/dev.sh

WI-FI SETUP (cable-free after first time):
  1. Connect USB cable once, accept debugging prompt
  2. Run: ./scripts/dev-wifi-pair.sh   ← one-time pairing
  3. From now on: ./scripts/dev.sh --wifi  (no cable needed)

HOT RELOAD:
  Backend (Go):    Automatic via air — edit any .go file, API restarts in ~1s
  Android (Kotlin): NOT automatic by default (Compose does not support hot-swap
                   outside AS). Use --watch to auto-redeploy on file save (~30s).
                   For fast UI iteration: open android/ in Android Studio and use
                   "Apply Changes" (⌘R / Ctrl+R) while this script streams logcat.

WHAT'S RUNNING:
  • Postgres + Redis:  docker compose (backend/docker-compose.yml)
  • Go API:            air hot-reload on port 8080
  • Android logcat:   filtered to com.scanvault.app.*

STOPPING:
  Ctrl+C — stops Go API + logcat. Postgres + Redis keep running for fast restart.
  If logcat/ADB disconnects unexpectedly, backend is kept alive by default.
  To also stop containers: docker compose -f backend/docker-compose.yml down

ENV OVERRIDES:
  DEV_BACKEND_EXIT_POLICY=auto|always|never
EOF
  exit 0
fi

# ── Colours & helpers ─────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/android"
BACKEND_DIR="$ROOT/backend"
APP_PACKAGE="com.scanvault.app"
APP_PACKAGE_DEBUG="${APP_PACKAGE}.debug"

info()    { printf "${BLUE}[dev]${NC} %s\n" "$*"; }
ok()      { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
warn()    { printf "  ${YELLOW}⚠${NC}  %s\n" "$*"; }
die()     { printf "  ${RED}✗${NC} %s\n" "$*"; exit 1; }
banner()  { echo ""; printf "${CYAN}${BOLD}══ %s ══${NC}\n" "$*"; }
step()    { echo ""; printf "${YELLOW}▶ %s${NC}\n" "$*"; }

LIFECYCLE_LIB="$ROOT/scripts/lib/dev-lifecycle.sh"
[[ -f "$LIFECYCLE_LIB" ]] || die "Missing lifecycle helper: $LIFECYCLE_LIB"
# shellcheck disable=SC1090
source "$LIFECYCLE_LIB"

# ── Parse flags ───────────────────────────────────────────────────────────────
MODE="full"          # full | phone-only | backend-only
DO_WIFI=false
DO_INSTALL=true
DO_WATCH=false
DO_CLEAN=false
DO_VERBOSE=false
DO_STAGING=false
TARGET_DEVICE=""
PORT="${PORT:-8080}"
BACKEND_EXIT_POLICY="${DEV_BACKEND_EXIT_POLICY:-auto}"
# Real AWS staging endpoint — no local backend needed when --staging is used
STAGING_API_URL="https://4dbidumnq3.execute-api.ap-south-1.amazonaws.com"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend-only)  MODE="backend-only"; shift ;;
    --phone-only)    MODE="phone-only"; shift ;;
    --staging)       DO_STAGING=true; MODE="phone-only"; shift ;;
    --wifi)          DO_WIFI=true; shift ;;
    --no-install)    DO_INSTALL=false; shift ;;
    --watch)         DO_WATCH=true; shift ;;
    --clean)         DO_CLEAN=true; shift ;;
    --verbose)       DO_VERBOSE=true; shift ;;
    --device)        TARGET_DEVICE="$2"; shift 2 ;;
    --port)          PORT="$2"; shift 2 ;;
    --backend-exit-policy) BACKEND_EXIT_POLICY="$2"; shift 2 ;;
    *) die "Unknown flag: $1 (use --help)" ;;
  esac
done

# --staging overrides the API URL baked into the APK
if $DO_STAGING; then
  BACKEND_URL="$STAGING_API_URL"
  MODE="phone-only"
fi

case "$BACKEND_EXIT_POLICY" in
  auto|always|never) ;;
  *) die "Invalid --backend-exit-policy: $BACKEND_EXIT_POLICY (use auto|always|never)" ;;
esac

# ── ADB path resolution (Windows + WSL + native Linux/Mac) ───────────────────
find_adb() {
  # 1. Already in PATH
  if command -v adb &>/dev/null; then echo "adb"; return; fi
  # 2. Windows SDK default location (running in Git Bash / MSYS2)
  local win_sdk="/c/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools/adb.exe"
  if [[ -f "$win_sdk" ]]; then echo "$win_sdk"; return; fi
  # 3. Windows SDK via ANDROID_HOME env
  if [[ -n "${ANDROID_HOME:-}" && -f "$ANDROID_HOME/platform-tools/adb.exe" ]]; then
    echo "$ANDROID_HOME/platform-tools/adb.exe"; return
  fi
  if [[ -n "${ANDROID_HOME:-}" && -f "$ANDROID_HOME/platform-tools/adb" ]]; then
    echo "$ANDROID_HOME/platform-tools/adb"; return
  fi
  # 4. Common Linux paths
  for p in "$HOME/Android/Sdk/platform-tools/adb" "/usr/bin/adb"; do
    if [[ -f "$p" ]]; then echo "$p"; return; fi
  done
  echo ""
}

ADB_BIN="$(find_adb)"
if [[ -z "$ADB_BIN" ]]; then
  die "adb not found. Add Android SDK platform-tools to PATH or set ANDROID_HOME."
fi

adb_cmd() { "$ADB_BIN" "$@"; }

resolve_launcher_component() {
  local serial="$1"
  local pkg=""
  local resolved=""

  for pkg in "$APP_PACKAGE_DEBUG" "$APP_PACKAGE"; do
    resolved="$(adb_cmd -s "$serial" shell cmd package resolve-activity --brief \
      -a android.intent.action.MAIN \
      -c android.intent.category.LAUNCHER \
      "$pkg" 2>/dev/null | tr -d '\r' | grep '/' | tail -1 || true)"
    if [[ -n "$resolved" ]]; then
      echo "$resolved"
      return 0
    fi
  done

  return 1
}

start_component() {
  local serial="$1"
  local component="$2"
  local launch_out=""
  local launch_exit=0

  set +e
  launch_out="$(adb_cmd -s "$serial" shell am start -n "$component" --activity-clear-top 2>&1)"
  launch_exit=$?
  set -e

  printf "%s\n" "$launch_out" | sed 's/^/  /'
  return "$launch_exit"
}

# ── LAN IP detection ──────────────────────────────────────────────────────────
detect_lan_ip() {
  # Honour explicit override first
  [[ -n "${BACKEND_HOST:-}" ]] && { echo "$BACKEND_HOST"; return; }

  local ip=""

  # Windows / MSYS2 / Git Bash — use cmd.exe //C (double slash required in MSYS2)
  # Filter out Hyper-V virtual switches (192.168.64.x, 172.x.x.x) and loopback
  if command -v cmd.exe &>/dev/null; then
    ip=$(timeout 5 cmd.exe //C ipconfig 2>/dev/null \
      | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' \
      | grep '^192\.168\.' \
      | grep -v '\.64\.' \
      | head -1 || true)
    # Broader fallback: any non-virtual IPv4
    if [[ -z "$ip" ]]; then
      ip=$(timeout 5 cmd.exe //C ipconfig 2>/dev/null \
        | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' \
        | grep -v '^127\.' | grep -v '^169\.' | grep -v '^172\.' \
        | grep -v '^192\.168\.64\.' \
        | head -1 || true)
    fi
  fi

  # Linux / macOS fallback
  if [[ -z "$ip" ]]; then
    ip=$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K\S+' | head -1 || true)
  fi

  echo "${ip:-127.0.0.1}"
}

LAN_IP="$(detect_lan_ip)"

# Set BACKEND_URL if not already set by --staging
if [[ -z "${BACKEND_URL:-}" ]]; then
  BACKEND_URL="http://${LAN_IP}:${PORT}"
fi

# ── Pre-flight banner ─────────────────────────────────────────────────────────
clear
printf "${CYAN}${BOLD}"
cat <<'LOGO'
 ____                 __   __         _ _
/ ___|  ___ __ _ _ __\ \ / /_ _ _   _| | |_
\___ \ / __/ _` | '_ \\ V / _` | | | | | __|
 ___) | (_| (_| | | | || | (_| | |_| | | |_
|____/ \___\__,_|_| |_||_|\__,_|\__,_|_|\__|
  dev environment — session runner
LOGO
printf "${NC}"
echo ""

# ── Check prerequisites ───────────────────────────────────────────────────────
step "Pre-flight checks"

# Docker
if [[ "$MODE" != "phone-only" ]]; then
  command -v docker &>/dev/null || die "docker not found — install Docker Desktop"
  docker info &>/dev/null 2>&1 || die "Docker daemon not running — start Docker Desktop first"
  ok "Docker daemon running"
  [[ -f "$BACKEND_DIR/docker-compose.yml" ]] || die "backend/docker-compose.yml missing"
  [[ -f "$BACKEND_DIR/.env" ]] || {
    warn "backend/.env missing — copying from .env.example"
    cp "$BACKEND_DIR/.env.example" "$BACKEND_DIR/.env" 2>/dev/null \
      || die "backend/.env.example also missing — run ./scripts/bootstrap.sh first"
  }
fi

# Android
if [[ "$MODE" != "backend-only" ]]; then
  [[ -f "$ANDROID_DIR/gradlew" ]] || die "android/gradlew not found — run Phase 1B setup"
  ok "Android project found"
fi

# air (Go hot-reload)
if [[ "$MODE" != "phone-only" ]]; then
  AIR_BIN=""
  if command -v air &>/dev/null; then
    AIR_BIN="air"
  elif [[ -f "$HOME/go/bin/air" ]]; then
    AIR_BIN="$HOME/go/bin/air"
  elif [[ -f "/c/Users/Nautilus/go/bin/air" ]]; then
    AIR_BIN="/c/Users/Nautilus/go/bin/air"
  fi
  if [[ -z "$AIR_BIN" ]]; then
    warn "air not found — falling back to 'go run' (no hot-reload)"
    AIR_BIN=""
  else
    ok "air found: $AIR_BIN"
  fi
fi

# ── Network summary ───────────────────────────────────────────────────────────
banner "Network"
if $DO_STAGING; then
  printf "  ${YELLOW}${BOLD}⚡ STAGING MODE — APK points at LIVE AWS API ⚡${NC}\n"
  printf "  AWS API       : ${GREEN}${BOLD}${BACKEND_URL}${NC}\n"
else
  printf "  Laptop LAN IP : ${GREEN}${BOLD}${LAN_IP}${NC}\n"
  printf "  Backend URL   : ${GREEN}${BOLD}${BACKEND_URL}${NC}\n"
fi
printf "  Phone target  : "
if $DO_WIFI; then
  printf "${CYAN}Wi-Fi ADB${NC}\n"
else
  printf "${CYAN}USB cable${NC}\n"
fi
echo ""
warn "Your phone must be on the same network (Wi-Fi or LAN)."
warn "If IP is wrong, run: BACKEND_HOST=<correct-ip> ./scripts/dev.sh"

# ── Find / connect ADB device ─────────────────────────────────────────────────
DEVICE_SERIAL=""
DEVICE_MODEL=""

if [[ "$MODE" != "backend-only" ]]; then
  step "ADB device detection"

  if $DO_WIFI; then
    printf "\n${YELLOW}Wi-Fi mode selected.${NC}\n"
    # Try to read saved address from dev-wifi-pair.sh
    WIFI_CONFIG="$HOME/.scanvault-wifi-adb"
    SAVED_ADDR=""
    if [[ -f "$WIFI_CONFIG" ]]; then
      SAVED_ADDR=$(grep '^WIFI_ADB_ADDRESS=' "$WIFI_CONFIG" | cut -d= -f2 | tr -d '[:space:]')
    fi
    if [[ -n "$SAVED_ADDR" ]]; then
      info "Auto-connecting to saved address: $SAVED_ADDR"
      adb_cmd connect "$SAVED_ADDR" 2>/dev/null && ok "Wi-Fi ADB connected: $SAVED_ADDR" || {
        warn "Saved address failed. The port may have changed after reboot."
        warn "Run ./scripts/dev-wifi-pair.sh to re-pair, or enter a new address."
        read -rp "  Enter phone's Wi-Fi debug address (IP:PORT): " WIFI_ADDR
        [[ -n "$WIFI_ADDR" ]] && adb_cmd connect "$WIFI_ADDR" && ok "Connected: $WIFI_ADDR"
      }
    else
      printf "  No saved Wi-Fi address found.\n"
      printf "  Run ${CYAN}./scripts/dev-wifi-pair.sh${NC} once to pair permanently.\n\n"
      read -rp "  Enter phone's Wi-Fi debug address (IP:PORT, e.g. 192.168.1.8:37000): " WIFI_ADDR
      [[ -n "$WIFI_ADDR" ]] && adb_cmd connect "$WIFI_ADDR" && ok "Wi-Fi ADB connected: $WIFI_ADDR"
    fi
  fi

  # Wait up to 15s for a device to appear (handles slow USB auth)
  info "Waiting for authorized device (accept prompt on phone if shown)..."
  WAIT=0
  while [[ $WAIT -lt 15 ]]; do
    DEVICE_LIST=$(adb_cmd devices 2>/dev/null | grep -v '^List' | grep 'device$' | awk '{print $1}')
    if [[ -n "$DEVICE_LIST" ]]; then break; fi
    # Show unauthorized devices so user knows what's happening
    UNAUTH=$(adb_cmd devices 2>/dev/null | grep 'unauthorized' | awk '{print $1}')
    if [[ -n "$UNAUTH" ]]; then
      printf "\r  ${YELLOW}⚠${NC}  Device ${UNAUTH} is unauthorized — accept the debugging prompt on your phone... [${WAIT}s]  "
    fi
    sleep 1; WAIT=$((WAIT+1))
  done
  echo ""

  DEVICE_LIST=$(adb_cmd devices 2>/dev/null | grep -v '^List' | grep 'device$' | awk '{print $1}')
  DEVICE_COUNT=$(echo "$DEVICE_LIST" | grep -c '.' || true)

  if [[ "$DEVICE_COUNT" -eq 0 ]]; then
    echo ""
    printf "${RED}No authorized device found.${NC}\n"
    echo ""
    printf "  USB debugging checklist:\n"
    printf "    1. Settings → About Phone → tap Build Number 7× (enables Developer Options)\n"
    printf "    2. Settings → Developer Options → USB Debugging → ON\n"
    printf "    3. Connect USB cable\n"
    printf "    4. Accept the 'Allow USB debugging?' popup on your phone\n"
    printf "    5. Check: adb devices  (should show your serial + 'device')\n"
    printf "\n"
    printf "  Wi-Fi alternative: ./scripts/dev.sh --wifi\n"
    die "Cannot continue without a device."
  fi

  if [[ -n "$TARGET_DEVICE" ]]; then
    DEVICE_SERIAL="$TARGET_DEVICE"
  else
    DEVICE_SERIAL=$(echo "$DEVICE_LIST" | head -1)
    if [[ "$DEVICE_COUNT" -gt 1 ]]; then
      warn "Multiple devices found — using: $DEVICE_SERIAL"
      warn "Use --device <serial> to pick a specific one:"
      adb_cmd devices | grep 'device$'
    fi
  fi

  DEVICE_MODEL=$(adb_cmd -s "$DEVICE_SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "Phone")
  ANDROID_VER=$(adb_cmd -s "$DEVICE_SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "?")
  ok "Device: ${DEVICE_MODEL} (Android ${ANDROID_VER}) — serial: ${DEVICE_SERIAL}"
fi

# ── PIDS list (for cleanup) ───────────────────────────────────────────────────
PIDS=()
BACKEND_STARTED=false
INTERRUPTED=false

on_signal() {
  INTERRUPTED=true
  exit 130
}

cleanup() {
  local exit_code=$?
  local stop_backend=false

  echo ""
  banner "Shutting down"

  if $BACKEND_STARTED && should_stop_backend_on_exit "$BACKEND_EXIT_POLICY" "$INTERRUPTED"; then
    stop_backend=true
  fi

  if $stop_backend; then
    for pid in "${PIDS[@]}"; do
      kill "$pid" 2>/dev/null || true
    done
    for pid in "${PIDS[@]}"; do
      wait "$pid" 2>/dev/null || true
    done
    ok "Go API stopped"
  elif $BACKEND_STARTED; then
    ok "Go API left running for fast reuse"
    for pid in "${PIDS[@]}"; do
      info "Go API PID: $pid"
    done
    info "Stop API manually: kill <pid>"
  else
    info "No Go API process to stop"
  fi

  info "Postgres + Redis are still running (fast restart next time)"
  info "Stop containers: docker compose -f backend/docker-compose.yml down"
  if [[ "$exit_code" -ne 0 && "$INTERRUPTED" == "false" && "$stop_backend" == "false" ]]; then
    warn "dev.sh exited with code $exit_code; backend kept running for debugging"
    warn "Check backend logs: tail -n 80 /tmp/scanvault-backend.log"
  fi
  echo ""
}
trap on_signal INT TERM
trap cleanup EXIT

# ── Start backend ─────────────────────────────────────────────────────────────
if [[ "$MODE" != "phone-only" ]]; then
  step "Starting backend stack"

  # Load .env
  set -a
  # shellcheck disable=SC1090,SC1091
  [[ -f "$BACKEND_DIR/.env" ]] && source "$BACKEND_DIR/.env"
  set +a

  # Start docker compose
  info "Starting Postgres + Redis..."
  cd "$BACKEND_DIR"
  docker compose up -d --remove-orphans 2>&1 | grep -v '^#' | grep -v '^$' | sed 's/^/  /' || true

  # Wait for Postgres
  info "Waiting for Postgres..."
  for i in $(seq 1 30); do
    if docker compose exec -T db pg_isready -q 2>/dev/null; then
      ok "Postgres ready (${i}s)"; break
    fi
    sleep 1
    if [[ $i -eq 30 ]]; then die "Postgres not ready after 30s"; fi
  done

  # Run migrations
  if command -v goose &>/dev/null && [[ -n "${DATABASE_URL:-}" && -d "$BACKEND_DIR/db/migrations" ]]; then
    info "Running migrations..."
    goose -dir "$BACKEND_DIR/db/migrations" postgres "$DATABASE_URL" up 2>&1 | sed 's/^/  /' && ok "Migrations applied"
  else
    GOOSE_BIN=""
    for p in "$HOME/go/bin/goose" "/c/Users/Nautilus/go/bin/goose.exe" "/c/Users/Nautilus/go/bin/goose"; do
      [[ -f "$p" ]] && { GOOSE_BIN="$p"; break; }
    done
    if [[ -n "$GOOSE_BIN" && -n "${DATABASE_URL:-}" && -d "$BACKEND_DIR/db/migrations" ]]; then
      info "Running migrations (via $GOOSE_BIN)..."
      "$GOOSE_BIN" -dir "$BACKEND_DIR/db/migrations" postgres "$DATABASE_URL" up 2>&1 | sed 's/^/  /' && ok "Migrations applied"
    else
      warn "Skipping migrations (goose not found or DATABASE_URL not set)"
    fi
  fi

  # Start Go API (air hot-reload or fallback go run)
  info "Starting Go API..."
  cd "$BACKEND_DIR"
  if [[ -n "${AIR_BIN:-}" ]]; then
    AIR_OPTS=()
    [[ -f "$BACKEND_DIR/.air.toml" ]] && AIR_OPTS=("-c" ".air.toml")
    PORT="$PORT" "$AIR_BIN" "${AIR_OPTS[@]}" > /tmp/scanvault-backend.log 2>&1 &
  else
    PORT="$PORT" go run ./cmd/api > /tmp/scanvault-backend.log 2>&1 &
  fi
  API_PID=$!
  PIDS+=("$API_PID")
  BACKEND_STARTED=true
  ok "Go API starting (PID $API_PID) — logs: /tmp/scanvault-backend.log"

  # Wait for API to become healthy
  info "Waiting for API to be healthy..."
  API_READY=false
  for i in $(seq 1 30); do
    if curl -sf "http://localhost:${PORT}/health" &>/dev/null 2>&1; then
      API_READY=true; ok "API healthy (${i}s)"; break
    fi
    # Show last log line so user sees progress
    LAST_LOG=$(tail -1 /tmp/scanvault-backend.log 2>/dev/null | tr -d '\r' || true)
    printf "\r  [%ds] %s                    " "$i" "${LAST_LOG:0:60}"
    sleep 1
  done
  echo ""
  if ! $API_READY; then
    warn "API not healthy after 30s — it may still be compiling."
    warn "Check logs: tail -f /tmp/scanvault-backend.log"
  fi

  cd "$ROOT"
fi

# ── Build & deploy Android app ────────────────────────────────────────────────
if [[ "$MODE" != "backend-only" ]]; then
  step "Building Android APK"

  GRADLE_QUIET="--quiet"
  $DO_VERBOSE && GRADLE_QUIET=""

  GRADLE_ARGS=(
    "--parallel"
    "--build-cache"
    "-PapiBaseUrl=${BACKEND_URL}"
    $GRADLE_QUIET
  )

  cd "$ANDROID_DIR"

  if $DO_CLEAN; then
    info "Cleaning Gradle cache..."
    ./gradlew clean "${GRADLE_ARGS[@]}" 2>&1 | tail -5
    ok "Clean done"
  fi

  info "Assembling debug APK (backend: ${BACKEND_URL})..."
  BUILD_START=$(date +%s)

  if $DO_VERBOSE; then
    ./gradlew assembleDebug "${GRADLE_ARGS[@]}"
  else
    ./gradlew assembleDebug "${GRADLE_ARGS[@]}" 2>&1 | \
      grep -E "^(BUILD|FAILURE|ERROR|> Task|Caused|Exception|error:)" \
      | sed 's/^/  /' || true
  fi

  BUILD_END=$(date +%s)
  BUILD_TIME=$((BUILD_END - BUILD_START))

  APK_PATH=$(find "$ANDROID_DIR/app/build/outputs/apk/debug" -name "*.apk" 2>/dev/null | head -1)
  if [[ -z "$APK_PATH" ]]; then
    die "APK not found — run with --verbose to see full build output"
  fi

  APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
  ok "APK built in ${BUILD_TIME}s — ${APK_SIZE} — $(basename "$APK_PATH")"
  cd "$ROOT"

  if $DO_INSTALL; then
    step "Installing on ${DEVICE_MODEL}"
    adb_cmd -s "$DEVICE_SERIAL" install -r "$APK_PATH" 2>&1 | sed 's/^/  /'
    ok "APK installed"

    # Kill existing instance then launch fresh
    adb_cmd -s "$DEVICE_SERIAL" shell am force-stop "$APP_PACKAGE_DEBUG" 2>/dev/null || true
    adb_cmd -s "$DEVICE_SERIAL" shell am force-stop "$APP_PACKAGE" 2>/dev/null || true

    LAUNCH_COMPONENT="$(resolve_launcher_component "$DEVICE_SERIAL" || true)"
    if [[ -z "$LAUNCH_COMPONENT" ]]; then
      warn "Could not resolve launcher activity for $APP_PACKAGE_DEBUG or $APP_PACKAGE"
      warn "App installed, but auto-launch was skipped. Launch it manually on phone."
    else
      info "Launching activity: $LAUNCH_COMPONENT"
      if start_component "$DEVICE_SERIAL" "$LAUNCH_COMPONENT"; then
        ok "App launched on ${DEVICE_MODEL}"
      else
        warn "Component launch failed for $LAUNCH_COMPONENT"
        TARGET_PACKAGE="${LAUNCH_COMPONENT%%/*}"
        info "Trying fallback launcher intent for $TARGET_PACKAGE..."
        set +e
        MONKEY_OUT="$(adb_cmd -s "$DEVICE_SERIAL" shell monkey -p "$TARGET_PACKAGE" -c android.intent.category.LAUNCHER 1 2>&1)"
        MONKEY_EXIT=$?
        set -e
        printf "%s\n" "$MONKEY_OUT" | sed 's/^/  /'
        if [[ "$MONKEY_EXIT" -eq 0 ]]; then
          ok "App launched on ${DEVICE_MODEL} (fallback)"
        else
          warn "Fallback launch failed (exit $MONKEY_EXIT). Continuing for debugging."
        fi
      fi
    fi
  fi
fi

# ── Live status dashboard ─────────────────────────────────────────────────────
banner "Dev environment running"
printf "\n"
printf "  ${GREEN}${BOLD}%-20s${NC} %s\n" "Backend API:" "http://localhost:${PORT}/health"
printf "  ${GREEN}${BOLD}%-20s${NC} %s\n" "Phone API URL:" "$BACKEND_URL"
if [[ "$MODE" != "backend-only" ]]; then
printf "  ${GREEN}${BOLD}%-20s${NC} %s (Android %s)\n" "Device:" "$DEVICE_MODEL" "$ANDROID_VER"
fi
printf "\n"
if [[ -n "${AIR_BIN:-}" ]]; then
printf "  ${CYAN}Go hot-reload:${NC}       Edit any .go file → API restarts automatically (~1s)\n"
fi
printf "  ${CYAN}Android rebuild:${NC}    Ctrl+C → edit code → ./scripts/dev.sh --phone-only\n"
printf "  ${CYAN}Faster iteration:${NC}   Open android/ in Android Studio, use Apply Changes (▶)\n"
printf "  ${CYAN}Backend logs:${NC}       tail -f /tmp/scanvault-backend.log\n"
printf "\n"
printf "  ${YELLOW}Ctrl+C to stop.${NC}\n"

# ── File watcher mode (auto-redeploy on Kotlin change) ───────────────────────
if $DO_WATCH && [[ "$MODE" != "backend-only" ]]; then
  echo ""
  info "Watch mode: monitoring Kotlin files for changes..."
  if command -v inotifywait &>/dev/null; then
    while true; do
      inotifywait -r -e close_write --include '.*\.kt$' "$ANDROID_DIR/app" "$ANDROID_DIR/core" "$ANDROID_DIR/feature" 2>/dev/null
      echo ""
      info "Kotlin change detected — rebuilding and redeploying..."
      cd "$ANDROID_DIR"
      ./gradlew assembleDebug --parallel --build-cache --quiet -PapiBaseUrl="${BACKEND_URL}" 2>&1 | tail -5
      APK_PATH=$(find "$ANDROID_DIR/app/build/outputs/apk/debug" -name "*.apk" | head -1)
      if [[ -n "$APK_PATH" ]]; then
        adb_cmd -s "$DEVICE_SERIAL" install -r "$APK_PATH" 2>/dev/null
        adb_cmd -s "$DEVICE_SERIAL" shell am force-stop "$APP_PACKAGE_DEBUG" 2>/dev/null || true
        adb_cmd -s "$DEVICE_SERIAL" shell am force-stop "$APP_PACKAGE" 2>/dev/null || true
        LAUNCH_COMPONENT="$(resolve_launcher_component "$DEVICE_SERIAL" || true)"
        if [[ -n "$LAUNCH_COMPONENT" ]]; then
          adb_cmd -s "$DEVICE_SERIAL" shell am start -n "$LAUNCH_COMPONENT" --activity-clear-top >/dev/null 2>&1 || true
        fi
        ok "Redeployed at $(date '+%H:%M:%S')"
      fi
      cd "$ROOT"
    done
  else
    warn "inotifywait not found — install inotify-tools for --watch mode"
    warn "On Ubuntu/WSL: sudo apt-get install inotify-tools"
  fi
fi

# ── Stream logcat ─────────────────────────────────────────────────────────────
if [[ "$MODE" != "backend-only" && $DO_INSTALL == true ]]; then
  banner "Logcat — ${DEVICE_MODEL}"
  echo ""

  # Poll for app PID up to 15s — the app takes a few seconds to start
  info "Waiting for app to start..."
  APP_PID=""
  for i in $(seq 1 15); do
    PID=$(adb_cmd -s "$DEVICE_SERIAL" shell pidof -s "$APP_PACKAGE_DEBUG" 2>/dev/null | tr -d '[:space:]' || true)
    if [[ -z "$PID" ]]; then
      PID=$(adb_cmd -s "$DEVICE_SERIAL" shell pidof -s "$APP_PACKAGE" 2>/dev/null | tr -d '[:space:]' || true)
    fi
    if [[ -n "$PID" && "$PID" =~ ^[0-9]+$ ]]; then
      APP_PID="$PID"
      ok "App running — PID $APP_PID"
      break
    fi
    printf "\r  [%ds] waiting for app process..." "$i"
    sleep 1
  done
  echo ""

  # Build logcat filter — prefer PID filter; fall back to tag filter (never --pid=0)
  LOGCAT_ARGS=("-v" "time")
  if [[ -n "$APP_PID" ]]; then
    LOGCAT_ARGS+=("--pid=$APP_PID")
  else
    warn "App PID not found — filtering by package name tag (may be slightly noisy)"
    # Tag filter: show any log line containing the package name
    LOGCAT_ARGS+=("-s" "ScanVault:V" "AndroidRuntime:E" "System.err:W")
  fi

  printf "${CYAN}  Streaming logs — Ctrl+C to stop (app keeps running on phone)${NC}\n\n"

  # Logcat over Wi-Fi can drop unexpectedly; retry so backend sessions do not get torn down.
  while true; do
    set +e
    adb_cmd -s "$DEVICE_SERIAL" logcat "${LOGCAT_ARGS[@]}" 2>/dev/null \
      | while IFS= read -r line; do
          case "$line" in
            *" E "*)  printf "${RED}%s${NC}\n"    "$line" ;;
            *" W "*)  printf "${YELLOW}%s${NC}\n" "$line" ;;
            *" I "*)  printf "${GREEN}%s${NC}\n"  "$line" ;;
            *" D "*)  printf "${BLUE}%s${NC}\n"   "$line" ;;
            *" V "*)  printf "%s\n"               "$line" ;;
            *)        printf "%s\n"               "$line" ;;
          esac
        done
    LOGCAT_EXIT=${PIPESTATUS[0]}
    set -e

    if [[ "$INTERRUPTED" == "true" ]]; then
      break
    fi

    warn "logcat stream ended (adb exit code: $LOGCAT_EXIT)"
    warn "Retrying logcat in 2s..."
    sleep 2

    # Refresh PID filter after app restart/redeploy.
    APP_PID=$(adb_cmd -s "$DEVICE_SERIAL" shell pidof -s "$APP_PACKAGE_DEBUG" 2>/dev/null | tr -d '[:space:]' || true)
    if [[ -z "$APP_PID" ]]; then
      APP_PID=$(adb_cmd -s "$DEVICE_SERIAL" shell pidof -s "$APP_PACKAGE" 2>/dev/null | tr -d '[:space:]' || true)
    fi
    if [[ -n "$APP_PID" && "$APP_PID" =~ ^[0-9]+$ ]]; then
      LOGCAT_ARGS=("-v" "time" "--pid=$APP_PID")
    else
      LOGCAT_ARGS=("-v" "time" "-s" "ScanVault:V" "AndroidRuntime:E" "System.err:W")
    fi
  done
else
  # Backend-only mode: tail the API log
  banner "Backend logs"
  echo ""
  info "Tailing /tmp/scanvault-backend.log (Ctrl+C to stop)"
  echo ""
  tail -f /tmp/scanvault-backend.log 2>/dev/null || wait
fi

# Wait for all background jobs if somehow we get here
wait
