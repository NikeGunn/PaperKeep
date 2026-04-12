#!/usr/bin/env bash
# =============================================================================
# ScanVault — dev-wifi-pair.sh
# One-time Wi-Fi ADB pairing wizard. Run once, then use --wifi forever.
#
# Requires Android 11+. Phone must be on the same Wi-Fi network as laptop.
# Laptop can be on Ethernet — they just need to share the same router.
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
if command -v adb &>/dev/null; then ADB_BIN="adb";
elif [[ -f "/c/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools/adb.exe" ]]; then
  ADB_BIN="/c/Users/Nautilus/AppData/Local/Android/Sdk/platform-tools/adb.exe"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
  for ext in ".exe" ""; do
    [[ -f "$ANDROID_HOME/platform-tools/adb${ext}" ]] && { ADB_BIN="$ANDROID_HOME/platform-tools/adb${ext}"; break; }
  done
fi
[[ -z "$ADB_BIN" ]] && die "adb not found. Set ANDROID_HOME or add platform-tools to PATH."

# ── Get laptop IP ─────────────────────────────────────────────────────────────
LAPTOP_IP=""
if command -v cmd.exe &>/dev/null; then
  LAPTOP_IP=$(cmd.exe /C "ipconfig" 2>/dev/null \
    | grep -A 1 "Ethernet" | grep "IPv4" \
    | grep -oE '192\.168\.[0-9]+\.[0-9]+' | head -1)
  if [[ -z "$LAPTOP_IP" ]]; then
    LAPTOP_IP=$(cmd.exe /C "ipconfig" 2>/dev/null \
      | grep "IPv4" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' \
      | grep -v '^127\.' | grep -v '^169\.' | grep -v '^172\.' | head -1)
  fi
fi
if [[ -z "$LAPTOP_IP" ]]; then
  LAPTOP_IP=$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K\S+' | head -1 || echo "unknown")
fi

clear
printf "${CYAN}${BOLD}"
cat <<'EOF'
  Wi-Fi ADB Pairing Wizard
  ─────────────────────────
EOF
printf "${NC}"
echo ""
printf "This pairs your phone over Wi-Fi so you never need a USB cable again.\n"
printf "Your phone must be on the ${BOLD}same network${NC} (same router, any connection).\n"
echo ""
printf "Laptop IP: ${GREEN}${BOLD}${LAPTOP_IP}${NC}\n"
echo ""

# ── Instructions ─────────────────────────────────────────────────────────────
printf "${YELLOW}Step 1 — Open Wireless Debugging on your phone:${NC}\n"
printf "  Settings → Developer Options → Wireless debugging → (toggle ON)\n"
echo ""
printf "${YELLOW}Step 2 — Pair with code:${NC}\n"
printf "  Inside 'Wireless debugging', tap ${BOLD}\"Pair device with pairing code\"${NC}\n"
printf "  You will see a 6-digit pairing code and a PORT number (e.g. 192.168.1.8:43521)\n"
echo ""
read -rp "  Enter the pairing address shown on phone (IP:PORT): " PAIR_ADDR
read -rp "  Enter the 6-digit pairing code:                     " PAIR_CODE

echo ""
info "Pairing with $PAIR_ADDR using code $PAIR_CODE..."
"$ADB_BIN" pair "$PAIR_ADDR" "$PAIR_CODE"
echo ""
ok "Paired successfully!"

# ── Now connect ───────────────────────────────────────────────────────────────
echo ""
printf "${YELLOW}Step 3 — Connect (this step uses a different port):${NC}\n"
printf "  On your phone, look at the main '${BOLD}Wireless debugging${NC}' screen.\n"
printf "  You will see: ${BOLD}IP address & Port${NC} (e.g. 192.168.1.8:37000)\n"
printf "  This is DIFFERENT from the pairing port above.\n"
echo ""
read -rp "  Enter the connection address (IP:PORT): " CONNECT_ADDR

info "Connecting to $CONNECT_ADDR..."
"$ADB_BIN" connect "$CONNECT_ADDR"
echo ""

# ── Verify ────────────────────────────────────────────────────────────────────
DEVICES=$("$ADB_BIN" devices | grep 'device$' | awk '{print $1}')
if echo "$DEVICES" | grep -q "${CONNECT_ADDR%%:*}"; then
  ok "Phone connected over Wi-Fi!"
else
  warn "Device may not be in list — check: adb devices"
fi

# ── Save config ───────────────────────────────────────────────────────────────
WIFI_CONFIG_FILE="$HOME/.scanvault-wifi-adb"
cat > "$WIFI_CONFIG_FILE" <<CONF
# ScanVault Wi-Fi ADB saved connection
# Last paired: $(date)
WIFI_ADB_ADDRESS=${CONNECT_ADDR}
CONF

echo ""
ok "Connection address saved to $WIFI_CONFIG_FILE"
echo ""
printf "${CYAN}${BOLD}You're all set!${NC}\n"
printf "\n"
printf "From now on, run the dev environment with:\n"
printf "  ${GREEN}${BOLD}./scripts/dev.sh --wifi${NC}\n"
printf "\n"
printf "The script will read ${CYAN}$WIFI_CONFIG_FILE${NC} automatically.\n"
printf "\n"
printf "${YELLOW}Note:${NC} The connection port changes on each reboot. If --wifi stops working:\n"
printf "  1. Go to Settings → Developer Options → Wireless debugging\n"
printf "  2. Note the new IP:PORT\n"
printf "  3. Run: adb connect <new-ip:port>\n"
printf "  (No need to pair again — pairing is permanent)\n"
