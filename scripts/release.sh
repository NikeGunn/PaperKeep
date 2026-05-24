#!/usr/bin/env bash
#
# release.sh — one-command release for Paperkeep.
#
# After you've MERGED your PR into main on GitHub, run this script. It will:
#   1. check you're on an up-to-date, clean main branch
#   2. ask you for the new version
#   3. bump VERSION + versionName + versionCode (in build.gradle.kts)
#   4. commit, push, create the git tag, and push the tag
#   5. the tag push triggers the GitHub Release + Play Store pipeline
#
# Usage:
#   bash scripts/release.sh              # do a real release (asks to confirm)
#   bash scripts/release.sh --dry-run    # show what it WOULD do, change nothing
#   bash scripts/release.sh 2.0.0-alpha.2            # pass version directly
#   bash scripts/release.sh 2.0.0-alpha.2 --dry-run  # both
#
# Safe to run: it confirms before any change, and --dry-run touches nothing.

set -euo pipefail

# ── pretty output helpers ────────────────────────────────────────────────────
BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; CYAN=$'\033[36m'; RESET=$'\033[0m'
info()  { echo "${CYAN}ℹ ${RESET}$*"; }
ok()    { echo "${GREEN}✓ ${RESET}$*"; }
warn()  { echo "${YELLOW}⚠ ${RESET}$*"; }
err()   { echo "${RED}✗ ${RESET}$*" >&2; }
step()  { echo; echo "${BOLD}── $* ──${RESET}"; }

# ── locate repo root (script lives in <root>/scripts/) ───────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

VERSION_FILE="$ROOT/VERSION"
GRADLE_FILE="$ROOT/android/app/build.gradle.kts"

# ── parse args ───────────────────────────────────────────────────────────────
DRY_RUN=false
NEW_VERSION=""
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) NEW_VERSION="$arg" ;;
  esac
done

if $DRY_RUN; then
  warn "DRY-RUN mode: nothing will be changed, committed, or pushed."
fi

# ── run a command, or just print it in dry-run ───────────────────────────────
run() {
  if $DRY_RUN; then
    echo "   ${YELLOW}would run:${RESET} $*"
  else
    "$@"
  fi
}

# ── compute smart next-version candidates from a current version ─────────────
# Sets the global array CANDIDATES[] (label-less version strings) and
# CANDIDATE_LABELS[] (human descriptions), in suggested order.
compute_candidates() {
  local cur="$1"
  local core pre
  if [[ "$cur" =~ ^([0-9]+\.[0-9]+\.[0-9]+)(-(.+))?$ ]]; then
    core="${BASH_REMATCH[1]}"
    pre="${BASH_REMATCH[3]}"   # e.g. alpha.1  (empty for stable)
  else
    core="$cur"; pre=""
  fi
  local major minor patch
  IFS='.' read -r major minor patch <<< "$core"

  CANDIDATES=(); CANDIDATE_LABELS=()
  add() { CANDIDATES+=("$1"); CANDIDATE_LABELS+=("$2"); }

  if [ -n "$pre" ]; then
    # We're in a pre-release like alpha.1 / beta.3
    local chan num
    if [[ "$pre" =~ ^([A-Za-z]+)\.?([0-9]*)$ ]]; then
      chan="${BASH_REMATCH[1]}"; num="${BASH_REMATCH[2]}"
    else
      chan="$pre"; num=""
    fi
    [ -z "$num" ] && num=0
    add "${core}-${chan}.$((num + 1))" "next ${chan} build (most common)"
    case "$chan" in
      alpha) add "${core}-beta.1"  "move to beta" ;;
      beta)  add "${core}-rc.1"    "move to release candidate" ;;
      rc)    : ;;
    esac
    add "${core}" "promote to STABLE ${core}"
    add "${major}.${minor}.$((patch + 1))" "skip ahead: patch ${major}.${minor}.$((patch + 1))"
    add "${major}.$((minor + 1)).0" "skip ahead: minor ${major}.$((minor + 1)).0"
  else
    # We're on a stable version
    add "${major}.${minor}.$((patch + 1))" "patch — small fix (most common)"
    add "${major}.$((minor + 1)).0" "minor — new feature"
    add "$((major + 1)).0.0" "major — big/breaking release"
    add "${major}.$((minor + 1)).0-alpha.1" "start next feature as alpha"
  fi
  # always offer a manual entry
  add "__CUSTOM__" "type a custom version…"
}

