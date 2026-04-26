#!/usr/bin/env bash
# =============================================================================
# Paperkeep — dev-android.sh
#
# Build the debug APK, install it on your phone, launch it, stream logcat.
# Zero manual interaction during a normal run.
#
# DEVICE CONNECTION (fully automatic, no prompts):
#   • USB cable plugged in? Uses it immediately.
#   • Wi-Fi address saved from a previous run? Reconnects silently.
#   • Phone already shows in `adb devices`? Uses it.
#   • Run with --pair to do the one-time Wi-Fi pairing setup (only needed once).
#
# USAGE:
#   ./scripts/dev-android.sh              # build → install → launch → logcat
#   ./scripts/dev-android.sh --clean      # clean Gradle cache first (~30s extra)
#   ./scripts/dev-android.sh --no-build   # skip build, re-install last APK
#   ./scripts/dev-android.sh --no-launch  # install but don't auto-launch
#   ./scripts/dev-android.sh --no-logcat  # launch but don't stream logs
#   ./scripts/dev-android.sh --pair       # one-time Wi-Fi pairing setup
#   ./scripts/dev-android.sh --device SERIAL  # force a specific device
#   ./scripts/dev-android.sh --verbose    # show full Gradle output
#   ./scripts/dev-android.sh --help
# =============================================================================

set -euo pipefail

# ── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m';  GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m';  BOLD='\033[1m';  NC='\033[0m'

# ── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$ROOT/android"
APP_PACKAGE="app.paperkeep"
APP_PACKAGE_DEBUG="${APP_PACKAGE}.debug"

# Saved Wi-Fi address persists across sessions (never ask twice)
WIFI_STATE_FILE="$HOME/.paperkeep-dev-wifi"

# ── Helpers ───────────────────────────────────────────────────────────────────
info()   { printf "${BLUE}[paperkeep]${NC} %s\n" "$*"; }
ok()     { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
warn()   { printf "  ${YELLOW}!${NC} %s\n" "$*"; }
die()    { printf "\n  ${RED}✗ %s${NC}\n\n" "$*"; exit 1; }
banner() { printf "\n${CYAN}${BOLD}── %s ──${NC}\n" "$*"; }
step()   { printf "\n${YELLOW}▶ %s${NC}\n" "$*"; }

# ── Help ──────────────────────────────────────────────────────────────────────
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'HELP'

dev-android.sh — Paperkeep phone dev loop (zero manual interaction)

USAGE
  ./scripts/dev-android.sh [OPTIONS]

OPTIONS
  --clean         Run Gradle clean before building (fixes weird cache issues)
  --no-build      Skip Gradle — re-install the last built APK
  --no-launch     Install the APK but don't auto-launch
  --no-logcat     Launch but don't stream logcat
  --pair          One-time Wi-Fi ADB pairing wizard (run once, never again)
                  After pairing, all future runs reconnect silently.
  --device SERIAL Force a specific device serial
  --verbose       Show full Gradle output
  --help, -h      Show this message

HOW DEVICE CONNECTION WORKS (automatic, no prompts needed)
  1. USB cable? → uses it immediately
  2. Saved Wi-Fi address? → reconnects silently ($HOME/.paperkeep-dev-wifi)
  3. Already in adb devices? → uses it
  4. Nothing found → shows checklist and exits

FIRST-TIME USB SETUP (one-time, takes 30 seconds)
  1. Phone: Settings → About Phone → tap Build Number 7 times
  2. Phone: Settings → Developer Options → USB Debugging → ON
  3. Plug in USB cable → accept "Allow USB debugging?" on phone
  4. Run: ./scripts/dev-android.sh

FIRST-TIME WI-FI SETUP (run once, then cable-free forever)
  1. Phone: Settings → Developer Options → Wireless Debugging → ON
  2. Run: ./scripts/dev-android.sh --pair
  3. Follow the on-screen instructions (takes ~60 seconds)
  4. After that: ./scripts/dev-android.sh  (no flags needed, reconnects automatically)

WHAT YOU'LL SEE ON THE PHONE
  Flow: Onboarding (3 pages) → Camera/Scanner → Crop → Library → Settings
  Camera requires real hardware — works on physical phone only.
  Biometric lock and encrypted storage also require real device hardware.

HELP
  exit 0
fi

# ── Parse flags ───────────────────────────────────────────────────────────────
DO_CLEAN=false
DO_BUILD=true
DO_LAUNCH=true
DO_LOGCAT=true
DO_PAIR=false
DO_VERBOSE=false
TARGET_DEVICE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --clean)      DO_CLEAN=true;    shift ;;
    --no-build)   DO_BUILD=false;   shift ;;
    --no-launch)  DO_LAUNCH=false;  shift ;;
    --no-logcat)  DO_LOGCAT=false;  shift ;;
    --pair)       DO_PAIR=true;     shift ;;
    --verbose)    DO_VERBOSE=true;  shift ;;
    --device)     TARGET_DEVICE="$2"; shift 2 ;;
    *) die "Unknown flag: $1  (run --help for usage)" ;;
  esac
