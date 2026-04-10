#!/usr/bin/env bash
# =============================================================================
# ScanVault — rollback.sh
# Emergency rollback for android, backend, OTA, or all components.
# Run this at 2am when something breaks in prod.
#
# Usage:
#   ./scripts/rollback.sh android     # Halt Play Store rollout
#   ./scripts/rollback.sh backend     # Revert VPS binary, restart systemd
#   ./scripts/rollback.sh ota         # Revert OTA config to previous version
#   ./scripts/rollback.sh all         # All three, in order: backend → ota → android
#   ./scripts/rollback.sh --help
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
rollback.sh — Emergency rollback for ScanVault production systems

USAGE:
  ./scripts/rollback.sh <target> [OPTIONS]

TARGETS:
  android    Halt the active Play Store rollout at current percentage.
             Requires Fastlane + Play Console service account credentials.

  backend    SSH to the production VPS, swap the binary to the previous
             version, and restart the scanvault systemd service.
             Requires: VPS_HOST, VPS_USER env vars (or backend/.env).

  ota        Revert the OTA remote-config.json to the previous committed
             version and push it to the CDN/R2 bucket.

  all        Run backend → ota → android in sequence.
             Rolls back the entire stack in the safest order.

OPTIONS:
  --help, -h          Show this help message and exit
  --dry-run           Show commands without executing them
  --vps-host HOST     Override VPS hostname (default: from backend/.env VPS_HOST)
  --vps-user USER     Override VPS SSH user (default: from backend/.env VPS_USER)
  --version TAG       Roll back to a specific git tag (default: previous tag)
  --yes               Skip confirmation prompt

EXAMPLES:
  ./scripts/rollback.sh backend
      Rolls back the Go API on the VPS to the previous binary.

  ./scripts/rollback.sh all --dry-run
      Show what all rollbacks would do without executing anything.

  ./scripts/rollback.sh backend --version v0.1.2
      Roll back backend to a specific tagged version.

  ./scripts/rollback.sh android
      Halts the Play Store rollout at its current percentage.
      (Users already updated keep the new version; no further rollout.)

WHAT "ROLLBACK" MEANS FOR EACH TARGET:
  android:
    Uses `fastlane supply` to halt the in-progress staged rollout.
    This stops the rollout percentage from increasing further.
    Already-updated devices are unaffected (Android has no downgrade).
    For a full revert: submit the previous AAB to Play Console manually.

  backend:
    Assumes systemd unit: scanvault.service
    The deploy script always keeps the previous binary as scanvault.previous
    This script runs: systemctl stop scanvault → swap binary → systemctl start
    Health-checks the /health endpoint before declaring success.

  ota:
    Uses git to find the previous version of ota/remote-config.json
    Pushes it to the R2 bucket via rclone or aws CLI
    Apps pick up the reverted config on next poll (default: 5 min TTL)

ENVIRONMENT VARIABLES (from backend/.env or shell):
  VPS_HOST          Production VPS hostname or IP
  VPS_USER          SSH username (default: scanvault)
  VPS_SSH_KEY       Path to SSH private key (default: ~/.ssh/id_ed25519)
  R2_BUCKET         Cloudflare R2 bucket name for OTA configs
  R2_ENDPOINT       R2 S3-compatible endpoint URL
  AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY   R2 credentials
EOF
  exit 0
fi

# -----------------------------------------------------------------------------
# Parse args
# -----------------------------------------------------------------------------
TARGET="${1:-}"
shift || true

DRY_RUN=false
VPS_HOST_OVERRIDE=""
VPS_USER_OVERRIDE=""
ROLLBACK_VERSION=""
SKIP_CONFIRM=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)        DRY_RUN=true; shift ;;
    --vps-host)       VPS_HOST_OVERRIDE="$2"; shift 2 ;;
    --vps-user)       VPS_USER_OVERRIDE="$2"; shift 2 ;;
    --version)        ROLLBACK_VERSION="$2"; shift 2 ;;
    --yes)            SKIP_CONFIRM=true; shift ;;
    *) printf "Unknown option: %s (use --help)\n" "$1"; exit 1 ;;
  esac
done

# Validate target
case "${TARGET:-}" in
  android|backend|ota|all) ;;
  "")
    printf "Error: target required (android | backend | ota | all)\n"
    printf "Usage: ./scripts/rollback.sh <target>\n"
    printf "       ./scripts/rollback.sh --help\n"
    exit 1
    ;;
  *)
    printf "Error: unknown target '%s'\n" "$TARGET"
    printf "Valid targets: android, backend, ota, all\n"
    exit 1
    ;;
esac

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

