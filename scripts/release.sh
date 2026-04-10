#!/usr/bin/env bash
# =============================================================================
# ScanVault — release.sh
# One-command release: bumps VERSION, generates changelog, tags, pushes.
# GitHub Actions takes over from the tag push.
#
# Usage:
#   ./scripts/release.sh patch    # 0.1.0 → 0.1.1
#   ./scripts/release.sh minor    # 0.1.0 → 0.2.0
#   ./scripts/release.sh major    # 0.1.0 → 1.0.0
#   ./scripts/release.sh --help
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
release.sh — Bump version, generate changelog, tag, and push

USAGE:
  ./scripts/release.sh <bump-type> [OPTIONS]

BUMP TYPES:
  patch     Increment patch version:  0.1.0 → 0.1.1
  minor     Increment minor version:  0.1.0 → 0.2.0
  major     Increment major version:  0.1.0 → 1.0.0

OPTIONS:
  --help, -h      Show this help message and exit
  --dry-run       Show what would happen without making any changes
  --no-push       Create commit and tag locally but don't push
  --no-tests      Skip test run (not recommended)
  --force         Skip confirmation prompt (for CI use)

WHAT IT DOES:
  1. Verifies working tree is clean and on main branch
  2. Pulls latest main from origin
  3. Runs make test-all (unless --no-tests)
  4. Reads current version from VERSION file
  5. Calculates new version according to bump-type
  6. Asks for confirmation (shows current → new version)
  7. Writes new version to VERSION (no trailing newline)
  8. Generates changelog from conventional commits since last tag
  9. Appends new section to docs/CHANGELOG.md
  10. Commits: "chore(deps): release v<NEW_VERSION>"
  11. Tags: v<NEW_VERSION>
  12. Pushes commit + tag to origin main
  13. GitHub Actions picks up the tag and builds/deploys

CONVENTIONAL COMMIT → CHANGELOG MAPPING:
  feat:       → Added
  fix:        → Fixed
  perf:       → Performance
  refactor:   → Changed
  docs:       → Documentation
  chore/ci:   → (omitted from changelog)
  feat!:      → Breaking Changes

EXAMPLES:
  ./scripts/release.sh patch
      Bump 0.1.0 → 0.1.1, asks for confirmation.

  ./scripts/release.sh minor --dry-run
      Show what a minor bump would do without changing anything.

  ./scripts/release.sh patch --no-push
      Create the commit and tag locally for inspection before pushing.

NOTE:
  VERSION file lives at the repo root. It is the single source of truth.
  All other systems (Gradle, Fastlane, OTA manifest) read from it.
EOF
  exit 0
fi

# -----------------------------------------------------------------------------
# Parse args
# -----------------------------------------------------------------------------
BUMP_TYPE="${1:-}"
DRY_RUN=false
NO_PUSH=false
NO_TESTS=false
FORCE=false

shift || true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)   DRY_RUN=true;  shift ;;
    --no-push)   NO_PUSH=true;  shift ;;
    --no-tests)  NO_TESTS=true; shift ;;
    --force)     FORCE=true;    shift ;;
    *) printf "Unknown option: %s (use --help)\n" "$1"; exit 1 ;;
  esac
done

if [[ -z "$BUMP_TYPE" ]]; then
  printf "Error: bump type required (patch | minor | major)\n"
  printf "Usage: ./scripts/release.sh <patch|minor|major>\n"
  printf "       ./scripts/release.sh --help\n"
  exit 1
fi

case "$BUMP_TYPE" in
  patch|minor|major) ;;
  *) printf "Error: invalid bump type '%s' — must be patch, minor, or major\n" "$BUMP_TYPE"; exit 1 ;;
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
VERSION_FILE="$ROOT/VERSION"
CHANGELOG_FILE="$ROOT/docs/CHANGELOG.md"