done

# ── Find adb ─────────────────────────────────────────────────────────────────
find_adb() {
  command -v adb &>/dev/null && { echo "adb"; return; }
  local win="/c/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools/adb.exe"
  [[ -f "$win" ]] && { echo "$win"; return; }
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    [[ -f "$ANDROID_HOME/platform-tools/adb.exe" ]] && { echo "$ANDROID_HOME/platform-tools/adb.exe"; return; }
    [[ -f "$ANDROID_HOME/platform-tools/adb"     ]] && { echo "$ANDROID_HOME/platform-tools/adb";     return; }
  fi
  for p in "$HOME/Android/Sdk/platform-tools/adb" "/usr/bin/adb" "/usr/local/bin/adb"; do
    [[ -f "$p" ]] && { echo "$p"; return; }
  done
  echo ""
}

ADB_BIN="$(find_adb)"
[[ -z "$ADB_BIN" ]] && die "adb not found. Set ANDROID_HOME or add platform-tools to PATH.\nExpected: C:/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools"

# Wrappers
adb()   { "$ADB_BIN" "$@"; }
adb_d() { "$ADB_BIN" -s "$DEVICE_SERIAL" "$@"; }

# ── Find Java 17 ─────────────────────────────────────────────────────────────
find_java() {
  [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] && { echo "$JAVA_HOME/bin/java"; return; }
  local corretto="/c/Program Files/Amazon Corretto/jdk17.0.16_8/bin/java.exe"
  [[ -f "$corretto" ]] && { echo "$corretto"; return; }
  command -v java &>/dev/null && { echo "java"; return; }
  echo ""
}

JAVA_BIN="$(find_java)"
[[ -z "$JAVA_BIN" ]] && die "Java 17 not found. Set JAVA_HOME or install Amazon Corretto 17."

# ── Pre-flight: Gradle ────────────────────────────────────────────────────────
[[ -d "$ANDROID_DIR" ]] || die "android/ directory not found at $ANDROID_DIR"
GRADLE_JAR="$ANDROID_DIR/gradle/wrapper/gradle-wrapper.jar"
[[ -f "$GRADLE_JAR" ]] || die "gradle-wrapper.jar not found at $GRADLE_JAR"

