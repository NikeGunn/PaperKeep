#!/usr/bin/env bash
# =============================================================================
# ScanVault — dev-wifi-pair.sh
# Set up Wi-Fi ADB. Two methods depending on your situation:
#
#   METHOD A (cable plugged in) — fastest, works on all Android versions
#   METHOD B (no cable)        — Android 11+ Wireless Debugging pairing code
#
# After setup: ./scripts/dev.sh --wifi  (no cable needed)
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

ok()    { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
info()  { printf "${BLUE}[wifi-pair]${NC} %s\n" "$*"; }
warn()  { printf "  ${YELLOW}⚠${NC}  %s\n" "$*"; }
die()   { printf "\n  ${RED}✗${NC} %s\n\n" "$*"; exit 1; }
bold()  { printf "${BOLD}%s${NC}" "$*"; }

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

# ── Get laptop LAN IP (non-blocking) ─────────────────────────────────────────
LAPTOP_IP=""
if command -v cmd.exe &>/dev/null; then
  LAPTOP_IP=$(timeout 5 cmd.exe //C ipconfig 2>/dev/null \
    | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' \
    | grep '^192\.168\.' | grep -v '\.64\.' | head -1 || true)
fi
[[ -z "$LAPTOP_IP" ]] && LAPTOP_IP=$(ip route get 1.1.1.1 2>/dev/null | grep -oP 'src \K\S+' | head -1 || true)
LAPTOP_IP="${LAPTOP_IP:-unknown}"

# ── Detect if a USB device is currently connected ────────────────────────────
USB_SERIAL=""
USB_DEVICE_LIST=$("$ADB_BIN" devices 2>/dev/null | grep -v '^List' | grep 'device$' | awk '{print $1}' || true)
for s in $USB_DEVICE_LIST; do
  # Wi-Fi ADB serials look like 192.168.x.x:port — USB serials are alphanumeric
  if [[ ! "$s" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+$ ]]; then
    USB_SERIAL="$s"
    break
  fi
done

# ── Banner ────────────────────────────────────────────────────────────────────
clear
printf "${CYAN}${BOLD}"
cat <<'BANNER'
  Wi-Fi ADB Pairing Wizard
  ─────────────────────────
BANNER
printf "${NC}\n"
printf "Laptop IP : ${GREEN}${BOLD}${LAPTOP_IP}${NC}\n"

# ── Method selection ──────────────────────────────────────────────────────────
if [[ -n "$USB_SERIAL" ]]; then
  USB_MODEL=$("$ADB_BIN" -s "$USB_SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "Phone")
  printf "USB device: ${GREEN}${BOLD}${USB_MODEL}${NC} (${USB_SERIAL})\n\n"
  printf "${GREEN}${BOLD}Cable is connected — using Method A (fastest).${NC}\n"
  printf "No pairing code needed. This takes about 10 seconds.\n\n"
  METHOD="A"
else
  printf "No USB device detected.\n\n"
  printf "Choose setup method:\n"
  printf "  ${BOLD}A${NC}) I have a USB cable handy  (plug in now, fastest)\n"
  printf "  ${BOLD}B${NC}) No cable — use Wireless Debugging pairing code (Android 11+)\n\n"
  read -rp "  Method [A/B]: " METHOD_INPUT
  METHOD="${METHOD_INPUT^^}"
fi

# ══════════════════════════════════════════════════════════════════════════════
# METHOD A — Cable → switch to TCP, then unplug
# ══════════════════════════════════════════════════════════════════════════════
if [[ "$METHOD" == "A" ]]; then
  echo ""
  printf "${YELLOW}${BOLD}METHOD A — Cable Setup${NC}\n"
  echo ""

  # Ensure we have a USB device
  if [[ -z "$USB_SERIAL" ]]; then
    printf "Plug in your USB cable now.\n"
    printf "Make sure USB debugging is ON: Settings → Developer Options → USB Debugging\n"
    printf "Accept the 'Allow USB debugging?' popup on your phone.\n\n"
    info "Waiting for USB device (up to 30s)..."
    for i in $(seq 1 30); do
      USB_DEVICE_LIST=$("$ADB_BIN" devices 2>/dev/null | grep -v '^List' | grep 'device$' | awk '{print $1}' || true)
      for s in $USB_DEVICE_LIST; do
        if [[ ! "$s" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+$ ]]; then
          USB_SERIAL="$s"; break 2
        fi
      done
      printf "\r  [%ds] waiting..." "$i"
      sleep 1
    done
    echo ""
    [[ -z "$USB_SERIAL" ]] && die "No USB device found. Check cable and USB debugging setting."
    USB_MODEL=$("$ADB_BIN" -s "$USB_SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "Phone")
    ok "USB device: ${USB_MODEL} (${USB_SERIAL})"
  fi

  # Get phone's Wi-Fi IP — must use wlan0 specifically.
  # 'ip route' picks up cellular (ccmni0/rmnet) before Wi-Fi on phones with mobile data.
  PHONE_WIFI_IP=""

  # Method 1: ifconfig wlan0 (most reliable — always the Wi-Fi interface)
  PHONE_WIFI_IP=$("$ADB_BIN" -s "$USB_SERIAL" shell ifconfig wlan0 2>/dev/null \
    | grep -oE 'inet addr:[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' \
    | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)

  # Method 2: ip addr show wlan0 (newer Android)
  if [[ -z "$PHONE_WIFI_IP" ]]; then
    PHONE_WIFI_IP=$("$ADB_BIN" -s "$USB_SERIAL" shell ip addr show wlan0 2>/dev/null \
      | grep "inet " | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)
  fi

  # Method 3: ip route filtered to wlan0 only
  if [[ -z "$PHONE_WIFI_IP" ]]; then
    PHONE_WIFI_IP=$("$ADB_BIN" -s "$USB_SERIAL" shell ip route 2>/dev/null \
      | grep wlan0 | grep -oE 'src [0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' \
      | awk '{print $2}' | head -1 || true)
  fi

  if [[ -z "$PHONE_WIFI_IP" ]]; then
    echo ""
    warn "Could not auto-detect phone's Wi-Fi IP."
    printf "  On your phone: Settings → Wi-Fi → tap your network → IP address\n"
    read -rp "  Enter your phone's Wi-Fi IP: " PHONE_WIFI_IP
  else
    ok "Phone Wi-Fi IP: ${PHONE_WIFI_IP}"
  fi

  # Switch ADB to TCP mode on port 5555
  info "Switching device to TCP/IP mode on port 5555..."
  "$ADB_BIN" -s "$USB_SERIAL" tcpip 5555
  ok "TCP mode active"

  echo ""
  printf "  ${YELLOW}Unplug the USB cable now.${NC}\n"
  printf "  ${CYAN}Press Enter when cable is unplugged...${NC}"
  read -r

  # Connect over Wi-Fi
  CONNECT_ADDR="${PHONE_WIFI_IP}:5555"
  info "Connecting to ${CONNECT_ADDR}..."

  for attempt in 1 2 3; do
    if timeout 10 "$ADB_BIN" connect "$CONNECT_ADDR" 2>&1 | grep -qi "connected"; then
      break
    fi
    if [[ $attempt -lt 3 ]]; then
      warn "Attempt $attempt failed — retrying in 2s..."
      sleep 2
    else
      echo ""
      printf "${RED}Could not connect.${NC} Troubleshooting:\n"
      printf "  1. Make sure phone is on Wi-Fi (same network as this laptop)\n"
      printf "  2. Phone IP was: ${PHONE_WIFI_IP} — confirm in Settings → Wi-Fi\n"
      printf "  3. Try plugging cable back in and running this script again\n"
      die "Connection failed after 3 attempts."
    fi
  done

# ══════════════════════════════════════════════════════════════════════════════
# METHOD B — Wireless Debugging pairing code (Android 11+, no cable)
# ══════════════════════════════════════════════════════════════════════════════
elif [[ "$METHOD" == "B" ]]; then
  echo ""
  printf "${YELLOW}${BOLD}METHOD B — Wireless Debugging Pairing Code${NC}\n\n"
  printf "${YELLOW}IMPORTANT:${NC} The 'Pair device with pairing code' option only appears\n"
  printf "when ${BOLD}no USB cable is connected${NC}. Unplug any cable first.\n\n"
  printf "  ${CYAN}Press Enter when cable is unplugged and Wireless Debugging is ON...${NC}"
  read -r

  echo ""
  printf "On your phone:\n"
  printf "  Settings → Developer Options → Wireless Debugging → tap ${BOLD}\"Pair device with pairing code\"${NC}\n\n"
  printf "A popup appears with a pairing ${BOLD}IP:PORT${NC} and a ${BOLD}6-digit code${NC}.\n"
  printf "${YELLOW}You have ~60 seconds — enter these quickly.${NC}\n\n"

  read -rp "  Pairing address (IP:PORT from popup): " PAIR_ADDR
  read -rp "  6-digit pairing code:                  " PAIR_CODE

  echo ""
  info "Pairing..."
  if ! timeout 30 "$ADB_BIN" pair "$PAIR_ADDR" "$PAIR_CODE"; then
    echo ""
    printf "${RED}Pairing failed.${NC} Common causes:\n"
    printf "  • Code expired — dismiss popup, tap 'Pair device' again, re-run script\n"
    printf "  • Wrong IP:PORT — copy exactly what the popup shows\n"
    printf "  • USB cable still plugged in — unplug it, the option disappears with cable\n"
    die "Pairing failed."
  fi
  ok "Paired!"

  echo ""
  printf "Now go back to the main 'Wireless Debugging' screen.\n"
  printf "You'll see ${BOLD}IP address & Port${NC} (e.g. 192.168.1.8:37000).\n"
  printf "This is a ${BOLD}different port${NC} from the pairing popup.\n\n"

  read -rp "  Connection address (IP:PORT from main Wireless Debugging screen): " CONNECT_ADDR

  info "Connecting to ${CONNECT_ADDR}..."
  if ! timeout 15 "$ADB_BIN" connect "$CONNECT_ADDR"; then
    warn "adb connect returned non-zero — checking device list anyway..."
  fi

else
  die "Unknown method '${METHOD}'. Run the script again and enter A or B."
fi

# ── Verify connection ─────────────────────────────────────────────────────────
echo ""
DEVICE_IP="${CONNECT_ADDR%%:*}"
sleep 1
DEVICES=$("$ADB_BIN" devices 2>/dev/null | grep 'device$' | awk '{print $1}' || true)

if echo "$DEVICES" | grep -q "$DEVICE_IP"; then
  PHONE_MODEL=$("$ADB_BIN" -s "${CONNECT_ADDR}" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "Phone")
  ok "Connected over Wi-Fi: ${PHONE_MODEL} (${CONNECT_ADDR})"
else
  warn "Device not confirmed yet — it may still be connecting. Check: adb devices"
fi

# ── Save address ──────────────────────────────────────────────────────────────
WIFI_CONFIG="$HOME/.scanvault-wifi-adb"
cat > "$WIFI_CONFIG" <<CONF
# ScanVault Wi-Fi ADB — saved connection
# Created: $(date)
# Method used: ${METHOD}
# Re-run ./scripts/dev-wifi-pair.sh if --wifi stops working after a reboot.
WIFI_ADB_ADDRESS=${CONNECT_ADDR}
CONF

ok "Saved to ${WIFI_CONFIG}"

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
printf "${CYAN}${BOLD}All set!${NC}\n\n"
printf "  ${GREEN}${BOLD}./scripts/dev.sh --wifi${NC}   ← full dev loop, no cable\n\n"

if [[ "$METHOD" == "A" ]]; then
  printf "${YELLOW}After each phone reboot:${NC}\n"
  printf "  TCP mode resets on reboot. Two options:\n"
  printf "  Option 1: Plug cable in briefly →  ${CYAN}adb tcpip 5555${NC}  → unplug\n"
  printf "  Option 2: Re-run ${CYAN}./scripts/dev-wifi-pair.sh${NC}\n\n"
  printf "  Or just use the cable — ${CYAN}./scripts/dev.sh${NC} works great with USB too.\n"
else
  printf "${YELLOW}After each phone reboot:${NC}\n"
  printf "  The connection PORT changes (pairing is permanent, port is not).\n"
  printf "  Fix: Settings → Wireless debugging → note new port\n"
  printf "  Then: ${CYAN}adb connect ${DEVICE_IP}:<new-port>${NC}\n"
fi
echo ""