info()  { printf "${BLUE}[release]${NC} %s\n" "$*"; }
ok()    { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
warn()  { printf "  ${YELLOW}!${NC} %s\n" "$*"; }
die()   { printf "  ${RED}✗${NC} %s\n" "$*"; exit 1; }
dryrun(){ $DRY_RUN && printf "  ${CYAN}[DRY RUN]${NC} %s\n" "$*" || true; }

# -----------------------------------------------------------------------------
# Step 1: Verify clean working tree on main
# -----------------------------------------------------------------------------
info "Verifying repository state..."
cd "$ROOT"

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$CURRENT_BRANCH" != "main" ]]; then
  if $DRY_RUN; then
    warn "Not on main branch: '$CURRENT_BRANCH' (ignored in dry-run mode)"
  else
    die "Must be on main branch (currently on '$CURRENT_BRANCH')"
  fi
else
  ok "On main branch"
fi

DIRTY=$(git status --porcelain 2>/dev/null || true)
if [[ -n "$DIRTY" ]]; then
  if $DRY_RUN; then
    warn "Working tree is dirty (ignored in dry-run mode)"
  else
    die "Working tree is dirty — commit or stash changes first"
  fi
else
  ok "Working tree clean"
fi

# -----------------------------------------------------------------------------
# Step 2: Pull latest
# -----------------------------------------------------------------------------
info "Pulling latest main..."
if ! $DRY_RUN; then
  git pull --ff-only origin main 2>&1 | sed 's/^/  /' || die "Pull failed — resolve conflicts first"
fi
$DRY_RUN && dryrun "Would run: git pull --ff-only origin main"
ok "Up to date with origin/main"

# -----------------------------------------------------------------------------
# Step 3: Run tests
# -----------------------------------------------------------------------------
if ! $NO_TESTS; then
  info "Running tests..."
  if ! $DRY_RUN; then
    if [[ -f "$ROOT/Makefile" ]]; then
      make -C "$ROOT" test-all || die "Tests failed — fix before releasing"
    else
      warn "Makefile not found — skipping tests (set up backend/frontend first)"
    fi
  fi
  $DRY_RUN && dryrun "Would run: make test-all"
  ok "Tests passed"
else
  warn "Skipping tests (--no-tests) — not recommended for production releases"
fi

# -----------------------------------------------------------------------------
# Step 4: Read and bump version
# -----------------------------------------------------------------------------
if [[ ! -f "$VERSION_FILE" ]]; then
  die "VERSION file not found at $VERSION_FILE"
fi

CURRENT_VERSION=$(cat "$VERSION_FILE")
# Validate semver format
if ! echo "$CURRENT_VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  die "VERSION file contains invalid semver: '$CURRENT_VERSION'"
fi

MAJOR=$(echo "$CURRENT_VERSION" | cut -d. -f1)
MINOR=$(echo "$CURRENT_VERSION" | cut -d. -f2)
PATCH=$(echo "$CURRENT_VERSION" | cut -d. -f3)

case "$BUMP_TYPE" in
  patch) NEW_VERSION="${MAJOR}.${MINOR}.$((PATCH + 1))" ;;
  minor) NEW_VERSION="${MAJOR}.$((MINOR + 1)).0" ;;
  major) NEW_VERSION="$((MAJOR + 1)).0.0" ;;
esac

ok "Current version: $CURRENT_VERSION"
ok "New version:     $NEW_VERSION ($BUMP_TYPE bump)"

# -----------------------------------------------------------------------------
# Step 5: Confirmation
# -----------------------------------------------------------------------------
if ! $FORCE && ! $DRY_RUN; then
  echo ""
  printf "${YELLOW}About to release:${NC}\n"
  printf "  ${CURRENT_VERSION} → ${GREEN}${NEW_VERSION}${NC}\n"
  printf "  Tag:    v${NEW_VERSION}\n"
  printf "  Branch: main\n"
  echo ""
  read -rp "Proceed? [y/N] " CONFIRM
  case "$CONFIRM" in
    y|Y|yes|YES) ok "Confirmed" ;;
    *) info "Aborted."; exit 0 ;;
  esac
fi
$DRY_RUN && dryrun "Would prompt for confirmation: $CURRENT_VERSION → $NEW_VERSION"

# -----------------------------------------------------------------------------
# Step 6: Generate changelog
# -----------------------------------------------------------------------------
info "Generating changelog..."

# Get commits since last tag (or all commits if no tags)
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [[ -n "$LAST_TAG" ]]; then
  COMMIT_RANGE="${LAST_TAG}..HEAD"
  info "  Changelog from $LAST_TAG to HEAD"
else
  COMMIT_RANGE="HEAD"
  info "  No previous tags — using all commits"
fi

# Parse conventional commits into sections
FEAT_ENTRIES=""
FIX_ENTRIES=""
PERF_ENTRIES=""
REFACTOR_ENTRIES=""
BREAKING_ENTRIES=""

while IFS= read -r line; do
  HASH=$(echo "$line" | cut -d' ' -f1)
  MSG=$(echo "$line" | cut -d' ' -f2-)

  case "$MSG" in
    "feat!"*|*"BREAKING CHANGE"*)
      SCOPE=$(echo "$MSG" | grep -oE '\([^)]+\)' || echo "")
      SUBJECT=$(echo "$MSG" | sed 's/^feat![^:]*: //' | sed 's/^[^:]*BREAKING CHANGE: //')
      BREAKING_ENTRIES="${BREAKING_ENTRIES}\n- ${SUBJECT} ${SCOPE} (${HASH})"
      ;;
    feat*)
      SCOPE=$(echo "$MSG" | grep -oE '\([^)]+\)' || echo "")
      SUBJECT=$(echo "$MSG" | sed 's/^feat[^:]*: //')
      FEAT_ENTRIES="${FEAT_ENTRIES}\n- ${SUBJECT} ${SCOPE} (${HASH})"
      ;;
    fix*)
      SCOPE=$(echo "$MSG" | grep -oE '\([^)]+\)' || echo "")
      SUBJECT=$(echo "$MSG" | sed 's/^fix[^:]*: //')
      FIX_ENTRIES="${FIX_ENTRIES}\n- ${SUBJECT} ${SCOPE} (${HASH})"
      ;;
    perf*)
      SCOPE=$(echo "$MSG" | grep -oE '\([^)]+\)' || echo "")
      SUBJECT=$(echo "$MSG" | sed 's/^perf[^:]*: //')
      PERF_ENTRIES="${PERF_ENTRIES}\n- ${SUBJECT} ${SCOPE} (${HASH})"
      ;;
    refactor*)
      SCOPE=$(echo "$MSG" | grep -oE '\([^)]+\)' || echo "")
      SUBJECT=$(echo "$MSG" | sed 's/^refactor[^:]*: //')
      REFACTOR_ENTRIES="${REFACTOR_ENTRIES}\n- ${SUBJECT} ${SCOPE} (${HASH})"
      ;;
  esac