# ── arrow-key single-select menu ─────────────────────────────────────────────
# Args: prompt, then each "VALUE|LABEL" pair. Sets global MENU_CHOICE to VALUE.
arrow_menu() {
  local prompt="$1"; shift
  local items=("$@")
  local n=${#items[@]}
  local sel=0 key

  # Hide cursor; restore on exit of this function
  tput civis 2>/dev/null || true
  _menu_cleanup() { tput cnorm 2>/dev/null || true; }
  trap _menu_cleanup RETURN

  echo "${BOLD}${prompt}${RESET}"
  echo "  ${CYAN}(↑/↓ to move, Enter to select)${RESET}"
  local first=1
  while true; do
    # redraw: move cursor up over the n lines after the first paint
    if [ $first -eq 1 ]; then first=0; else tput cuu "$n" 2>/dev/null || true; fi
    local i
    for ((i = 0; i < n; i++)); do
      local val="${items[$i]%%|*}"
      local lbl="${items[$i]#*|}"
      tput el 2>/dev/null || true   # clear line
      if [ "$i" -eq "$sel" ]; then
        if [ "$val" = "__CUSTOM__" ]; then
          echo "  ${GREEN}❯ ${BOLD}${lbl}${RESET}"
        else
          echo "  ${GREEN}❯ ${BOLD}${val}${RESET}  ${CYAN}— ${lbl}${RESET}"
        fi
      else
        if [ "$val" = "__CUSTOM__" ]; then
          echo "    ${lbl}"
        else
          echo "    ${val}  ${CYAN}— ${lbl}${RESET}"
        fi
      fi
    done
    # read one key (handles escape sequences for arrows)
    IFS= read -rsn1 key
    if [ "$key" = $'\033' ]; then
      read -rsn2 -t 0.001 key2 || true
      key+="$key2"
    fi
    case "$key" in
      $'\033[A'|'k') ((sel = (sel - 1 + n) % n)) ;;   # up
      $'\033[B'|'j') ((sel = (sel + 1) % n)) ;;        # down
      '')            break ;;                          # Enter
    esac
  done
  MENU_CHOICE="${items[$sel]%%|*}"
}

# ── 1. sanity checks ─────────────────────────────────────────────────────────
step "Checking your repository state"

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$CURRENT_BRANCH" != "main" ]; then
  if $DRY_RUN; then
    warn "You are on '${CURRENT_BRANCH}', not 'main' — a real run would switch to main first. (dry-run continues)"
  else
    warn "You are on branch '${CURRENT_BRANCH}', not 'main'."
    warn "Releases are cut from main (after you merge your PR there)."
    read -r -p "Switch to main now? [y/N] " ans
    if [[ "$ans" =~ ^[Yy]$ ]]; then
      run git checkout main
      CURRENT_BRANCH="main"
    else
      err "Aborting — check out main first, then re-run."
      exit 1
    fi
  fi
else
  ok "On branch main."
fi

if [ -n "$(git status --porcelain)" ]; then
  if $DRY_RUN; then
    warn "Working tree has uncommitted changes — a real run would refuse until you commit/stash. (dry-run continues)"
  else
    err "You have uncommitted changes. Commit or stash them first:"
    git status --short
    exit 1
  fi
else
  ok "Working tree is clean."
fi

info "Fetching latest from GitHub…"
run git fetch origin --tags --quiet || true
if ! $DRY_RUN; then
  LOCAL="$(git rev-parse @)"
  REMOTE="$(git rev-parse @{u} 2>/dev/null || echo "$LOCAL")"
  if [ "$LOCAL" != "$REMOTE" ]; then
    warn "Your local main differs from origin/main."
    read -r -p "Pull latest now? [Y/n] " ans
    if [[ ! "$ans" =~ ^[Nn]$ ]]; then
      git pull --ff-only origin main
    fi
  fi
fi
ok "main is up to date."

# ── 2. figure out current + new version ──────────────────────────────────────
step "Version"

CURRENT_VERSION="$(tr -d ' \t\r\n' < "$VERSION_FILE")"
CURRENT_CODE="$(grep -oE 'versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')"
info "Current version: ${BOLD}${CURRENT_VERSION}${RESET} (versionCode ${CURRENT_CODE})"

