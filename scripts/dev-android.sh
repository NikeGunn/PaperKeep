#!/usr/bin/env bash
# =============================================================================
# Paperkeep — dev-android.sh
#
# Build the debug APK, install it on your physical phone, launch it, and
# stream colourised logcat — all in one command.
#
# No backend. No Docker. No AWS. Just your phone.
#
# Usage:
#   ./scripts/dev-android.sh              # build → install → launch → logcat
#   ./scripts/dev-android.sh --clean      # clean build cache first
#   ./scripts/dev-android.sh --no-launch  # install but don't auto-launch
#   ./scripts/dev-android.sh --no-logcat  # launch but don't stream logs
#   ./scripts/dev-android.sh --wifi       # connect over Wi-Fi ADB (cable-free)
#   ./scripts/dev-android.sh --device <serial>  # target a specific phone
#   ./scripts/dev-android.sh --verbose    # show full Gradle output
#   ./scripts/dev-android.sh --help
# =============================================================================

set -euo pipefail

# ── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m';  GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m';  BOLD='\033[1m';  NC='\033[0m'

# ── Paths ────────────────────────────────────��────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$ROOT/android"
APP_PACKAGE="app.paperkeep"
APP_PACKAGE_DEBUG="${APP_PACKAGE}.debug"

# ── Helpers ──��────────────────────────────────────────────────────────────────
info()   { printf "${BLUE}[paperkeep]${NC} %s\n" "$*"; }
ok()     { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
warn()   { printf "  ${YELLOW}!${NC} %s\n" "$*"; }
die()    { printf "\n  ${RED}✗ %s${NC}\n\n" "$*"; exit 1; }
banner() { printf "\n${CYAN}${BOLD}── %s ──${NC}\n" "$*"; }
step()   { printf "\n${YELLOW}▶ %s${NC}\n" "$*"; }

# ── Help ──���───────────────────────────────────────────────────────────────────
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'HELP'

dev-android.sh — Paperkeep phone dev loop

USAGE
  ./scripts/dev-android.sh [OPTIONS]

OPTIONS
  --clean           Run ./gradlew clean before building (use when build cache
                    causes weird failures; adds ~30s)
  --no-launch       Install the APK but don't auto-launch the app
  --no-logcat       Launch the app but don't stream logcat
  --wifi            Connect to your phone over Wi-Fi ADB (Android 11+)
                    You will be prompted for the pairing address + code
  --device SERIAL   Target a specific device when multiple are connected
                    (find serial with: adb devices)
  --verbose         Show the full Gradle build output instead of a summary
  --help, -h        Show this message and exit

FIRST-TIME SETUP
  1. On your phone: Settings → About Phone → tap Build Number 7 times
     This enables Developer Options.
  2. Settings → Developer Options → USB Debugging → ON
  3. Connect the USB cable to your computer
  4. Accept the "Allow USB debugging?" prompt on the phone
  5. Run: ./scripts/dev-android.sh

WI-FI (cable-free, Android 11+)
  1. Settings → Developer Options → Wireless Debugging → ON
  2. Run: ./scripts/dev-android.sh --wifi
  3. Follow the prompts (pairing code shown in Wireless Debugging settings)
  From now on, run with --wifi — no cable needed (same Wi-Fi network required).

WHAT YOU'LL SEE
  On first launch: Onboarding (3 pages, then Scanner screen)
  Flow: Onboarding → Scanner/Camera → Crop → Library → Reader → Settings
  All screens are fully wired. Camera requires physical hardware — works
  on a real phone, not an emulator.

KEYBOARD SHORTCUTS (while logcat is streaming)
  Ctrl+C   Stop logcat — the app keeps running on your phone

NOTES
  • The APK is app.paperkeep.debug — installed alongside any other build.
  • Encrypted image store and biometric lock require the hardware Keystore;
    they work fully on a real phone but not on an emulator.
  • No backend, no internet connection required. 100% on-device.

HELP
  exit 0
fi

# ── Parse flags ───────────────────────────────────────────────────────────────
DO_CLEAN=false
DO_LAUNCH=true
DO_LOGCAT=true
DO_WIFI=false
DO_VERBOSE=false
TARGET_DEVICE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --clean)      DO_CLEAN=true;   shift ;;
    --no-launch)  DO_LAUNCH=false; shift ;;
    --no-logcat)  DO_LOGCAT=false; shift ;;
    --wifi)       DO_WIFI=true;    shift ;;
    --verbose)    DO_VERBOSE=true; shift ;;
    --device)     TARGET_DEVICE="$2"; shift 2 ;;
    *) die "Unknown flag: $1  (run --help for usage)" ;;
  esac
done

# ── Pre-flight: find Gradle wrapper ──────────────────────────────────────────
[[ -d "$ANDROID_DIR" ]]          || die "android/ directory not found at $ANDROID_DIR"

