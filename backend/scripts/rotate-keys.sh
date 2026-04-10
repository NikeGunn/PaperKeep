#!/usr/bin/env bash
# =============================================================================
# ScanVault — rotate-keys.sh
# Rotates PASETO_KEY and IP_HASH_KEY on the production VPS.
#
# IMPORTANT: Rotating PASETO_KEY invalidates ALL active sessions.
# All users will be logged out and must log in again.
# Schedule this during off-peak hours and notify users in advance.
#
# Usage:
#   ./backend/scripts/rotate-keys.sh           # Interactive
#   ./backend/scripts/rotate-keys.sh --dry-run # Show what would change
# =============================================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
fi

info() { printf "${BLUE}[rotate-keys]${NC} %s\n" "$*"; }
ok()   { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
warn() { printf "  ${YELLOW}!${NC} %s\n" "$*"; }
die()  { printf "  ${RED}✗${NC} %s\n" "$*"; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT/backend/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  die "backend/.env not found — run this on the VPS or point to the right .env"
fi

info "Generating new PASETO_KEY..."
NEW_PASETO=$(openssl rand -base64 32)
ok "New PASETO_KEY generated (${#NEW_PASETO} chars)"

info "Generating new IP_HASH_KEY..."
NEW_IP_HASH=$(openssl rand -base64 32)
ok "New IP_HASH_KEY generated"

if $DRY_RUN; then
  printf "\n${YELLOW}[DRY RUN] Would update:${NC}\n"
  printf "  PASETO_KEY  → %s\n" "${NEW_PASETO:0:8}...(redacted)"
  printf "  IP_HASH_KEY → %s\n" "${NEW_IP_HASH:0:8}...(redacted)"
  printf "\n${YELLOW}[DRY RUN] Would restart: systemctl restart scanvault${NC}\n"
  exit 0
fi

echo ""
printf "${RED}WARNING: Rotating PASETO_KEY will log out ALL users on ALL devices.${NC}\n"
printf "Run this during a maintenance window and notify users first.\n\n"
read -rp "Type 'rotate' to confirm: " CONFIRM
if [[ "$CONFIRM" != "rotate" ]]; then
  info "Aborted."
  exit 0
fi

# Update the .env file
sed -i "s|^PASETO_KEY=.*|PASETO_KEY=${NEW_PASETO}|" "$ENV_FILE"
ok "PASETO_KEY updated in $ENV_FILE"

sed -i "s|^IP_HASH_KEY=.*|IP_HASH_KEY=${NEW_IP_HASH}|" "$ENV_FILE"
ok "IP_HASH_KEY updated in $ENV_FILE"

# Restart the service to pick up new keys
if command -v systemctl &>/dev/null; then
  info "Restarting scanvault.service..."
  systemctl restart scanvault
  sleep 2
  systemctl is-active scanvault && ok "Service restarted successfully" || die "Service failed to restart"
else
  warn "systemctl not found — restart the service manually to apply new keys"
fi

echo ""
printf "${GREEN}Key rotation complete.${NC}\n"
printf "All existing sessions are now invalid.\n"
printf "Monitor error rates for the next 10 minutes.\n"