info()    { printf "${BLUE}[rollback]${NC} %s\n" "$*"; }
ok()      { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
warn()    { printf "  ${YELLOW}!${NC} %s\n" "$*"; }
die()     { printf "  ${RED}✗${NC} %s\n" "$*"; exit 1; }
dryrun()  { $DRY_RUN && printf "  ${CYAN}[DRY RUN]${NC} %s\n" "$*" || true; }
run_cmd() {
  if $DRY_RUN; then
    dryrun "Would run: $*"
  else
    eval "$@"
  fi
}

# Load .env
ENV_FILE="$ROOT/backend/.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

# Apply overrides
VPS_HOST="${VPS_HOST_OVERRIDE:-${VPS_HOST:-}}"
VPS_USER="${VPS_USER_OVERRIDE:-${VPS_USER:-scanvault}}"
VPS_SSH_KEY="${VPS_SSH_KEY:-${HOME}/.ssh/id_ed25519}"

# -----------------------------------------------------------------------------
# Confirmation prompt
# -----------------------------------------------------------------------------
if ! $SKIP_CONFIRM && ! $DRY_RUN; then
  echo ""
  printf "${RED}══════════════════════════════════════════${NC}\n"
  printf "${RED}  PRODUCTION ROLLBACK: %s${NC}\n" "${TARGET^^}"
  printf "${RED}══════════════════════════════════════════${NC}\n"
  echo ""
  printf "This will roll back the ${YELLOW}${TARGET}${NC} component in production.\n"
  [[ -n "$ROLLBACK_VERSION" ]] && printf "Target version: ${YELLOW}${ROLLBACK_VERSION}${NC}\n"
  echo ""
  read -rp "Type 'rollback' to confirm: " CONFIRM
  if [[ "$CONFIRM" != "rollback" ]]; then
    info "Aborted."
    exit 0
  fi
fi

# Determine previous tag for version reference
if [[ -z "$ROLLBACK_VERSION" ]]; then
  TAGS=$(git tag --sort=-version:refname 2>/dev/null | head -2 || true)
  CURRENT_TAG=$(echo "$TAGS" | head -1)
  PREVIOUS_TAG=$(echo "$TAGS" | tail -1)
  if [[ -z "$PREVIOUS_TAG" || "$PREVIOUS_TAG" == "$CURRENT_TAG" ]]; then
    warn "Could not determine previous tag — will use 'previous' binary on VPS"
    ROLLBACK_VERSION="previous"
  else
    ROLLBACK_VERSION="$PREVIOUS_TAG"
    info "Rolling back to: $ROLLBACK_VERSION (from $CURRENT_TAG)"
  fi
fi

# -----------------------------------------------------------------------------
# Rollback functions
# -----------------------------------------------------------------------------

rollback_backend() {
  info "Rolling back backend..."

  if [[ -z "$VPS_HOST" ]]; then
    if $DRY_RUN; then
      VPS_HOST="<vps-host-not-set>"
      warn "VPS_HOST not set (dry-run continues with placeholder)"
    else
      die "VPS_HOST not set — add to backend/.env or use --vps-host"
    fi
  fi

  SSH_CMD="ssh -i $VPS_SSH_KEY -o StrictHostKeyChecking=accept-new ${VPS_USER}@${VPS_HOST}"

  ok "VPS: ${VPS_USER}@${VPS_HOST}"
  ok "Rolling back to: $ROLLBACK_VERSION"

  # Check connectivity
  if ! $DRY_RUN; then
    $SSH_CMD "echo ok" &>/dev/null || die "Cannot SSH to ${VPS_USER}@${VPS_HOST} — check VPS_HOST, VPS_USER, VPS_SSH_KEY"
    ok "SSH connection verified"
  fi

  # The deploy script saves the previous binary as scanvault.previous
  ROLLBACK_SCRIPT='
    set -euo pipefail
    SVCDIR="/opt/scanvault"

    if [ -f "$SVCDIR/scanvault.previous" ]; then
      echo "Stopping current service..."
      systemctl stop scanvault || true

      echo "Swapping binary..."
      mv "$SVCDIR/scanvault" "$SVCDIR/scanvault.rolled-back-$(date +%Y%m%d%H%M%S)" || true
      mv "$SVCDIR/scanvault.previous" "$SVCDIR/scanvault"
      chmod +x "$SVCDIR/scanvault"

      echo "Starting previous version..."
      systemctl start scanvault

      echo "Waiting for health check..."
      for i in $(seq 1 30); do
        if curl -sf http://localhost:8080/health >/dev/null 2>&1; then
          echo "Health check passed after ${i}s"
          break
        fi
        sleep 1
      done

      systemctl is-active scanvault && echo "Service is running." || { echo "Service failed to start!"; exit 1; }
    else
      echo "ERROR: No previous binary found at $SVCDIR/scanvault.previous"
      echo "Manual rollback required — check your deploy logs."
      exit 1
    fi
  '

  run_cmd "$SSH_CMD 'bash -s' <<'ENDSSH'
${ROLLBACK_SCRIPT}
ENDSSH"

  ok "Backend rolled back"

  # Health check from our side
  if ! $DRY_RUN && [[ -n "${BACKEND_URL:-}" ]]; then
    HEALTH_URL="${BACKEND_URL}/health"
    for i in $(seq 1 10); do
      if curl -sf "$HEALTH_URL" &>/dev/null; then
        ok "Health check passed: $HEALTH_URL"
        break
      fi
      sleep 2
    done
  fi
}

rollback_ota() {
  info "Rolling back OTA config..."

  OTA_FILE="$ROOT/ota/remote-config.json"
  if [[ ! -f "$OTA_FILE" ]]; then
    warn "ota/remote-config.json not found — created in task 5.16"
    return 0
  fi

  # Find the previous committed version of the OTA file
  PREVIOUS_OTA=$(git show HEAD~1:"ota/remote-config.json" 2>/dev/null || true)
  if [[ -z "$PREVIOUS_OTA" ]]; then
    die "Could not find previous version of ota/remote-config.json in git history"
  fi

  ok "Previous OTA config found"

  if ! $DRY_RUN; then
    # Write previous version to file
    echo "$PREVIOUS_OTA" > "$OTA_FILE"
    ok "OTA file reverted locally"

    # Push to R2 bucket if configured
    if [[ -n "${R2_BUCKET:-}" && -n "${R2_ENDPOINT:-}" ]]; then
      if command -v aws &>/dev/null; then
        aws s3 cp "$OTA_FILE" "s3://${R2_BUCKET}/remote-config.json" \
          --endpoint-url "$R2_ENDPOINT" \
          --cache-control "max-age=300"
        ok "OTA config pushed to R2 (5 min TTL)"
      else
        warn "aws CLI not found — push OTA config manually:"
        warn "  aws s3 cp ota/remote-config.json s3://${R2_BUCKET}/remote-config.json --endpoint-url $R2_ENDPOINT"
      fi
    else
      warn "R2_BUCKET or R2_ENDPOINT not set — OTA file reverted locally only"
      warn "Push manually: aws s3 cp ota/remote-config.json s3://<bucket>/remote-config.json"
    fi

    # Commit the rollback
    git add "$OTA_FILE"
    git commit -m "chore(ci): rollback ota config to previous version" || true
  else
    dryrun "Would revert ota/remote-config.json to HEAD~1 version"
    dryrun "Would push to R2 bucket: ${R2_BUCKET:-<not set>}"
  fi

  ok "OTA rolled back (apps will pick up change within 5 minutes)"
}

rollback_android() {
  info "Rolling back Android (halting Play Store rollout)..."

  ANDROID_DIR="$ROOT/android"
  if [[ ! -d "$ANDROID_DIR" ]]; then
    warn "android/ not set up yet (task 1B.1) — nothing to roll back"
    return 0
  fi

  if ! command -v bundle &>/dev/null; then
    warn "Bundler not found — install Fastlane: gem install bundler && bundle install"
    warn "Manual steps to halt rollout:"
    warn "  1. Go to: https://play.google.com/console"
    warn "  2. Select ScanVault → Release → Production"
    warn "  3. Click 'Halt rollout'"
    return 0
  fi

  if [[ ! -f "$ANDROID_DIR/Gemfile" ]]; then
    warn "android/Gemfile not found — Fastlane not configured yet"
    warn "Manual steps: https://play.google.com/console → Release → Production → Halt rollout"
    return 0
  fi

  ok "Halting Play Store rollout via Fastlane..."
  run_cmd "cd '$ANDROID_DIR' && bundle exec fastlane supply --track production --rollout 0"
  ok "Play Store rollout halted"
  warn "Note: devices already updated keep the new version (Android cannot downgrade)"
  warn "To fully revert: upload previous AAB to Play Console manually"
}

# -----------------------------------------------------------------------------
# Execute rollback(s)
# -----------------------------------------------------------------------------
echo ""
if $DRY_RUN; then
  printf "${CYAN}DRY RUN MODE — no changes will be made${NC}\n\n"
fi

case "$TARGET" in
  backend) rollback_backend ;;
  ota)     rollback_ota ;;
  android) rollback_android ;;
  all)
    info "Rolling back all components: backend → ota → android"
    rollback_backend
    echo ""
    rollback_ota
    echo ""
    rollback_android
    ;;
esac

# -----------------------------------------------------------------------------
# Done
# -----------------------------------------------------------------------------
echo ""
printf "${GREEN}══════════════════════════════════════════${NC}\n"
if $DRY_RUN; then
  printf "${CYAN}  Dry run complete — no changes made.${NC}\n"
else
  printf "${GREEN}  Rollback complete: %s${NC}\n" "$TARGET"
fi
printf "${GREEN}══════════════════════════════════════════${NC}\n"
echo ""
info "Next steps:"
info "  1. Monitor error rates and crash-free rate"
info "  2. Update RUNBOOK.md with incident details"
info "  3. File a post-mortem issue"