# Use the Java + gradle-wrapper.jar directly (works on Windows without gradlew.bat)
GRADLE_JAR="$ANDROID_DIR/gradle/wrapper/gradle-wrapper.jar"
GRADLE_PROPS="$ANDROID_DIR/gradle/wrapper/gradle-wrapper.properties"
[[ -f "$GRADLE_JAR" ]]   || die "gradle-wrapper.jar not found at $GRADLE_JAR"
[[ -f "$GRADLE_PROPS" ]] || die "gradle-wrapper.properties not found at $GRADLE_PROPS"

# Find Java 17
find_java() {
  # 1. JAVA_HOME
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    echo "$JAVA_HOME/bin/java"; return
  fi
  # 2. Corretto 17 (the project's known-good JDK on this machine)
  local corretto="/c/Program Files/Amazon Corretto/jdk17.0.16_8/bin/java.exe"
  [[ -f "$corretto" ]] && { echo "$corretto"; return; }
  # 3. System java
  if command -v java &>/dev/null; then
    echo "java"; return
  fi
  echo ""
}

JAVA_BIN="$(find_java)"
[[ -z "$JAVA_BIN" ]] && die "Java 17 not found. Set JAVA_HOME or install Amazon Corretto 17."

# ── Pre-flight: find adb ─────────────────────────────��────────────────────────
find_adb() {
  command -v adb &>/dev/null && { echo "adb"; return; }
  # Windows SDK — hardcoded known path on this machine
  local win_sdk="/c/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools/adb.exe"
  [[ -f "$win_sdk" ]] && { echo "$win_sdk"; return; }
  # ANDROID_HOME env
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    [[ -f "$ANDROID_HOME/platform-tools/adb.exe" ]] && { echo "$ANDROID_HOME/platform-tools/adb.exe"; return; }
    [[ -f "$ANDROID_HOME/platform-tools/adb"     ]] && { echo "$ANDROID_HOME/platform-tools/adb";     return; }
  fi
  # Common Linux paths
  for p in "$HOME/Android/Sdk/platform-tools/adb" "/usr/bin/adb"; do
    [[ -f "$p" ]] && { echo "$p"; return; }
  done
  echo ""
}

ADB_BIN="$(find_adb)"
[[ -z "$ADB_BIN" ]] && die "adb not found.\nAdd Android SDK platform-tools to PATH or set ANDROID_HOME.\nDefault location: C:/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools"

adb() { "$ADB_BIN" "$@"; }

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

# ── Wi-Fi ADB pairing ─────��───────────────────────────────────────────────────
if $DO_WIFI; then
  banner "Wi-Fi ADB setup"
  printf "\n"
  printf "  On your phone:\n"
  printf "    Settings → Developer Options → Wireless Debugging → ON\n"
  printf "    Tap ${BOLD}\"Pair device with pairing code\"${NC}\n\n"

  read -rp "  Pairing address (IP:PORT shown on phone): " PAIR_ADDR
  read -rp "  Pairing code (6-digit number on phone):  " PAIR_CODE

  printf "\n"
  "$ADB_BIN" pair "$PAIR_ADDR" "$PAIR_CODE" || die "Pairing failed — check address and code"
  ok "Paired"

  printf "\n  Now look at the main Wireless Debugging screen (not the pairing screen).\n"
  printf "  It shows a second IP:PORT — use that one to connect.\n\n"
  read -rp "  Connection address (IP:PORT from main screen): " CONNECT_ADDR
  "$ADB_BIN" connect "$CONNECT_ADDR" || die "Connection failed — is the phone on the same Wi-Fi network?"
  ok "Connected over Wi-Fi: $CONNECT_ADDR"
fi

# ── Find device ───────────────────────────────────────────────────────────────
step "Connecting to device"

# Wait up to 20 s for a device — handles slow USB auth dialogs
DEVICE_SERIAL=""
WAIT=0
while [[ $WAIT -lt 20 ]]; do
  if [[ -n "$TARGET_DEVICE" ]]; then
    # Check the requested serial is present and authorized
    if "$ADB_BIN" devices | grep -q "^${TARGET_DEVICE}.*device$"; then
      DEVICE_SERIAL="$TARGET_DEVICE"
      break
    fi
  else
    DEVICE_SERIAL=$("$ADB_BIN" devices 2>/dev/null \
      | grep -v '^List' | grep $'\tdevice$' | awk '{print $1}' | head -1 || true)
    [[ -n "$DEVICE_SERIAL" ]] && break
  fi

  # Show what ADB can see so the user knows what's happening
  UNAUTH=$("$ADB_BIN" devices 2>/dev/null | grep 'unauthorized' | awk '{print $1}' || true)
  if [[ -n "$UNAUTH" ]]; then
    printf "\r  ${YELLOW}!${NC}  Device %s is unauthorized — accept the USB debugging prompt on your phone...  [%ds]  " "$UNAUTH" "$WAIT"
  else
    printf "\r  Waiting for authorized device... [%ds]  " "$WAIT"
  fi

  sleep 1
  WAIT=$((WAIT + 1))
