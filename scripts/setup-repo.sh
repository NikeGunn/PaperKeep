#!/usr/bin/env bash
#
# setup-repo.sh — configure a GitHub repo to industry-grade standards in one shot.
#
# Idempotent: safe to re-run. It applies repo metadata, security features, branch
# protection, a label taxonomy, community health settings, and merge hygiene.
#
# Requires: gh (authenticated), git, jq.  Run from inside the repo, or pass --repo.
#
# Usage:
#   bash scripts/setup-repo.sh                          # configure current repo
#   bash scripts/setup-repo.sh --repo owner/name        # configure a specific repo
#   bash scripts/setup-repo.sh --description "..." --homepage "https://..."
#   bash scripts/setup-repo.sh --topics "android,kotlin,security"
#   bash scripts/setup-repo.sh --check-contexts "CI Status,Security Scan"
#   bash scripts/setup-repo.sh --dry-run               # print actions, change nothing
#
# Flags:
#   --repo            owner/name (default: current repo via gh)
#   --description     repo "About" description
#   --homepage        repo website URL
#   --topics          comma-separated topics
#   --branch          default branch to protect (default: auto-detected)
#   --check-contexts  comma-separated required status-check names for branch protection
#                     (default: none required — protect force-push/deletion + PR only)
#   --reviews         required approving reviews on the default branch (default: 0)
#   --no-protection   skip branch protection
#   --dry-run         show what would happen; make no changes

set -euo pipefail

BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; CYAN=$'\033[36m'; RESET=$'\033[0m'
ok(){ echo "${GREEN}✓${RESET} $*"; }
info(){ echo "${CYAN}ℹ${RESET} $*"; }
warn(){ echo "${YELLOW}⚠${RESET} $*"; }
err(){ echo "${RED}✗${RESET} $*" >&2; }
step(){ echo; echo "${BOLD}── $* ──${RESET}"; }

# ── defaults / args ──────────────────────────────────────────────────────────
REPO=""; DESCRIPTION=""; HOMEPAGE=""; TOPICS=""; BRANCH=""; CHECK_CONTEXTS=""
REVIEWS=0; DRY_RUN=false; DO_PROTECTION=true
while [ $# -gt 0 ]; do
  case "$1" in
    --repo) REPO="$2"; shift 2;;
    --description) DESCRIPTION="$2"; shift 2;;
    --homepage) HOMEPAGE="$2"; shift 2;;
    --topics) TOPICS="$2"; shift 2;;
    --branch) BRANCH="$2"; shift 2;;
    --check-contexts) CHECK_CONTEXTS="$2"; shift 2;;
    --reviews) REVIEWS="$2"; shift 2;;
    --no-protection) DO_PROTECTION=false; shift;;
    --dry-run) DRY_RUN=true; shift;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0;;
    *) err "Unknown flag: $1"; exit 1;;
  esac
done

# ── preflight ────────────────────────────────────────────────────────────────
command -v gh >/dev/null || { err "gh CLI not found. Install: https://cli.github.com"; exit 1; }
command -v jq >/dev/null || { err "jq not found. Install jq."; exit 1; }
gh auth status >/dev/null 2>&1 || { err "Not authenticated. Run: gh auth login"; exit 1; }

[ -z "$REPO" ] && REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null || true)"
[ -z "$REPO" ] && { err "No repo. Run inside a repo or pass --repo owner/name."; exit 1; }

if [ -z "$BRANCH" ]; then
  BRANCH="$(gh repo view "$REPO" --json defaultBranchRef --jq '.defaultBranchRef.name' 2>/dev/null || echo main)"
fi

$DRY_RUN && warn "DRY-RUN: no changes will be made."
info "Repo:           $REPO"
info "Default branch: $BRANCH"
[ -n "$CHECK_CONTEXTS" ] && info "Required checks: $CHECK_CONTEXTS"

api(){ # api METHOD path [jq-filter]; honors dry-run
  local method="$1" path="$2" filter="${3:-}"
  if $DRY_RUN; then echo "   ${YELLOW}would:${RESET} gh api -X $method $path"; return 0; fi
  if [ -n "$filter" ]; then gh api -X "$method" "$path" --jq "$filter"; else gh api -X "$method" "$path"; fi
}
apiin(){ # apiin METHOD path < json-on-stdin
  local method="$1" path="$2"
  if $DRY_RUN; then echo "   ${YELLOW}would:${RESET} gh api -X $method $path (with JSON body)"; cat >/dev/null; return 0; fi
  gh api -X "$method" "$path" --input -
}
ghrun(){ if $DRY_RUN; then echo "   ${YELLOW}would:${RESET} $*"; else "$@"; fi; }

