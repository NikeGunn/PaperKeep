#!/usr/bin/env bash
# =============================================================================
# ScanVault — dev-wifi-pair.sh
# One-time Wi-Fi ADB pairing wizard. Run once, then use --wifi forever.
#
# Requires Android 11+. Phone and laptop must be on the same router.
# Laptop on Ethernet is fine — phone on Wi-Fi is fine.
#
# Usage:
#   ./scripts/dev-wifi-pair.sh
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

ok()   { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
info() { printf "${BLUE}[wifi-pair]${NC} %s\n" "$*"; }
warn() { printf "  ${YELLOW}⚠${NC}  %s\n" "$*"; }
die()  { printf "  ${RED}✗${NC} %s\n" "$*"; exit 1; }

# ── Find ADB ──────────────────────────────────────────────────────────────────
ADB_BIN=""
if command -v adb &>/dev/null; then
  ADB_BIN="adb"
elif [[ -f "/c/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools/adb.exe" ]]; then
  ADB_BIN="/c/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools/adb.exe"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
  for ext in ".exe" ""; do
    [[ -f "$ANDROID_HOME/platform-tools/adb${ext}" ]] && {
      ADB_BIN="$ANDROID_HOME/platform-tools/adb${ext}"; break
    }
  done
fi
[[ -z "$ADB_BIN" ]] && die "adb not found. Set ANDROID_HOME or add platform-tools to PATH."

# ── Get laptop IP (Windows-safe, non-blocking) ────────────────────────────────
LAPTOP_IP=""
# Use double-slash for cmd.exe in MSYS2/Git Bash; timeout prevents blocking
if command -v cmd.exe &>/dev/null; then
  LAPTOP_IP=$(timeout 5 cmd.exe //C ipconfig 2>/dev/null \
    | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' \
    | grep '^192\.168\.' | grep -v '\.64\.' | head -1 || true)
fi
# Linux/macOS fallback
if [[ -z "$LAPTOP_IP" ]]; then
  LAPTOP_IP=$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K\S+' | head -1 || true)
fi
LAPTOP_IP="${LAPTOP_IP:-unknown}"

# ── Banner ────────────────────────────────────────────────────────────────────
clear
printf "${CYAN}${BOLD}"
cat <<'EOF'
  Wi-Fi ADB Pairing Wizard
  ─────────────────────────
  Run once. Cable-free forever after.
EOF
printf "${NC}\n"

printf "Your phone must be on the ${BOLD}same router${NC} as this laptop.\n"
printf "(Laptop can be on Ethernet, phone on Wi-Fi — that's fine.)\n\n"
printf "Laptop IP: ${GREEN}${BOLD}${LAPTOP_IP}${NC}\n\n"

# ── Step 1: enable Wireless debugging ────────────────────────────────────────
printf "${YELLOW}${BOLD}STEP 1 — Enable Wireless Debugging on your phone${NC}\n"
printf "  Settings → Developer Options → Wireless debugging → toggle ON\n\n"
printf "  ${CYAN}Press Enter when you see the Wireless debugging screen...${NC}"
read -r

# ── Step 2: pair with code ────────────────────────────────────────────────────
printf "\n${YELLOW}${BOLD}STEP 2 — Get a pairing code${NC}\n"
printf "  Inside 'Wireless debugging', tap ${BOLD}\"Pair device with pairing code\"${NC}\n"
printf "  A popup appears with:\n"
printf "    • A pairing IP:PORT  (e.g. 192.168.1.8:43521)  ← for THIS step\n"
printf "    • A 6-digit code     (e.g. 123456)\n\n"
printf "  ${YELLOW}You have ~60 seconds before the code expires.${NC}\n\n"

read -rp "  Pairing address (IP:PORT shown in popup): " PAIR_ADDR
read -rp "  6-digit pairing code:                     " PAIR_CODE

echo ""
info "Pairing... (this takes a few seconds)"

# Run adb pair with a timeout so it never hangs forever
if ! timeout 30 "$ADB_BIN" pair "$PAIR_ADDR" "$PAIR_CODE"; then
  echo ""
  printf "${RED}Pairing failed or timed out.${NC} Common causes:\n"
  printf "  • Code expired (they last ~60s) — dismiss and tap 'Pair device' again\n"
  printf "  • Wrong IP:PORT — use exactly what's shown in the popup\n"
  printf "  • Phone and laptop on different networks\n"
  printf "\nRe-run: ${CYAN}./scripts/dev-wifi-pair.sh${NC}\n"
  exit 1
fi

ok "Paired!"

# ── Step 3: connect ───────────────────────────────────────────────────────────
printf "\n${YELLOW}${BOLD}STEP 3 — Connect${NC}\n"
printf "  Dismiss the pairing popup. Back on the main 'Wireless debugging' screen,\n"
printf "  you'll see: ${BOLD}IP address & Port${NC}  (e.g. 192.168.1.8:37000)\n"
printf "  This port is ${BOLD}DIFFERENT${NC} from the pairing port above.\n\n"

read -rp "  Connection address (IP:PORT from main screen): " CONNECT_ADDR

echo ""
info "Connecting to $CONNECT_ADDR..."
if ! timeout 15 "$ADB_BIN" connect "$CONNECT_ADDR"; then
  warn "adb connect returned an error — checking device list anyway..."
fi

# ── Verify ────────────────────────────────────────────────────────────────────
echo ""
DEVICE_IP="${CONNECT_ADDR%%:*}"
DEVICES=$("$ADB_BIN" devices 2>/dev/null | grep 'device$' | awk '{print $1}' || true)

if echo "$DEVICES" | grep -q "$DEVICE_IP"; then
  PHONE_MODEL=$("$ADB_BIN" -s "${CONNECT_ADDR}" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "Phone")
  ok "Connected over Wi-Fi: ${PHONE_MODEL} (${CONNECT_ADDR})"
else
  warn "Device not confirmed in 'adb devices' — it may still be connecting."
  warn "Run: adb devices   to check manually"
fi

# ── Save config ───────────────────────────────────────────────────────────────
WIFI_CONFIG="$HOME/.scanvault-wifi-adb"
cat > "$WIFI_CONFIG" <<CONF
# ScanVault Wi-Fi ADB — saved connection
# Paired: $(date)
# Re-run ./scripts/dev-wifi-pair.sh if --wifi stops working after a reboot.
WIFI_ADB_ADDRESS=${CONNECT_ADDR}
CONF

ok "Saved to $WIFI_CONFIG"

# ── Done ──────────────────────────────────────────────────────────────────────
printf "\n${CYAN}${BOLD}All set! From now on:${NC}\n\n"
printf "  ${GREEN}${BOLD}./scripts/dev.sh --wifi${NC}   ← runs everything over Wi-Fi, no cable\n\n"
printf "${YELLOW}After each phone reboot:${NC}\n"
printf "  The connection PORT changes (IP stays the same).\n"
printf "  Fix: Settings → Wireless debugging → note new port\n"
printf "  Then: ${CYAN}adb connect ${DEVICE_IP}:<new-port>${NC}\n"
printf "  (No need to pair again — pairing survives reboots.)\n\n"
