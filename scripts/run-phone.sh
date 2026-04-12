#!/usr/bin/env bash
# =============================================================================
# ScanVault — run-phone.sh
# Build, install, and run the Android app on a connected physical device.
# Streams filtered logcat with colourised output.
#
# Usage:
#   ./scripts/run-phone.sh [OPTIONS]
#
# This is the script you run 50 times a day during development.
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
run-phone.sh — Build & run ScanVault on a connected Android device

USAGE:
  ./scripts/run-phone.sh [OPTIONS]

OPTIONS:
  --help, -h            Show this help message and exit
  --clean               Run ./gradlew clean before building (slow, for weird build states)
  --release             Build release-signed debug APK instead of debug
  --profile             Enable Perfetto method tracing; pull trace on Ctrl+C
  --benchmark           Run macrobenchmark suite instead of launching the app
  --wifi                Connect device over Wi-Fi ADB (pair once, then cable-free)
  --backend <URL>       Override API base URL (e.g. --backend http://192.168.1.5:8080)
  --variant <variant>   Gradle variant to assemble (default: debug)
  --device <serial>     Target a specific device serial (default: first connected)

EXAMPLES:
  ./scripts/run-phone.sh
      Standard debug build on the first connected device.

  ./scripts/run-phone.sh --wifi --backend http://192.168.1.5:8080
      Cable-free development with local backend.

  ./scripts/run-phone.sh --clean
      Clean build (use when gradle cache causes weird failures).

  ./scripts/run-phone.sh --profile
      Build with tracing, stream logs; on Ctrl+C pulls Perfetto trace.

NOTES:
  • Requires: adb (Android SDK platform-tools) in PATH
  • Requires: android/ directory with gradlew set up (task 1B.1)
  • The --wifi flag uses `adb pair` + `adb connect` (Android 11+ only)
  • Device must have USB debugging enabled (or Wi-Fi debugging for --wifi)
  • Logcat is filtered to com.scanvault.app package only
  • Press Ctrl+C to stop logcat — the app keeps running on the device

WHY PHYSICAL DEVICE:
  You cannot properly test a document scanner on an emulator.
  Emulator cameras give synthetic frames; edge detection looks perfect
  when it isn't. Test on real hardware constantly.
EOF
  exit 0
fi

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
ANDROID_DIR="$ROOT/android"
APP_PACKAGE="com.scanvault.app"
APP_PACKAGE_DEBUG="${APP_PACKAGE}.debug"

info()  { printf "${BLUE}[run-phone]${NC} %s\n" "$*"; }
ok()    { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
warn()  { printf "  ${YELLOW}!${NC} %s\n" "$*"; }
die()   { printf "  ${RED}✗${NC} %s\n" "$*"; exit 1; }

resolve_launcher_component() {
  local pkg=""
  local resolved=""

  for pkg in "$APP_PACKAGE_DEBUG" "$APP_PACKAGE"; do
    resolved=$($ADB shell cmd package resolve-activity --brief \
      -a android.intent.action.MAIN \
      -c android.intent.category.LAUNCHER \
      "$pkg" 2>/dev/null | tr -d '\r' | grep '/' | tail -1 || true)
    if [[ -n "$resolved" ]]; then
      echo "$resolved"
      return 0
    fi
  done

  return 1
}

# -----------------------------------------------------------------------------
# Parse flags
# -----------------------------------------------------------------------------
DO_CLEAN=false
DO_RELEASE=false
DO_PROFILE=false
DO_BENCHMARK=false
DO_WIFI=false
BACKEND_URL=""
VARIANT="debug"
TARGET_DEVICE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --clean)       DO_CLEAN=true; shift ;;
    --release)     DO_RELEASE=true; VARIANT="release"; shift ;;
    --profile)     DO_PROFILE=true; shift ;;
    --benchmark)   DO_BENCHMARK=true; shift ;;
    --wifi)        DO_WIFI=true; shift ;;
    --backend)     BACKEND_URL="$2"; shift 2 ;;
    --variant)     VARIANT="$2"; shift 2 ;;
    --device)      TARGET_DEVICE="$2"; shift 2 ;;
    *) die "Unknown flag: $1 (use --help)" ;;
  esac