if [ -z "$NEW_VERSION" ]; then
  if $DRY_RUN && [ ! -t 0 ]; then
    # non-interactive dry-run (e.g. automated test) — just preview candidates
    compute_candidates "$CURRENT_VERSION"
    info "Suggested next versions (arrow-menu shown in a real terminal):"
    for ((i = 0; i < ${#CANDIDATES[@]}; i++)); do
      [ "${CANDIDATES[$i]}" = "__CUSTOM__" ] && continue
      echo "    • ${CANDIDATES[$i]}  — ${CANDIDATE_LABELS[$i]}"
    done
    NEW_VERSION="${CANDIDATES[0]}"   # default to the top suggestion for preview
    info "Preview will use the top suggestion: ${BOLD}${NEW_VERSION}${RESET}"
  else
    compute_candidates "$CURRENT_VERSION"
    # build "VALUE|LABEL" items for the menu
    menu_items=()
    for ((i = 0; i < ${#CANDIDATES[@]}; i++)); do
      menu_items+=("${CANDIDATES[$i]}|${CANDIDATE_LABELS[$i]}")
    done
    echo
    arrow_menu "Pick the new version:" "${menu_items[@]}"
    if [ "$MENU_CHOICE" = "__CUSTOM__" ]; then
      read -r -p "Type the version (without 'v', e.g. 2.3.0): " NEW_VERSION
    else
      NEW_VERSION="$MENU_CHOICE"
    fi
    echo
  fi
fi

# strip a leading 'v' if the user typed one
NEW_VERSION="${NEW_VERSION#v}"

# validate: X.Y.Z optionally followed by -something
if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$ ]]; then
  err "Version '${NEW_VERSION}' is not valid. Use X.Y.Z or X.Y.Z-alpha.N (e.g. 2.0.0-alpha.2)."
  exit 1
fi
if [ "$NEW_VERSION" = "$CURRENT_VERSION" ]; then
  err "New version equals current version. Pick a higher one."
  exit 1
fi

TAG="v${NEW_VERSION}"
if git rev-parse "$TAG" >/dev/null 2>&1 || git ls-remote --tags origin "$TAG" | grep -q "$TAG"; then
  err "Tag ${TAG} already exists. Pick a different version (versionCode can't be reused)."
  exit 1
fi

NEW_CODE=$((CURRENT_CODE + 1))
NOTES_FILE="android/store/release-notes-${TAG}.txt"

# ── 3. show the plan and confirm ─────────────────────────────────────────────
step "Plan"
echo "  Version:      ${CURRENT_VERSION}  →  ${BOLD}${NEW_VERSION}${RESET}"
echo "  versionCode:  ${CURRENT_CODE}  →  ${BOLD}${NEW_CODE}${RESET}"
echo "  Git tag:      ${BOLD}${TAG}${RESET}"
echo "  Release notes: ${NOTES_FILE}"
echo
echo "  This will: edit VERSION + build.gradle.kts, commit, push to main,"
echo "  create tag ${TAG}, and push the tag (which starts the release pipeline)."
echo

if ! $DRY_RUN; then
  read -r -p "${BOLD}Proceed with the release?${RESET} [y/N] " ans
  if [[ ! "$ans" =~ ^[Yy]$ ]]; then
    err "Cancelled. Nothing was changed."
    exit 1
  fi
fi

# ── 4. release notes ─────────────────────────────────────────────────────────
step "Release notes"
if [ -f "$NOTES_FILE" ]; then
  ok "Using existing ${NOTES_FILE}"
else
  if $DRY_RUN; then
    echo "   ${YELLOW}would create:${RESET} ${NOTES_FILE} (you'd be asked for a 'what's new' line)"
  else
    echo "  Type a short 'what's new' line for this release (or press Enter for a default):"
    read -r whatsnew
    [ -z "$whatsnew" ] && whatsnew="Paperkeep ${NEW_VERSION}"
    printf 'Paperkeep %s\n\n- %s\n' "$NEW_VERSION" "$whatsnew" > "$NOTES_FILE"
    ok "Wrote ${NOTES_FILE}"
  fi
fi

# ── 5. bump the version files ────────────────────────────────────────────────
step "Bumping version files"
if $DRY_RUN; then
  echo "   ${YELLOW}would set:${RESET} VERSION = ${NEW_VERSION}"
  echo "   ${YELLOW}would set:${RESET} versionName = \"${NEW_VERSION}\""
  echo "   ${YELLOW}would set:${RESET} versionCode = ${NEW_CODE}"
else
  printf '%s' "$NEW_VERSION" > "$VERSION_FILE"
  # versionName line:  versionName = "X"
  sed -i.bak -E "s/versionName = \"[^\"]*\"/versionName = \"${NEW_VERSION}\"/" "$GRADLE_FILE"
  # versionCode line:  versionCode = N
  sed -i.bak -E "s/versionCode = [0-9]+/versionCode = ${NEW_CODE}/" "$GRADLE_FILE"
  rm -f "${GRADLE_FILE}.bak"
  ok "VERSION = ${NEW_VERSION}"
  ok "versionName = \"${NEW_VERSION}\", versionCode = ${NEW_CODE}"
  # show the diff so you can eyeball it
  git --no-pager diff -- "$VERSION_FILE" "$GRADLE_FILE" | sed 's/^/   /'
fi

# ── 5b. TEST GATE — run unit tests with the bumped version BEFORE tagging ─────
# A version bump can break tests (e.g. a test that pins the version string).
# Catching it here means we never push a broken tag/release. On failure we revert
# the version edits so your tree is left clean.
step "Test gate (unit tests with the new version)"
if $DRY_RUN; then
  echo "   ${YELLOW}would run:${RESET} (cd android && ./gradlew testDebugUnitTest)"
elif [ "${SKIP_TESTS:-false}" = "true" ]; then
  warn "SKIP_TESTS=true — skipping the test gate (not recommended)."
else
  info "Running ./gradlew testDebugUnitTest … (this is your safety net)"
  if ( cd android && ./gradlew testDebugUnitTest --console=plain -q ); then
    ok "All unit tests passed with version ${NEW_VERSION}."
  else
    err "Unit tests FAILED with the new version. NOT releasing."
    warn "Reverting the version-file edits so your tree is clean…"
    git checkout -- "$VERSION_FILE" "$GRADLE_FILE" 2>/dev/null || true
    rm -f "$NOTES_FILE" 2>/dev/null || true
    err "Fix the failing tests, then re-run ./scripts/release.sh."
    err "(To bypass in an emergency: SKIP_TESTS=true ./scripts/release.sh)"
    exit 1
  fi
fi

# ── 6. commit the version bump ───────────────────────────────────────────────
step "Committing version bump"
run git add "$VERSION_FILE" "$GRADLE_FILE" "$NOTES_FILE"
run git commit -m "chore(android): release ${NEW_VERSION}"

# Is the default branch protected? If so, a direct push needs admin bypass — we
# detect it and tell the user clearly rather than silently relying on bypass.
BRANCH_PROTECTED=false
if ! $DRY_RUN && command -v gh >/dev/null 2>&1; then
  REPO_SLUG="$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null || true)"
  if [ -n "$REPO_SLUG" ] && gh api "repos/$REPO_SLUG/branches/main/protection" >/dev/null 2>&1; then
    BRANCH_PROTECTED=true
  fi
fi

step "Pushing to main"
if $DRY_RUN; then
  echo "   ${YELLOW}would run:${RESET} git push origin main"
elif $BRANCH_PROTECTED; then
  warn "main is a PROTECTED branch. A direct push needs your admin bypass."
  warn "The cleaner path is a PR. Choose:"
  echo "   [1] Push directly to main (admin bypass) — fast"
  echo "   [2] Open a PR for the version bump — clean, but you merge it before the tag"
  read -r -p "Pick [1/2] (default 1): " pushans
  if [ "$pushans" = "2" ]; then
    REL_BRANCH="release/${TAG}"
    git switch -c "$REL_BRANCH"
    git push -u origin "$REL_BRANCH"
    gh pr create --title "chore(android): release ${NEW_VERSION}" \
      --body "Version bump for ${TAG}. Merge, then run: git tag ${TAG} && git push origin ${TAG}" >/dev/null
    ok "Opened a PR on ${REL_BRANCH}."
    warn "MERGE the PR, switch back to main & pull, THEN tag:"
    echo "   git switch main && git pull && git tag ${TAG} && git push origin ${TAG}"
    info "Stopping here so you can merge the PR first."
    exit 0
  fi
  git push origin main && ok "Pushed to main (admin bypass)."
else
  git push origin main && ok "Pushed to main."
fi

# ── 7. tag — this is what triggers the release pipeline ──────────────────────
step "Tagging ${TAG}"
run git tag "$TAG"
run git push origin "$TAG"
$DRY_RUN || ok "Tag ${TAG} pushed — release pipeline triggered."

# ── 8. confirm the pipeline actually started ─────────────────────────────────
if ! $DRY_RUN && command -v gh >/dev/null 2>&1; then
  step "Confirming the release pipeline"
  sleep 5
  RELRUN="$(gh run list --workflow 'Android Release — GitHub + Play Store' --limit 1 --json databaseId,status --jq '.[0]' 2>/dev/null || true)"
  if [ -n "$RELRUN" ]; then
    ok "Release workflow is running."
    info "Watch it:   gh run watch \$(gh run list --workflow 'Android Release — GitHub + Play Store' --limit 1 --json databaseId --jq '.[0].databaseId')"
  else
    warn "Couldn't confirm the run started — check the Actions tab."
  fi
fi

# ── done ─────────────────────────────────────────────────────────────────────
step "Done"
if $DRY_RUN; then
  warn "DRY-RUN complete. Nothing was changed. Re-run without --dry-run to release for real."
else
  ok "Released ${NEW_VERSION}! 🎉"
  echo
  info "GitHub Release (APK attached when the pipeline finishes):"
  echo "   https://github.com/NikeGunn/PaperKeep/releases/tag/${TAG}"
  echo
  info "Play Store upload happens automatically once Play secrets are set"
  info "(see docs/RELEASE_SECRETS_SETUP.md). Until then it prints 'coming soon'."
fi