# ── Banner ────────────────────────────────────────────────────────────────────
clear
printf "${CYAN}${BOLD}"
cat <<'LOGO'
  ____                        _  __
 |  _ \ __ _ _ __   ___ _ __| |/ /___  ___ _ __
 | |_) / _` | '_ \ / _ \ '__| ' // _ \/ _ \ '_ \
 |  __/ (_| | |_) |  __/ |  | . \  __/  __/ |_) |
 |_|   \__,_| .__/ \___|_|  |_|\_\___|\___| .__/
            |_|                            |_|
LOGO
printf "${NC}"
printf "  ${BOLD}Android dev loop${NC} — build · install · launch · logcat\n\n"

# =============================================================================
# ── ONE-TIME WI-FI PAIRING WIZARD  (--pair flag only) ────────────────────────
# =============================================================================
# This block runs ONLY when you pass --pair. After it completes once,
# subsequent runs reconnect silently with no prompts.
# =============================================================================
if $DO_PAIR; then
  banner "Wi-Fi ADB one-time pairing wizard"
  printf "\n"
  printf "  This runs ONCE. After it completes, all future runs connect\n"
  printf "  automatically with no prompts.\n\n"

  printf "  ${BOLD}Step 1 — Enable Wireless Debugging on your phone:${NC}\n"
  printf "    Settings → Developer Options → Wireless Debugging → toggle ON\n\n"

  printf "  ${BOLD}Step 2 — Get the PAIRING code:${NC}\n"
  printf "    Tap \"Pair device with pairing code\" inside Wireless Debugging\n"
  printf "    Your phone shows: IP address, PORT, and a 6-digit code\n\n"

  # Auto-detect phone's IP from ARP table or mDNS — try to pre-fill
  GUESSED_IP=""
  if command -v arp &>/dev/null; then
    # Most phones show up in ARP cache after any network activity on LAN
    GUESSED_IP=$(arp -a 2>/dev/null | grep -oE '192\.168\.[0-9]+\.[0-9]+' | head -1 || true)
  fi
  if [[ -z "$GUESSED_IP" ]] && command -v cmd.exe &>/dev/null; then
    GUESSED_IP=$(cmd.exe //C arp -a 2>/dev/null \
      | grep -oE '192\.168\.[0-9]+\.[0-9]+' | grep -v '255$' | head -1 || true)
  fi

  HINT=""
  [[ -n "$GUESSED_IP" ]] && HINT=" [guessed: ${GUESSED_IP}]"

  printf "  ${BOLD}Enter the pairing address shown on your phone${NC} (e.g. 192.168.1.5:12345)${HINT}:\n"
  read -rp "  > " PAIR_ADDR
  PAIR_ADDR="${PAIR_ADDR:-${GUESSED_IP}}"
  [[ -z "$PAIR_ADDR" ]] && die "No pairing address entered."

  printf "\n  ${BOLD}Enter the 6-digit pairing code shown on your phone:${NC}\n"
  read -rp "  > " PAIR_CODE
  [[ -z "$PAIR_CODE" ]] && die "No pairing code entered."

  printf "\n  Pairing...\n"
  if "$ADB_BIN" pair "$PAIR_ADDR" "$PAIR_CODE"; then
    ok "Paired successfully"
  else
    die "Pairing failed. Check the code and address are from the same 'Pair device with pairing code' screen."
  fi

  printf "\n  ${BOLD}Step 3 — Get the CONNECTION address:${NC}\n"
  printf "    Go BACK to the main Wireless Debugging screen\n"
  printf "    It shows \"IP address & Port\" — that's the connection address (different from pairing)\n\n"

  # Extract IP from pair address as a hint for the connection port
  PAIR_IP="${PAIR_ADDR%%:*}"
  printf "  ${BOLD}Enter the connection address${NC} (e.g. ${PAIR_IP}:37000):\n"
  read -rp "  > " CONNECT_ADDR
  [[ -z "$CONNECT_ADDR" ]] && die "No connection address entered."

  printf "\n  Connecting...\n"
  CONNECT_OUT=$("$ADB_BIN" connect "$CONNECT_ADDR" 2>&1 || true)
  if echo "$CONNECT_OUT" | grep -qi "connected\|already"; then
    ok "Connected: $CONNECT_ADDR"
    # Save for future runs — no prompts ever again
    echo "WIFI_CONNECT_ADDR=${CONNECT_ADDR}" > "$WIFI_STATE_FILE"
    echo "WIFI_PAIR_IP=${PAIR_IP}" >> "$WIFI_STATE_FILE"
    ok "Saved Wi-Fi address to ${WIFI_STATE_FILE}"
    printf "\n  ${GREEN}${BOLD}Wi-Fi setup complete!${NC}\n"
    printf "  From now on, just run:  ./scripts/dev-android.sh\n"
    printf "  (No --pair flag, no prompts, fully automatic)\n\n"
  else
    printf "%s\n" "$CONNECT_OUT" | sed 's/^/  /'
    die "Connection failed. The phone must be on the same Wi-Fi network as your computer."
  fi

  # Fall through — continue with the build + install in this same run
fi

# =============================================================================
# ── AUTO DEVICE CONNECTION (zero prompts) ────────────────────────────────────
# =============================================================================
step "Connecting to device"

DEVICE_SERIAL=""

# ── Strategy 1: USB or already-connected device ───────────────────────────────
# Give adb up to 8s to see a device (handles USB auth dialog delay)
for _i in $(seq 1 8); do
  if [[ -n "$TARGET_DEVICE" ]]; then
    "$ADB_BIN" devices | grep -q "^${TARGET_DEVICE}.*device$" && {
      DEVICE_SERIAL="$TARGET_DEVICE"; break
    }
  else
    DEVICE_SERIAL=$("$ADB_BIN" devices 2>/dev/null \
      | grep -v '^List' | grep $'\tdevice$' | awk '{print $1}' | head -1 || true)
    [[ -n "$DEVICE_SERIAL" ]] && break
  fi

  # Show helpful status during wait
  UNAUTH=$("$ADB_BIN" devices 2>/dev/null | grep 'unauthorized' | awk '{print $1}' || true)
  if [[ -n "$UNAUTH" ]]; then
    printf "\r  ${YELLOW}!${NC}  Accept the USB debugging prompt on your phone... [%ds]  " "$_i"
  else
    printf "\r  Looking for device... [%ds]  " "$_i"
  fi
  sleep 1
done
printf "\r%60s\r" ""

# ── Strategy 2: Reconnect saved Wi-Fi address silently ───────────────────────
if [[ -z "$DEVICE_SERIAL" && -f "$WIFI_STATE_FILE" ]]; then
  # shellcheck source=/dev/null
  source "$WIFI_STATE_FILE" 2>/dev/null || true
  if [[ -n "${WIFI_CONNECT_ADDR:-}" ]]; then
    info "Trying saved Wi-Fi address: ${WIFI_CONNECT_ADDR}"
    RECONNECT_OUT=$("$ADB_BIN" connect "$WIFI_CONNECT_ADDR" 2>&1 || true)
    if echo "$RECONNECT_OUT" | grep -qi "connected\|already connected"; then
      ok "Wi-Fi reconnected: ${WIFI_CONNECT_ADDR}"
      # Now wait a moment for adb devices to reflect it
      sleep 1
      DEVICE_SERIAL=$("$ADB_BIN" devices 2>/dev/null \
        | grep -v '^List' | grep $'\tdevice$' | awk '{print $1}' | head -1 || true)
    else
      warn "Saved Wi-Fi address unreachable (${WIFI_CONNECT_ADDR})"
      warn "Phone may be off Wi-Fi, or the port changed after a reboot."
      warn "Run: ./scripts/dev-android.sh --pair   to re-pair (takes ~60s)"
      # Clear stale state so we don't keep failing on it
      rm -f "$WIFI_STATE_FILE"
    fi
  fi
fi

# ── Strategy 3: Scan the LAN for ADB over TCP (mDNS / port scan) ─────────────
if [[ -z "$DEVICE_SERIAL" ]]; then
  info "No device found yet — scanning LAN for ADB (port 5555)..."

  # Get our own IP to determine the subnet
  OWN_IP=""
  if command -v cmd.exe &>/dev/null; then
    OWN_IP=$(cmd.exe //C ipconfig 2>/dev/null \
      | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | grep '^192\.168\.' | head -1 || true)
  fi
  if [[ -z "$OWN_IP" ]] && command -v ip &>/dev/null; then
    OWN_IP=$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K\S+' | head -1 || true)
  fi

  if [[ -n "$OWN_IP" ]]; then
    SUBNET="${OWN_IP%.*}"
    info "Scanning ${SUBNET}.0/24 for ADB devices (this takes ~10s)..."
    # Try connecting to common Android IPs in the subnet quickly
    for _last in $(seq 1 254); do
      CANDIDATE="${SUBNET}.${_last}"
      # Skip our own IP
      [[ "$CANDIDATE" == "$OWN_IP" ]] && continue
      # Non-blocking connect attempt via adb — timeout 1s per host
      if "$ADB_BIN" connect "${CANDIDATE}:5555" 2>&1 | grep -qi "connected\|already"; then
        sleep 0.5
        DEVICE_SERIAL=$("$ADB_BIN" devices 2>/dev/null \
          | grep -v '^List' | grep $'\tdevice$' | awk '{print $1}' | head -1 || true)
        if [[ -n "$DEVICE_SERIAL" ]]; then
          ok "Found ADB device at ${CANDIDATE}:5555"
          # Save for next time
          echo "WIFI_CONNECT_ADDR=${CANDIDATE}:5555" > "$WIFI_STATE_FILE"
          echo "WIFI_PAIR_IP=${CANDIDATE}" >> "$WIFI_STATE_FILE"
          break
        fi
      fi
    done 2>/dev/null
  fi
fi

# ── No device found → helpful error ──────────────────────────────────────────
if [[ -z "$DEVICE_SERIAL" ]]; then
  printf "\n"
  printf "  ${RED}${BOLD}No device found.${NC}\n\n"
  printf "  ${BOLD}Option A — USB cable (fastest):${NC}\n"
  printf "    1. Settings → About Phone → tap Build Number 7 times\n"
  printf "    2. Settings → Developer Options → USB Debugging → ON\n"
  printf "    3. Connect USB cable → accept the debugging prompt on the phone\n"
  printf "    4. Run this script again\n\n"
  printf "  ${BOLD}Option B — Wi-Fi (cable-free, one-time setup):${NC}\n"
  printf "    1. Settings → Developer Options → Wireless Debugging → ON\n"
  printf "    2. Run: ./scripts/dev-android.sh --pair\n"
  printf "    3. After setup, all future runs are automatic\n\n"
  printf "  ${BOLD}Debug:${NC}  adb devices   (should show your phone serial)\n\n"
  exit 1
fi

# ── Multiple devices warning ──────────────────────────────────────────────────
ALL_DEVICES=$("$ADB_BIN" devices 2>/dev/null | grep $'\tdevice$' | awk '{print $1}' || true)
DEVICE_COUNT=$(printf '%s\n' "$ALL_DEVICES" | grep -c '.' || true)
if [[ "$DEVICE_COUNT" -gt 1 && -z "$TARGET_DEVICE" ]]; then
  warn "Multiple devices — using first: ${DEVICE_SERIAL}"
  warn "Use --device <serial> to pick one:"
  printf '%s\n' "$ALL_DEVICES" | sed 's/^/    /'
fi

DEVICE_MODEL=$(adb_d shell getprop ro.product.model        2>/dev/null | tr -d '\r' || echo "Phone")
ANDROID_VER=$(adb_d shell  getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "?")
ANDROID_API=$(adb_d shell  getprop ro.build.version.sdk    2>/dev/null | tr -d '\r' || echo "?")

ok "Device : ${BOLD}${DEVICE_MODEL}${NC}"
ok "Android: ${BOLD}${ANDROID_VER}${NC} (API ${ANDROID_API})  serial: ${DEVICE_SERIAL}"

# =============================================================================
# ── GRADLE BUILD ──────────────────────────────────────────────────────────────
# =============================================================================
APK_PATH=""

if $DO_BUILD; then
  step "Building debug APK"

  GRADLE_CMD=("$JAVA_BIN" -cp "$GRADLE_JAR" org.gradle.wrapper.GradleWrapperMain)
  GRADLE_ARGS=("--parallel" "--build-cache")
  $DO_VERBOSE || GRADLE_ARGS+=("--quiet")

  cd "$ANDROID_DIR"

  if $DO_CLEAN; then
    info "Cleaning build cache..."
    "${GRADLE_CMD[@]}" clean "${GRADLE_ARGS[@]}" 2>&1 \
      | grep -E "^(BUILD|> Task :clean)" | sed 's/^/  /' || true
    ok "Clean done"
  fi

  BUILD_START=$(date +%s)

  if $DO_VERBOSE; then
    "${GRADLE_CMD[@]}" assembleDebug "${GRADLE_ARGS[@]}"
  else
    "${GRADLE_CMD[@]}" assembleDebug "${GRADLE_ARGS[@]}" 2>&1 \
      | grep -E "^(BUILD|FAILURE|error:|> Task :app:|Caused by)" \
      | sed 's/^/  /' || true
  fi

  BUILD_END=$(date +%s)
  BUILD_SECS=$((BUILD_END - BUILD_START))

  APK_PATH=$(find "$ANDROID_DIR/app/build/outputs/apk/debug" -name "*.apk" 2>/dev/null | head -1 || true)
  [[ -z "$APK_PATH" ]] && die "APK not found — run with --verbose to see the full build output"

  APK_SIZE=$(du -h "$APK_PATH" 2>/dev/null | cut -f1 || echo "?")
  ok "Built in ${BUILD_SECS}s — ${APK_SIZE} — $(basename "$APK_PATH")"
  cd "$ROOT"
else
  # --no-build: find the last-built APK
  APK_PATH=$(find "$ANDROID_DIR/app/build/outputs/apk/debug" -name "*.apk" 2>/dev/null \
    | sort -t_ -k1 | tail -1 || true)
  if [[ -z "$APK_PATH" ]]; then
    die "No APK found in app/build/outputs/apk/debug — run without --no-build first"
  fi
  APK_SIZE=$(du -h "$APK_PATH" 2>/dev/null | cut -f1 || echo "?")
  info "Skipping build — using existing APK: $(basename "$APK_PATH") (${APK_SIZE})"
fi

# =============================================================================
# ── INSTALL ───────────────────────────────────────────────────────────────────
# =============================================================================
step "Installing on ${DEVICE_MODEL}"

INSTALL_OUT=$(adb_d install -r "$APK_PATH" 2>&1 || true)
if echo "$INSTALL_OUT" | grep -q "Success"; then
  ok "Installed: ${APP_PACKAGE_DEBUG}"
elif echo "$INSTALL_OUT" | grep -q "INSTALL_FAILED_VERSION_DOWNGRADE"; then
  info "Version downgrade — uninstalling old APK first..."
  adb_d uninstall "$APP_PACKAGE_DEBUG" 2>/dev/null || true
  adb_d install "$APK_PATH" 2>&1 | sed 's/^/  /' || true
  ok "Installed (after uninstall)"
elif echo "$INSTALL_OUT" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
  info "Signature mismatch — uninstalling old APK first..."
  adb_d uninstall "$APP_PACKAGE_DEBUG" 2>/dev/null || true
  adb_d install "$APK_PATH" 2>&1 | sed 's/^/  /' || true
  ok "Installed (after signature fix)"
else
  printf "%s\n" "$INSTALL_OUT" | sed 's/^/  /'
  die "Installation failed — see output above"
fi

# =============================================================================
# ── LAUNCH ────────────────────────────────────────────────────────────────────
# =============================================================================
ACTIVE_PACKAGE="$APP_PACKAGE_DEBUG"

if $DO_LAUNCH; then
  step "Launching app"

  adb_d shell am force-stop "$APP_PACKAGE_DEBUG" 2>/dev/null || true
  adb_d shell am force-stop "$APP_PACKAGE"       2>/dev/null || true

  resolve_launcher() {
    local pkg resolved
    for pkg in "$APP_PACKAGE_DEBUG" "$APP_PACKAGE"; do
      resolved=$(adb_d shell cmd package resolve-activity --brief \
        -a android.intent.action.MAIN \
        -c android.intent.category.LAUNCHER \
        "$pkg" 2>/dev/null | tr -d '\r' | grep '/' | tail -1 || true)
      [[ -n "$resolved" ]] && { echo "$resolved"; return 0; }
    done
    return 1
  }

  LAUNCH_COMPONENT="$(resolve_launcher || true)"

  if [[ -n "$LAUNCH_COMPONENT" ]]; then
    ACTIVE_PACKAGE="${LAUNCH_COMPONENT%%/*}"
    LAUNCH_OUT=$(adb_d shell am start -n "$LAUNCH_COMPONENT" --activity-clear-top 2>&1 || true)
    if echo "$LAUNCH_OUT" | grep -qv "Error\|Exception"; then
      ok "Launched ${ACTIVE_PACKAGE}"
    else
      printf "%s\n" "$LAUNCH_OUT" | grep -v '^$' | sed 's/^/  /'
      warn "am start reported an error — trying monkey fallback..."
      adb_d shell monkey -p "$ACTIVE_PACKAGE" -c android.intent.category.LAUNCHER 1 \
        2>&1 | grep -v "^Events injected" | sed 's/^/  /' || true
    fi
  else
    warn "Could not resolve launcher — APK installed but not auto-launched"
    warn "Open Paperkeep manually on your phone"
  fi
fi

# =============================================================================
# ── LOGCAT ────────────────────────────────────────────────────────────────────
# =============================================================================
if ! $DO_LOGCAT; then
  printf "\n"
  ok "Done — app is running on ${DEVICE_MODEL}."
  printf "\n  Stream logs later:\n"
  printf "    adb -s %s logcat --pid=\$(adb -s %s shell pidof -s %s)\n\n" \
    "$DEVICE_SERIAL" "$DEVICE_SERIAL" "$ACTIVE_PACKAGE"
  exit 0
fi

banner "Logcat — ${DEVICE_MODEL}"
printf "\n"
printf "  ${BOLD}App:${NC}     %s\n"                     "$ACTIVE_PACKAGE"
printf "  ${BOLD}Device:${NC}  %s  (Android %s  API %s)\n" "$DEVICE_MODEL" "$ANDROID_VER" "$ANDROID_API"
printf "\n"
printf "  ${CYAN}Ctrl+C stops logcat — app keeps running on your phone.${NC}\n\n"

# Wait up to 8s for the app process
APP_PID=""
for _i in $(seq 1 8); do
  APP_PID=$(adb_d shell pidof -s "$ACTIVE_PACKAGE" 2>/dev/null | tr -d '\r[:space:]' || true)
  [[ -n "$APP_PID" && "$APP_PID" =~ ^[0-9]+$ ]] && break
  printf "\r  Waiting for app process... [%ds]" "$_i"
  sleep 1
done
printf "\r%40s\r" ""

LOGCAT_ARGS=("-v" "time")
if [[ -n "$APP_PID" && "$APP_PID" =~ ^[0-9]+$ ]]; then
  LOGCAT_ARGS+=("--pid=$APP_PID")
  ok "App process: PID ${APP_PID}"
else
  warn "PID not found — filtering by tag (slightly noisier)"
  LOGCAT_ARGS+=("-s" "paperkeep:V" "Paperkeep:V" "AndroidRuntime:E" "System.err:W")
fi

printf "\n"

INTERRUPTED=false
trap 'INTERRUPTED=true' INT TERM

# Retry loop — Wi-Fi ADB can drop the stream; reconnect automatically
while true; do
  set +e
  "$ADB_BIN" -s "$DEVICE_SERIAL" logcat "${LOGCAT_ARGS[@]}" 2>/dev/null \
    | while IFS= read -r line; do
        case "$line" in
          *" E "*) printf "${RED}%s${NC}\n"    "$line" ;;
          *" W "*) printf "${YELLOW}%s${NC}\n" "$line" ;;
          *" I "*) printf "${GREEN}%s${NC}\n"  "$line" ;;
          *" D "*) printf "${BLUE}%s${NC}\n"   "$line" ;;
          *)       printf "%s\n"               "$line" ;;
        esac
      done
  PIPE_EXIT=${PIPESTATUS[0]}
  set -e

  $INTERRUPTED && break

  warn "Logcat stream ended (adb exit ${PIPE_EXIT}) — reconnecting in 3s..."

  # Attempt Wi-Fi reconnect if we have a saved address
  if [[ -f "$WIFI_STATE_FILE" ]]; then
    # shellcheck source=/dev/null
    source "$WIFI_STATE_FILE" 2>/dev/null || true
    [[ -n "${WIFI_CONNECT_ADDR:-}" ]] && \
      "$ADB_BIN" connect "$WIFI_CONNECT_ADDR" &>/dev/null || true
  fi

  sleep 3

  NEW_PID=$(adb_d shell pidof -s "$ACTIVE_PACKAGE" 2>/dev/null | tr -d '\r[:space:]' || true)
  if [[ -n "$NEW_PID" && "$NEW_PID" =~ ^[0-9]+$ && "$NEW_PID" != "$APP_PID" ]]; then
    APP_PID="$NEW_PID"
    LOGCAT_ARGS=("-v" "time" "--pid=$APP_PID")
    info "App restarted — new PID: ${APP_PID}"
  fi
done

printf "\n"
ok "Logcat stopped. App still running on ${DEVICE_MODEL}."
printf "\n"