done

# -----------------------------------------------------------------------------
# ADB path resolution (Windows Git Bash + native Linux/Mac)
# -----------------------------------------------------------------------------
find_adb() {
  command -v adb &>/dev/null && { echo "adb"; return; }
  local win_sdk="/c/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools/adb.exe"
  [[ -f "$win_sdk" ]] && { echo "$win_sdk"; return; }
  [[ -n "${ANDROID_HOME:-}" && -f "$ANDROID_HOME/platform-tools/adb.exe" ]] && \
    { echo "$ANDROID_HOME/platform-tools/adb.exe"; return; }
  [[ -n "${ANDROID_HOME:-}" && -f "$ANDROID_HOME/platform-tools/adb" ]] && \
    { echo "$ANDROID_HOME/platform-tools/adb"; return; }
  echo ""
}
ADB_BIN="$(find_adb)"
[[ -z "$ADB_BIN" ]] && die "adb not found — add Android SDK platform-tools to PATH or set ANDROID_HOME"

# Prerequisite checks


if [[ ! -d "$ANDROID_DIR" ]]; then
  die "android/ directory not found — has task 1B.1 been completed?"
fi

if [[ ! -f "$ANDROID_DIR/gradlew" ]]; then
  die "android/gradlew not found — run task 1B.1 first to set up the Android project"
fi

# -----------------------------------------------------------------------------
# Step 1: Wi-Fi ADB pairing
# -----------------------------------------------------------------------------
if $DO_WIFI; then
  info "Wi-Fi ADB mode"
  echo ""
  printf "${CYAN}On your phone:${NC}\n"
  printf "  Settings → Developer Options → Wireless debugging → Pair device with pairing code\n"
  printf "  Note the IP:PORT shown for pairing.\n"
  echo ""
  read -rp "  Enter pairing address (IP:PORT): " PAIR_ADDR
  read -rp "  Enter pairing code: " PAIR_CODE
  "$ADB_BIN" pair "$PAIR_ADDR" "$PAIR_CODE"
  echo ""
  printf "  Now note the IP:PORT shown under 'Wireless debugging' (main screen).\n"
  read -rp "  Enter connection address (IP:PORT): " CONNECT_ADDR
  "$ADB_BIN" connect "$CONNECT_ADDR"
  ok "Wi-Fi ADB connected"
fi

# -----------------------------------------------------------------------------
# Step 2: Find target device
# -----------------------------------------------------------------------------
info "Looking for connected devices..."

# Build adb selector
ADB_OPTS=()
if [[ -n "$TARGET_DEVICE" ]]; then
  ADB_OPTS=(-s "$TARGET_DEVICE")
fi

DEVICE_LIST=$("$ADB_BIN" devices 2>/dev/null | grep -v '^List' | grep 'device$' | awk '{print $1}')
DEVICE_COUNT=$(echo "$DEVICE_LIST" | grep -c '.' || true)

if [[ "$DEVICE_COUNT" -eq 0 ]]; then
  echo ""
  printf "${RED}No devices connected.${NC}\n"
  printf "To connect a device:\n"
  printf "  USB:    Enable USB debugging on device (Settings → Developer Options)\n"
  printf "          Connect USB cable, accept debugging prompt on device\n"
  printf "  Wi-Fi:  Run: ./scripts/run-phone.sh --wifi\n"
  printf "  Check:  adb devices\n"
  exit 1
fi

if [[ -n "$TARGET_DEVICE" ]]; then
  DEVICE_SERIAL="$TARGET_DEVICE"
  ok "Targeting specified device: $DEVICE_SERIAL"
else
  DEVICE_SERIAL=$(echo "$DEVICE_LIST" | head -1)
  if [[ "$DEVICE_COUNT" -gt 1 ]]; then
    warn "Multiple devices found — using first: $DEVICE_SERIAL"
    warn "Use --device <serial> to target a specific device"
    adb devices | grep 'device$'
  else
    ok "Device found: $DEVICE_SERIAL"
  fi