done
printf "\r%60s\r" ""   # clear the waiting line

if [[ -z "$DEVICE_SERIAL" ]]; then
  printf "\n  ${RED}No authorized device found after ${WAIT}s.${NC}\n\n"
  printf "  USB debugging checklist:\n"
  printf "    1. Settings → About Phone → tap Build Number 7 times\n"
  printf "    2. Settings → Developer Options → USB Debugging → ON\n"
  printf "    3. Connect USB cable\n"
  printf "    4. Accept the \"Allow USB debugging?\" popup on the phone\n"
  printf "    5. Try: adb devices   (should show serial + 'device')\n\n"
  printf "  Wi-Fi alternative: ./scripts/dev-android.sh --wifi\n\n"
  exit 1
fi

# Multiple devices?
ALL_DEVICES=$("$ADB_BIN" devices 2>/dev/null | grep $'\tdevice$' | awk '{print $1}' || true)
DEVICE_COUNT=$(printf '%s\n' "$ALL_DEVICES" | grep -c '.' || true)
if [[ "$DEVICE_COUNT" -gt 1 && -z "$TARGET_DEVICE" ]]; then
  warn "Multiple devices found — using: $DEVICE_SERIAL"
  warn "Pass --device <serial> to pick a specific one"
  printf "\n  Connected devices:\n"
  "$ADB_BIN" devices | grep $'\tdevice$' | awk '{printf "    %s\n", $1}'
  printf "\n"
fi

adb_d() { "$ADB_BIN" -s "$DEVICE_SERIAL" "$@"; }

DEVICE_MODEL=$(adb_d shell getprop ro.product.model  2>/dev/null | tr -d '\r' || echo "Phone")
ANDROID_VER=$(adb_d shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "?")
ANDROID_API=$(adb_d shell getprop ro.build.version.sdk   2>/dev/null | tr -d '\r' || echo "?")

ok "Device : ${BOLD}${DEVICE_MODEL}${NC}"
ok "Android: ${BOLD}${ANDROID_VER}${NC} (API ${ANDROID_API})  serial: ${DEVICE_SERIAL}"

# ── Gradle build ──────────────────────────────────────────────────────────────
step "Building debug APK"

GRADLE_CMD=("$JAVA_BIN" -cp "$GRADLE_JAR" org.gradle.wrapper.GradleWrapperMain)
GRADLE_ARGS=(
  "--parallel"
  "--build-cache"
)
$DO_VERBOSE || GRADLE_ARGS+=("--quiet")

cd "$ANDROID_DIR"

if $DO_CLEAN; then
  info "Cleaning build cache..."
  "${GRADLE_CMD[@]}" clean "${GRADLE_ARGS[@]}" 2>&1 \
    | grep -E "^(BUILD|> Task :clean|Cleaned)" | sed 's/^/  /' || true
  ok "Clean done"
fi

BUILD_START=$(date +%s)

if $DO_VERBOSE; then
  "${GRADLE_CMD[@]}" assembleDebug "${GRADLE_ARGS[@]}"
else
  # Show only errors + final BUILD line; suppress the wall of UP-TO-DATE tasks
  "${GRADLE_CMD[@]}" assembleDebug "${GRADLE_ARGS[@]}" 2>&1 \
    | grep -E "^(BUILD|FAILURE|error:|> Task :app:|Caused by)" \
    | sed 's/^/  /' || true
fi

BUILD_END=$(date +%s)
BUILD_SECS=$((BUILD_END - BUILD_START))

APK_PATH=$(find "$ANDROID_DIR/app/build/outputs/apk/debug" -name "*.apk" 2>/dev/null | head -1 || true)
[[ -z "$APK_PATH" ]] && die "APK not found after build — run with --verbose to see the full output"

APK_SIZE=$(du -h "$APK_PATH" 2>/dev/null | cut -f1 || echo "?")
ok "Built in ${BUILD_SECS}s — ${APK_SIZE} — $(basename "$APK_PATH")"

cd "$ROOT"

# ── Install ───────────────────────────────────────────────────────────────────
step "Installing on ${DEVICE_MODEL}"

INSTALL_OUT=$(adb_d install -r "$APK_PATH" 2>&1 || true)
if echo "$INSTALL_OUT" | grep -q "Success"; then
  ok "Installed: ${APP_PACKAGE_DEBUG}"