done < <(git log "$COMMIT_RANGE" --oneline --no-merges 2>/dev/null || true)

# Build changelog entry
RELEASE_DATE=$(date -u +%Y-%m-%d)
CHANGELOG_ENTRY="## [${NEW_VERSION}] - ${RELEASE_DATE}\n"

[[ -n "$BREAKING_ENTRIES" ]] && CHANGELOG_ENTRY+="### Breaking Changes\n${BREAKING_ENTRIES}\n\n"
[[ -n "$FEAT_ENTRIES" ]]     && CHANGELOG_ENTRY+="### Added\n${FEAT_ENTRIES}\n\n"
[[ -n "$FIX_ENTRIES" ]]      && CHANGELOG_ENTRY+="### Fixed\n${FIX_ENTRIES}\n\n"
[[ -n "$PERF_ENTRIES" ]]     && CHANGELOG_ENTRY+="### Performance\n${PERF_ENTRIES}\n\n"
[[ -n "$REFACTOR_ENTRIES" ]] && CHANGELOG_ENTRY+="### Changed\n${REFACTOR_ENTRIES}\n\n"

if [[ -z "$BREAKING_ENTRIES$FEAT_ENTRIES$FIX_ENTRIES$PERF_ENTRIES$REFACTOR_ENTRIES" ]]; then
  CHANGELOG_ENTRY+="### Other\n- Internal changes and maintenance\n\n"
fi

ok "Changelog entry prepared"

# -----------------------------------------------------------------------------
# Step 7: Write VERSION + update CHANGELOG
# -----------------------------------------------------------------------------
if ! $DRY_RUN; then
  # Write new version (no trailing newline — single source of truth)
  printf '%s' "$NEW_VERSION" > "$VERSION_FILE"
  ok "VERSION updated: $CURRENT_VERSION → $NEW_VERSION"

  # Prepend new changelog section
  if [[ -f "$CHANGELOG_FILE" ]]; then
    EXISTING=$(cat "$CHANGELOG_FILE")
    # Insert after the first line (the # Changelog header)
    HEADER=$(head -1 "$CHANGELOG_FILE")
    REST=$(tail -n +2 "$CHANGELOG_FILE")
    printf "%s\n\n%b\n%s" "$HEADER" "$CHANGELOG_ENTRY" "$REST" > "$CHANGELOG_FILE"
  else
    # Create changelog file
    printf "# Changelog\n\n%b" "$CHANGELOG_ENTRY" > "$CHANGELOG_FILE"
  fi
  ok "docs/CHANGELOG.md updated"
else
  dryrun "Would write '$NEW_VERSION' → VERSION"
  dryrun "Would prepend to docs/CHANGELOG.md:"
  printf "%b\n" "$CHANGELOG_ENTRY" | head -20 | sed 's/^/    /'
fi

# -----------------------------------------------------------------------------
# Step 8: Commit
# -----------------------------------------------------------------------------
COMMIT_MSG="chore(deps): release v${NEW_VERSION}"

if ! $DRY_RUN; then
  git add "$VERSION_FILE" "$CHANGELOG_FILE"
  git commit -m "$COMMIT_MSG"
  ok "Committed: $COMMIT_MSG"
else
  dryrun "Would commit: $COMMIT_MSG"
fi

# -----------------------------------------------------------------------------
# Step 9: Tag
# -----------------------------------------------------------------------------
TAG="v${NEW_VERSION}"

if ! $DRY_RUN; then
  git tag -a "$TAG" -m "Release $TAG"
  ok "Tagged: $TAG"
else
  dryrun "Would tag: $TAG"
fi

# -----------------------------------------------------------------------------
# Step 10: Push
# -----------------------------------------------------------------------------
if ! $NO_PUSH; then
  if ! $DRY_RUN; then
    info "Pushing to origin..."
    git push origin main
    git push origin "$TAG"
    ok "Pushed main + tag $TAG to origin"
  else
    dryrun "Would push: git push origin main && git push origin $TAG"
  fi
else
  warn "Skipped push (--no-push) — run when ready:"
  warn "  git push origin main && git push origin $TAG"
fi

# -----------------------------------------------------------------------------
# Done
# -----------------------------------------------------------------------------
echo ""
printf "${GREEN}══════════════════════════════════════════${NC}\n"
printf "${GREEN}  Released: v${CURRENT_VERSION} → v${NEW_VERSION}${NC}\n"
if ! $NO_PUSH && ! $DRY_RUN; then
  printf "${GREEN}  GitHub Actions will now build and deploy.${NC}\n"
fi
printf "${GREEN}══════════════════════════════════════════${NC}\n"