fi

ADB="$ADB_BIN -s $DEVICE_SERIAL"

# Print device info
DEVICE_MODEL=$($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "unknown")
ANDROID_VER=$($ADB shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "unknown")
ok "  $DEVICE_MODEL (Android $ANDROID_VER)"

# -----------------------------------------------------------------------------
# Step 3: Determine backend URL
# -----------------------------------------------------------------------------
if [[ -z "$BACKEND_URL" ]]; then
  # Try to detect local backend
  LAN_IP=""
  if command -v ip &>/dev/null; then
    LAN_IP=$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K[^\s]+' | head -1 || true)
  elif command -v ifconfig &>/dev/null; then
    LAN_IP=$(ifconfig | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1 || true)
  fi
  if [[ -n "$LAN_IP" ]]; then
    BACKEND_URL="http://${LAN_IP}:8080"
    info "Backend URL: $BACKEND_URL (auto-detected LAN IP)"
    info "Override with: --backend <URL>"
  fi
fi

# -----------------------------------------------------------------------------
# Step 4: Gradle build
# -----------------------------------------------------------------------------
info "Building $VARIANT APK..."
cd "$ANDROID_DIR"

GRADLE_ARGS=(
  "--parallel"
  "--configuration-cache"
  "--build-cache"
  "-PapiBaseUrl=${BACKEND_URL:-https://api.scanvault.app}"
)

if $DO_PROFILE; then
  GRADLE_ARGS+=("--profile")
fi

if $DO_CLEAN; then
  info "Running clean first..."
  ./gradlew clean "${GRADLE_ARGS[@]}"
fi

if $DO_BENCHMARK; then
  info "Running macrobenchmark suite..."
  ./gradlew :benchmark:connectedBenchmarkAndroidTest "${GRADLE_ARGS[@]}"
  ok "Benchmarks complete — results in android/benchmark/build/outputs/connected_android_test_additional_output/"
  exit 0
fi

ASSEMBLE_TASK="assemble$(echo "${VARIANT:0:1}" | tr '[:lower:]' '[:upper:]')${VARIANT:1}"
./gradlew "$ASSEMBLE_TASK" "${GRADLE_ARGS[@]}"
ok "$VARIANT APK built"

# Find the APK
APK_PATH=$(find "$ANDROID_DIR/app/build/outputs/apk" -name "*.apk" -path "*/${VARIANT}/*" | head -1 || true)
if [[ -z "$APK_PATH" ]]; then
  die "APK not found in android/app/build/outputs/apk/${VARIANT}/ — did the build succeed?"
fi
ok "APK: $APK_PATH"

cd "$ROOT"

# -----------------------------------------------------------------------------
# Step 5: Install APK
# -----------------------------------------------------------------------------
info "Installing APK on $DEVICE_MODEL..."
$ADB install -r "$APK_PATH"
ok "APK installed"

# -----------------------------------------------------------------------------
# Step 6: Launch app
# -----------------------------------------------------------------------------
LAUNCH_COMPONENT="$(resolve_launcher_component || true)"
ACTIVE_PACKAGE="$APP_PACKAGE_DEBUG"

if [[ -z "$LAUNCH_COMPONENT" ]]; then
  warn "Could not resolve launcher activity for $APP_PACKAGE_DEBUG or $APP_PACKAGE"
  warn "APK installed, but auto-launch was skipped. Launch manually on device."