elif echo "$INSTALL_OUT" | grep -q "INSTALL_FAILED_VERSION_DOWNGRADE"; then
  info "Version downgrade detected — uninstalling old APK first..."
  adb_d uninstall "$APP_PACKAGE_DEBUG" 2>/dev/null || true
  adb_d install "$APK_PATH" 2>&1 | sed 's/^/  /' || true
  ok "Installed (after uninstall)"
else
  printf "%s\n" "$INSTALL_OUT" | sed 's/^/  /'
  die "Installation failed — see output above"
fi

# ── Launch ────────────────────────────────────────────────────────────────────
ACTIVE_PACKAGE="$APP_PACKAGE_DEBUG"

if $DO_LAUNCH; then
  step "Launching app"

  # Kill any existing instance so we start fresh
  adb_d shell am force-stop "$APP_PACKAGE_DEBUG" 2>/dev/null || true
  adb_d shell am force-stop "$APP_PACKAGE"       2>/dev/null || true

  # Resolve the actual launcher activity (handles any MainActivity name)
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
    printf "%s\n" "$LAUNCH_OUT" | grep -v '^$' | sed 's/^/  /'
    if echo "$LAUNCH_OUT" | grep -q "Error\|Exception"; then
      warn "am start reported an error — trying monkey fallback..."
      adb_d shell monkey -p "$ACTIVE_PACKAGE" -c android.intent.category.LAUNCHER 1 \
        2>&1 | grep -v "^Events injected" | sed 's/^/  /' || true
    else
      ok "Launched ${ACTIVE_PACKAGE}"
    fi
  else
    warn "Could not resolve launcher activity — APK installed but not launched"
    warn "Open Paperkeep manually on your phone"
  fi
fi

# ── Logcat ────────────────────────────────────────────────────────────────────
if ! $DO_LOGCAT; then
  printf "\n"
  ok "Done. App is running on ${DEVICE_MODEL}."
  printf "\n  To stream logs later:\n"
  printf "    adb -s %s logcat --pid=\$(adb -s %s shell pidof -s %s)\n\n" \
    "$DEVICE_SERIAL" "$DEVICE_SERIAL" "$ACTIVE_PACKAGE"
  exit 0
fi

banner "Logcat — ${DEVICE_MODEL}"
printf "\n"
printf "  ${BOLD}App:${NC}     %s\n" "$ACTIVE_PACKAGE"
printf "  ${BOLD}Device:${NC}  %s  (Android %s  API %s)\n" "$DEVICE_MODEL" "$ANDROID_VER" "$ANDROID_API"
printf "\n"
printf "  ${CYAN}Ctrl+C to stop logcat — the app keeps running on your phone.${NC}\n"
printf "\n"

# Wait up to 8 s for the app process to appear
APP_PID=""
for i in $(seq 1 8); do
  APP_PID=$(adb_d shell pidof -s "$ACTIVE_PACKAGE" 2>/dev/null | tr -d '\r[:space:]' || true)
  [[ -n "$APP_PID" && "$APP_PID" =~ ^[0-9]+$ ]] && break
  printf "\r  Waiting for app process... [%ds]" "$i"
  sleep 1
done
printf "\r%40s\r" ""

LOGCAT_ARGS=("-v" "time")
if [[ -n "$APP_PID" && "$APP_PID" =~ ^[0-9]+$ ]]; then
  LOGCAT_ARGS+=("--pid=$APP_PID")
  ok "App process: PID ${APP_PID}"
else
  # Fallback: filter by tag — slightly noisier but always works
  warn "App PID not found — filtering by package tag (may include some system noise)"
  LOGCAT_ARGS+=("-s" "paperkeep:V" "Paperkeep:V" "AndroidRuntime:E" "System.err:W")
fi

printf "\n"

# Graceful shutdown on Ctrl+C
INTERRUPTED=false
trap 'INTERRUPTED=true' INT TERM

# Colour map: E=red  W=yellow  I=green  D=blue  V=plain
# Retry loop: Wi-Fi ADB can drop the stream unexpectedly
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

  # ADB dropped — retry with a fresh PID in case the app restarted
  warn "Logcat stream ended (adb exit ${PIPE_EXIT}) — reconnecting in 3s..."
  sleep 3

  NEW_PID=$(adb_d shell pidof -s "$ACTIVE_PACKAGE" 2>/dev/null | tr -d '\r[:space:]' || true)
  if [[ -n "$NEW_PID" && "$NEW_PID" =~ ^[0-9]+$ && "$NEW_PID" != "$APP_PID" ]]; then
    APP_PID="$NEW_PID"
    LOGCAT_ARGS=("-v" "time" "--pid=$APP_PID")
    info "App restarted — new PID: ${APP_PID}"
  fi
done

printf "\n"
ok "Logcat stopped. App is still running on ${DEVICE_MODEL}."
printf "\n"