# ── 1. Repo metadata: description, homepage, topics ──────────────────────────
step "1/8 Repo metadata (About, homepage, topics)"
EDIT_ARGS=()
[ -n "$DESCRIPTION" ] && EDIT_ARGS+=(--description "$DESCRIPTION")
[ -n "$HOMEPAGE" ]    && EDIT_ARGS+=(--homepage "$HOMEPAGE")
if [ ${#EDIT_ARGS[@]} -gt 0 ]; then
  ghrun gh repo edit "$REPO" "${EDIT_ARGS[@]}" && ok "Description/homepage set"
else
  info "No --description/--homepage given; leaving as-is."
fi
if [ -n "$TOPICS" ]; then
  IFS=',' read -ra T <<< "$TOPICS"; ARGS=(); for t in "${T[@]}"; do ARGS+=(--add-topic "$(echo "$t"|xargs)"); done
  ghrun gh repo edit "$REPO" "${ARGS[@]}" && ok "Topics set: $TOPICS"
fi

# ── 2. Repo features & merge hygiene ─────────────────────────────────────────
step "2/8 Features & merge hygiene"
# Enable Issues/Projects/Wiki off (wiki off is common for code-only repos), squash-only
# merges with auto-delete of head branches + auto-merge. Clean, linear history.
api PATCH "repos/$REPO" >/dev/null <<'JSON' 2>/dev/null || true
JSON
apiin PATCH "repos/$REPO" >/dev/null <<JSON
{
  "has_issues": true,
  "has_projects": true,
  "has_wiki": false,
  "allow_squash_merge": true,
  "allow_merge_commit": false,
  "allow_rebase_merge": false,
  "allow_auto_merge": true,
  "delete_branch_on_merge": true,
  "allow_update_branch": true,
  "squash_merge_commit_title": "PR_TITLE",
  "squash_merge_commit_message": "PR_BODY"
}
JSON
ok "Squash-only merges, auto-delete branches, auto-merge, issues+projects on, wiki off"

# ── 3. Security features ─────────────────────────────────────────────────────
step "3/8 Security features"
# Vulnerability alerts + automated Dependabot security fixes.
api PUT "repos/$REPO/vulnerability-alerts" >/dev/null 2>&1 && ok "Dependabot vulnerability alerts enabled" || warn "Could not enable vulnerability alerts (may need extra scope)"
api PUT "repos/$REPO/automated-security-fixes" >/dev/null 2>&1 && ok "Automated security fixes enabled" || warn "Could not enable automated security fixes"
# Secret scanning + push protection (GHAS — free on public repos).
apiin PATCH "repos/$REPO" >/dev/null <<'JSON' 2>/dev/null && ok "Secret scanning + push protection enabled" || warn "Secret scanning unavailable (private repo without GHAS)"
{
  "security_and_analysis": {
    "secret_scanning": { "status": "enabled" },
    "secret_scanning_push_protection": { "status": "enabled" }
  }
}
JSON

# ── 4. Branch protection on default branch ───────────────────────────────────
if $DO_PROTECTION; then
  step "4/8 Branch protection on '$BRANCH'"
  CONTEXTS_JSON="[]"
  if [ -n "$CHECK_CONTEXTS" ]; then
    CONTEXTS_JSON="$(jq -cn --arg s "$CHECK_CONTEXTS" '$s|split(",")|map(gsub("^\\s+|\\s+$";""))')"
  fi
  apiin PUT "repos/$REPO/branches/$BRANCH/protection" >/dev/null <<JSON && ok "Branch protection applied (force-push/deletion blocked, PR required, ${REVIEWS} review(s))" || warn "Branch protection failed (does '$BRANCH' exist / any commits pushed?)"
{
  "required_status_checks": { "strict": true, "contexts": ${CONTEXTS_JSON} },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": ${REVIEWS},
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_linear_history": true,
  "required_conversation_resolution": true
}
JSON
else
  step "4/8 Branch protection — skipped (--no-protection)"
fi

# ── 5. Label taxonomy ────────────────────────────────────────────────────────
step "5/8 Label taxonomy"
# name|color|description  — a clean, conventional set used by mature OSS projects.
LABELS=(
  "type: bug|d73a4a|Something isn't working"
  "type: feature|0e8a16|New feature or request"
  "type: docs|0075ca|Documentation only"
  "type: refactor|fbca04|Code change that neither fixes a bug nor adds a feature"
  "type: test|c5def5|Adding or fixing tests"
  "type: chore|ededed|Build, CI, deps, tooling"
  "type: security|b60205|Security-relevant issue or fix"
  "priority: high|e11d21|Urgent — address ASAP"
  "priority: medium|fbca04|Normal priority"
  "priority: low|0e8a16|Nice to have"
  "good first issue|7057ff|Good for newcomers"
  "help wanted|008672|Extra attention is wanted"
  "status: blocked|000000|Blocked on something else"
  "status: in progress|1d76db|Being worked on"
  "wontfix|ffffff|This will not be worked on"
  "duplicate|cfd3d7|This already exists"
)
for entry in "${LABELS[@]}"; do
  IFS='|' read -r name color desc <<< "$entry"
  if $DRY_RUN; then echo "   ${YELLOW}would:${RESET} label '$name'"; continue; fi
  gh label create "$name" --color "$color" --description "$desc" --repo "$REPO" --force >/dev/null 2>&1 \
    && echo "   • $name" || true
done
$DRY_RUN || ok "Labels synced (${#LABELS[@]} labels)"

# ── 6. Community health files (only if missing — never overwrite) ────────────
step "6/8 Community health files"
ROOT="$(git rev-parse --show-toplevel 2>/dev/null || echo .)"
mkfile(){ # mkfile relpath  (content on stdin) — creates only if absent
  local rel="$1" full="$ROOT/$1"
  if [ -f "$full" ]; then info "exists, keeping: $rel"; cat >/dev/null; return; fi
  if $DRY_RUN; then echo "   ${YELLOW}would create:${RESET} $rel"; cat >/dev/null; return; fi
  mkdir -p "$(dirname "$full")"; cat > "$full"; echo "   + $rel"
}

mkfile ".github/CODEOWNERS" <<EOF
# Default owner for everything. Update as your team grows.
*       @${REPO%%/*}
EOF

mkfile ".github/pull_request_template.md" <<'EOF'
## What & why
<!-- What does this change and why? Link issues: Closes #123 -->

## How tested
<!-- Commands run, manual steps, screenshots -->

## Checklist
- [ ] Tests added/updated and passing
- [ ] No secrets or credentials committed
- [ ] Docs updated if behavior changed
EOF

mkfile ".github/ISSUE_TEMPLATE/bug_report.yml" <<'EOF'
name: Bug report
description: Report something that isn't working
labels: ["type: bug"]
body:
  - type: textarea
    id: what-happened
    attributes:
      label: What happened?
      description: A clear description of the bug.
    validations: { required: true }
  - type: textarea
    id: repro
    attributes:
      label: Steps to reproduce
      placeholder: |
        1. ...
        2. ...
    validations: { required: true }
  - type: textarea
    id: expected
    attributes:
      label: Expected behavior
  - type: input
    id: version
    attributes:
      label: Version / environment
EOF

mkfile ".github/ISSUE_TEMPLATE/feature_request.yml" <<'EOF'
name: Feature request
description: Suggest an idea
labels: ["type: feature"]
body:
  - type: textarea
    id: problem
    attributes:
      label: Problem
      description: What problem does this solve?
    validations: { required: true }
  - type: textarea
    id: solution
    attributes:
      label: Proposed solution
EOF

mkfile ".github/ISSUE_TEMPLATE/config.yml" <<'EOF'
blank_issues_enabled: false
EOF

mkfile ".github/dependabot.yml" <<'EOF'
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule: { interval: "weekly" }
    labels: ["type: chore"]
EOF

# Only suggest these if truly absent — these usually exist already.
[ -f "$ROOT/CONTRIBUTING.md" ] || mkfile "CONTRIBUTING.md" <<EOF
# Contributing

Thanks for your interest!

1. Fork and create a branch: \`git checkout -b feat/your-change\`
2. Make your change with tests.
3. Open a PR. CI must pass and at least ${REVIEWS} review(s) where required.

Please follow the existing commit convention and code style. Be kind in reviews.
EOF

# ── 7. Push any new community files via a PR-free direct commit if safe ───────
step "7/8 Commit new community files"
if $DRY_RUN; then
  info "dry-run: skipping commit"
elif [ -n "$(git -C "$ROOT" status --porcelain 2>/dev/null)" ]; then
  warn "New files created locally. Review and commit them on a branch + PR:"
  git -C "$ROOT" status --short | sed 's/^/   /'
  echo "   ${CYAN}(not auto-committing — your default branch is protected)${RESET}"
else
  info "No new files to commit (all health files already present)."
fi

# ── 8. Summary ───────────────────────────────────────────────────────────────
step "8/8 Done"
if $DRY_RUN; then
  warn "Dry-run complete — nothing changed."
else
  ok "Repo '$REPO' configured to industry-grade standards."
  echo
  info "Verify: gh repo view $REPO --web"
  info "Branch protection: gh api repos/$REPO/branches/$BRANCH/protection --jq '.required_status_checks.contexts'"
  echo
  warn "Manual one-time items GitHub doesn't expose to the API:"
  echo "   • Add repo social preview image (Settings → General → Social preview)"
  echo "   • Enable Discussions if wanted (Settings → General → Features)"
  echo "   • Add a LICENSE via 'gh repo edit' is not supported — commit a LICENSE file"
fi