else
  ACTIVE_PACKAGE="${LAUNCH_COMPONENT%%/*}"
  info "Launching $LAUNCH_COMPONENT..."

  set +e
  START_OUT=$($ADB shell am start -n "$LAUNCH_COMPONENT" --activity-clear-top 2>&1)
  START_EXIT=$?
  set -e
  printf "%s\n" "$START_OUT" | sed 's/^/  /'

  if [[ "$START_EXIT" -eq 0 ]]; then
    ok "App launched"
  else
    warn "Component launch failed (exit $START_EXIT). Trying fallback launcher intent..."
    set +e
    MONKEY_OUT=$($ADB shell monkey -p "$ACTIVE_PACKAGE" -c android.intent.category.LAUNCHER 1 2>&1)
    MONKEY_EXIT=$?
    set -e
    printf "%s\n" "$MONKEY_OUT" | sed 's/^/  /'

    if [[ "$MONKEY_EXIT" -eq 0 ]]; then
      ok "App launched (fallback)"
    else
      warn "Fallback launch failed (exit $MONKEY_EXIT)."
    fi
  fi
fi

# -----------------------------------------------------------------------------
# Step 7: Perfetto profiling setup
# -----------------------------------------------------------------------------
if $DO_PROFILE; then
  TRACE_FILE="/data/local/tmp/scanvault-trace-$(date +%Y%m%d-%H%M%S).perfetto"
  info "Starting Perfetto trace → $TRACE_FILE"
  $ADB shell "perfetto -o $TRACE_FILE -t 300s -c - <<'PERFETTO_EOF'
buffers: { size_kb: 65536 }
data_sources: { config { name: \"linux.process_stats\" } }
data_sources: { config { name: \"android.surfaceflinger.frame\" } }
PERFETTO_EOF" &
  PERFETTO_PID=$!
fi

# -----------------------------------------------------------------------------
# Step 8: Stream logcat
# -----------------------------------------------------------------------------
info "Streaming logcat (Ctrl+C to stop)..."
echo ""
printf "${CYAN}══════════════════════════════════════════${NC}\n"
printf "${CYAN}  App:     ${NC}$ACTIVE_PACKAGE\n"
printf "${CYAN}  Device:  ${NC}$DEVICE_MODEL (Android $ANDROID_VER)\n"
[[ -n "$BACKEND_URL" ]] && printf "${CYAN}  Backend: ${NC}$BACKEND_URL\n"
printf "${CYAN}══════════════════════════════════════════${NC}\n"
echo ""

# Cleanup on Ctrl+C
cleanup() {
  echo ""
  info "Stopping logcat..."

  if $DO_PROFILE && [[ -n "${PERFETTO_PID:-}" ]]; then
    info "Stopping Perfetto trace..."
    kill "$PERFETTO_PID" 2>/dev/null || true
    wait "$PERFETTO_PID" 2>/dev/null || true
    LOCAL_TRACE="$ROOT/scanvault-trace-$(date +%Y%m%d-%H%M%S).perfetto"
    $ADB pull "$TRACE_FILE" "$LOCAL_TRACE" 2>/dev/null || true
    ok "Trace saved: $LOCAL_TRACE"
    if command -v open &>/dev/null; then
      open "https://ui.perfetto.dev" 2>/dev/null || true
    fi
  fi
}
trap cleanup INT TERM

# Colourised logcat — filter to our package, colourize by level
APP_PID="$($ADB shell pidof -s "$ACTIVE_PACKAGE" 2>/dev/null | tr -d '\r' || true)"
LOGCAT_ARGS=("-v" "time")
if [[ -n "$APP_PID" && "$APP_PID" =~ ^[0-9]+$ ]]; then
  LOGCAT_ARGS+=("--pid=$APP_PID")
else
  warn "App PID not found yet — using tag filters"
  LOGCAT_ARGS+=("-s" "ScanVault:V" "AndroidRuntime:E" "System.err:W")
fi

$ADB logcat "${LOGCAT_ARGS[@]}" 2>/dev/null \
  | while IFS= read -r line; do
    case "$line" in
      *" E "*) printf "${RED}%s${NC}\n" "$line" ;;
      *" W "*) printf "${YELLOW}%s${NC}\n" "$line" ;;
      *" I "*) printf "${GREEN}%s${NC}\n" "$line" ;;
      *" D "*) printf "${BLUE}%s${NC}\n" "$line" ;;
      *)       printf "%s\n" "$line" ;;
    esac
  done
