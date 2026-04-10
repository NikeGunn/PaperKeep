#!/usr/bin/env bash
# =============================================================================
# ScanVault — Phase 0 validation tests
# Verifies that all DevOps skeleton tasks 0.1–0.6 are correctly implemented.
# Usage: bash scripts/test-phase0.sh
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PASS=0
FAIL=0
FAILURES=()

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

assert_pass() {
  local desc="$1"
  PASS=$((PASS + 1))
  printf "  ${GREEN}✓${NC} %s\n" "$desc"
}

assert_fail() {
  local desc="$1"
  FAIL=$((FAIL + 1))
  FAILURES+=("$desc")
  printf "  ${RED}✗${NC} %s\n" "$desc"
}

check() {
  local desc="$1"
  local condition="$2"
  if eval "$condition"; then
    assert_pass "$desc"
  else
    assert_fail "$desc"
  fi
}

# =============================================================================
# TASK 0.1 — Directory structure
# =============================================================================
echo ""
printf "${YELLOW}Task 0.1 — Directory structure${NC}\n"

check "android/ directory exists"     "[ -d '$ROOT/android' ]"
check "backend/ directory exists"     "[ -d '$ROOT/backend' ]"
check "intelligence/ directory exists" "[ -d '$ROOT/intelligence' ]"
check "ota/ directory exists"         "[ -d '$ROOT/ota' ]"
check "scripts/ directory exists"     "[ -d '$ROOT/scripts' ]"
check "docs/ directory exists"        "[ -d '$ROOT/docs' ]"

# =============================================================================
# TASK 0.2 — VERSION file
# =============================================================================
echo ""
printf "${YELLOW}Task 0.2 — VERSION file${NC}\n"

check "VERSION file exists"           "[ -f '$ROOT/VERSION' ]"
check "VERSION contains '0.1.0'"      "[ \"\$(cat '$ROOT/VERSION')\" = '0.1.0' ]"
check "VERSION has no trailing newline" "[ \"\$(wc -c < '$ROOT/VERSION')\" = '5' ]"

# =============================================================================
# TASK 0.3 — .gitignore coverage
# =============================================================================
echo ""
printf "${YELLOW}Task 0.3 — .gitignore coverage${NC}\n"

check ".gitignore exists"             "[ -f '$ROOT/.gitignore' ]"

# Android patterns
check ".gitignore: android/build/ excluded"   "grep -q 'android/build/' '$ROOT/.gitignore'"
check ".gitignore: .gradle/ excluded"         "grep -q '\.gradle/' '$ROOT/.gitignore'"
check ".gitignore: *.apk excluded"            "grep -q '\*.apk' '$ROOT/.gitignore'"
check ".gitignore: *.aab excluded"            "grep -q '\*.aab' '$ROOT/.gitignore'"
check ".gitignore: local.properties excluded" "grep -q 'local\.properties' '$ROOT/.gitignore'"

# Go patterns
check ".gitignore: backend/bin/ excluded"     "grep -q 'backend/bin/' '$ROOT/.gitignore'"
check ".gitignore: vendor/ excluded"          "grep -q 'vendor/' '$ROOT/.gitignore'"

# Python patterns
check ".gitignore: __pycache__/ excluded"     "grep -q '__pycache__/' '$ROOT/.gitignore'"
check ".gitignore: .venv/ excluded"           "grep -q '\.venv/' '$ROOT/.gitignore'"
check ".gitignore: *.pyc excluded"            "grep -q '\*\.pyc' '$ROOT/.gitignore' || grep -q '\*\.py\[cod\]' '$ROOT/.gitignore'"
check ".gitignore: models/*.bin excluded"     "grep -q 'models/\*\.bin' '$ROOT/.gitignore'"

# IDE patterns
check ".gitignore: .idea/ excluded"           "grep -q '\.idea/' '$ROOT/.gitignore'"
check ".gitignore: .vscode/ excluded"         "grep -q '\.vscode/' '$ROOT/.gitignore'"
check ".gitignore: *.iml excluded"            "grep -q '\*\.iml' '$ROOT/.gitignore'"

# Secrets patterns
check ".gitignore: .env excluded"             "grep -q '^\.env' '$ROOT/.gitignore'"
check ".gitignore: *.jks excluded"            "grep -q '\*\.jks' '$ROOT/.gitignore'"
check ".gitignore: *.keystore excluded"       "grep -q '\*\.keystore' '$ROOT/.gitignore'"
check ".gitignore: play-service-account excluded" "grep -q 'play-service-account' '$ROOT/.gitignore'"

# OS patterns
check ".gitignore: .DS_Store excluded"        "grep -q '\.DS_Store' '$ROOT/.gitignore'"
check ".gitignore: Thumbs.db excluded"        "grep -q 'Thumbs\.db' '$ROOT/.gitignore'"

# =============================================================================
# TASK 0.4 — Makefile targets
# =============================================================================
echo ""
printf "${YELLOW}Task 0.4 — Makefile targets${NC}\n"

check "Makefile exists"               "[ -f '$ROOT/Makefile' ]"
check "Makefile: 'android' target"    "grep -q '^android:' '$ROOT/Makefile'"
check "Makefile: 'backend' target"    "grep -q '^backend:' '$ROOT/Makefile'"
check "Makefile: 'intelligence' target" "grep -q '^intelligence:' '$ROOT/Makefile'"
check "Makefile: 'test-all' target"   "grep -q '^test-all:' '$ROOT/Makefile'"
check "Makefile: 'lint-all' target"   "grep -q '^lint-all:' '$ROOT/Makefile'"
check "Makefile: 'clean-all' target"  "grep -q '^clean-all:' '$ROOT/Makefile'"
check "Makefile: delegates android to gradlew"  "grep -q 'gradlew' '$ROOT/Makefile'"
check "Makefile: delegates backend to make"     "grep -q 'cd backend' '$ROOT/Makefile'"
check "Makefile: delegates intelligence to make" "grep -q 'cd intelligence' '$ROOT/Makefile'"

# =============================================================================
# TASK 0.5 — .editorconfig
# =============================================================================
echo ""
printf "${YELLOW}Task 0.5 — .editorconfig${NC}\n"

check ".editorconfig exists"          "[ -f '$ROOT/.editorconfig' ]"
check ".editorconfig: root = true"    "grep -q 'root = true' '$ROOT/.editorconfig'"
check ".editorconfig: charset utf-8"  "grep -q 'charset = utf-8' '$ROOT/.editorconfig'"
check ".editorconfig: lf line endings" "grep -q 'end_of_line = lf' '$ROOT/.editorconfig'"
check ".editorconfig: insert_final_newline" "grep -q 'insert_final_newline = true' '$ROOT/.editorconfig'"
check ".editorconfig: Kotlin/Java 4-space indent" "grep -A5 '\[.*kt' '$ROOT/.editorconfig' | grep -q 'indent_size = 4'"
check ".editorconfig: Go uses tabs"   "grep -A5 '\[.*\.go' '$ROOT/.editorconfig' | grep -q 'indent_style = tab'"
check ".editorconfig: Python 4-space indent" "grep -A5 '\[.*\.py' '$ROOT/.editorconfig' | grep -q 'indent_size = 4'"
check ".editorconfig: trim_trailing_whitespace" "grep -q 'trim_trailing_whitespace = true' '$ROOT/.editorconfig'"

# =============================================================================
# TASK 0.6 — .pre-commit-config.yaml
# =============================================================================
echo ""
printf "${YELLOW}Task 0.6 — .pre-commit-config.yaml${NC}\n"

check ".pre-commit-config.yaml exists" "[ -f '$ROOT/.pre-commit-config.yaml' ]"

# Validate YAML syntax (use Python if available, else skip)
# Note: on Windows+Git Bash, python3 needs Windows-style forward-slash path
if command -v python3 &>/dev/null; then
  WIN_PATH="$(cygpath -w "$ROOT/.pre-commit-config.yaml" 2>/dev/null | tr '\\\\' '/' || echo "$ROOT/.pre-commit-config.yaml")"
  check ".pre-commit-config.yaml: valid YAML" \
    "python3 -c \"import yaml; yaml.safe_load(open('$WIN_PATH'))\" 2>/dev/null"
else
  printf "  ${YELLOW}~${NC} YAML syntax check skipped (python3 not found)\n"
fi

check ".pre-commit-config.yaml: has commitlint hook" \
  "grep -q 'commitlint' '$ROOT/.pre-commit-config.yaml'"
check ".pre-commit-config.yaml: has commit-msg stage" \
  "grep -q 'commit-msg' '$ROOT/.pre-commit-config.yaml'"
check ".pre-commit-config.yaml: no hardcoded secrets" \
  "! grep -q 'ghp_\|AKIA\|sk-' '$ROOT/.pre-commit-config.yaml'"

# Check commitlint config has required types
check "commitlint.config.js exists"   "[ -f '$ROOT/commitlint.config.js' ]"
check "commitlint: type 'feat'"       "grep -q \"'feat'\" '$ROOT/commitlint.config.js'"
check "commitlint: type 'fix'"        "grep -q \"'fix'\" '$ROOT/commitlint.config.js'"
check "commitlint: type 'perf'"       "grep -q \"'perf'\" '$ROOT/commitlint.config.js'"
check "commitlint: type 'refactor'"   "grep -q \"'refactor'\" '$ROOT/commitlint.config.js'"
check "commitlint: type 'docs'"       "grep -q \"'docs'\" '$ROOT/commitlint.config.js'"
check "commitlint: type 'test'"       "grep -q \"'test'\" '$ROOT/commitlint.config.js'"
check "commitlint: type 'build'"      "grep -q \"'build'\" '$ROOT/commitlint.config.js'"
check "commitlint: type 'ci'"         "grep -q \"'ci'\" '$ROOT/commitlint.config.js'"
check "commitlint: type 'chore'"      "grep -q \"'chore'\" '$ROOT/commitlint.config.js'"
check "commitlint: type 'revert'"     "grep -q \"'revert'\" '$ROOT/commitlint.config.js'"
check "commitlint: scope 'android'"   "grep -q \"'android'\" '$ROOT/commitlint.config.js'"
check "commitlint: scope 'backend'"   "grep -q \"'backend'\" '$ROOT/commitlint.config.js'"
check "commitlint: scope 'intelligence'" "grep -q \"'intelligence'\" '$ROOT/commitlint.config.js'"
check "commitlint: scope 'ci'"        "grep -q \"'ci'\" '$ROOT/commitlint.config.js'"
check "commitlint: scope 'docs'"      "grep -q \"'docs'\" '$ROOT/commitlint.config.js'"
check "commitlint: scope 'deps'"      "grep -q \"'deps'\" '$ROOT/commitlint.config.js'"

# =============================================================================
# TASK 0.7 — Spec docs moved to docs/
# =============================================================================
echo ""
printf "${YELLOW}Task 0.7 — Spec docs in docs/${NC}\n"

check "docs/FRONTEND_MVP.md exists"       "[ -f '$ROOT/docs/FRONTEND_MVP.md' ]"
check "docs/BACKEND_MVP.md exists"        "[ -f '$ROOT/docs/BACKEND_MVP.md' ]"
check "docs/DESIGN_SYSTEM.md exists"      "[ -f '$ROOT/docs/DESIGN_SYSTEM.md' ]"
check "docs/DEVOPS_AUTOMATION.md exists"  "[ -f '$ROOT/docs/DEVOPS_AUTOMATION.md' ]"
check "docs/INTELLIGENCE_LAYER.md exists" "[ -f '$ROOT/docs/INTELLIGENCE_LAYER.md' ]"

check "FRONTEND_MVP.md NOT at root"       "[ ! -f '$ROOT/FRONTEND_MVP.md' ]"
check "BACKEND_MVP.md NOT at root"        "[ ! -f '$ROOT/BACKEND_MVP.md' ]"
check "DESIGN_SYSTEM.md NOT at root"      "[ ! -f '$ROOT/DESIGN_SYSTEM.md' ]"
check "DEVOPS_AUTOMATION.md NOT at root"  "[ ! -f '$ROOT/DEVOPS_AUTOMATION.md' ]"
check "INTELLIGENCE_LAYER.md NOT at root" "[ ! -f '$ROOT/INTELLIGENCE_LAYER.md' ]"

check "CLAUDE.md references docs/ paths"  "grep -q 'docs/FRONTEND_MVP' '$ROOT/CLAUDE.md'"
check "CLAUDE.md: DEVOPS ref uses docs/"  "grep -q 'docs/DEVOPS_AUTOMATION' '$ROOT/CLAUDE.md'"
check "CLAUDE.md: BACKEND ref uses docs/" "grep -q 'docs/BACKEND_MVP' '$ROOT/CLAUDE.md'"
check "CLAUDE.md: no bare FRONTEND_MVP at root" "! grep -qE '^[^├│|].*[^d][^o][^c][^s][^/]FRONTEND_MVP' '$ROOT/CLAUDE.md'"
check "PROGRESS.md spec refs use docs/"   "grep -q 'docs/DEVOPS_AUTOMATION' '$ROOT/PROGRESS.md'"

# =============================================================================
# TASK 0.8 — bootstrap.sh
# =============================================================================
echo ""
printf "${YELLOW}Task 0.8 — scripts/bootstrap.sh${NC}\n"

check "bootstrap.sh exists"              "[ -f '$ROOT/scripts/bootstrap.sh' ]"
check "bootstrap.sh is executable"       "[ -x '$ROOT/scripts/bootstrap.sh' ]"
check "bootstrap.sh has set -euo pipefail" "grep -q 'set -euo pipefail' '$ROOT/scripts/bootstrap.sh'"
check "bootstrap.sh has shebang"         "head -1 '$ROOT/scripts/bootstrap.sh' | grep -q '#!/usr/bin/env bash'"
check "bootstrap.sh --help exits 0"      "bash '$ROOT/scripts/bootstrap.sh' --help"
check "bootstrap.sh --help prints USAGE" "bash '$ROOT/scripts/bootstrap.sh' --help | grep -q 'USAGE'"
check "bootstrap.sh checks JDK"          "grep -qi 'jdk\|java' '$ROOT/scripts/bootstrap.sh'"
check "bootstrap.sh checks Android SDK"  "grep -qi 'android' '$ROOT/scripts/bootstrap.sh'"
check "bootstrap.sh checks Go"           "grep -q 'go' '$ROOT/scripts/bootstrap.sh'"
check "bootstrap.sh checks Docker"       "grep -qi 'docker' '$ROOT/scripts/bootstrap.sh'"
check "bootstrap.sh checks Python"       "grep -qi 'python' '$ROOT/scripts/bootstrap.sh'"
check "bootstrap.sh checks pre-commit"   "grep -q 'pre-commit' '$ROOT/scripts/bootstrap.sh'"
check "bootstrap.sh copies .env.example" "grep -q '\.env\.example' '$ROOT/scripts/bootstrap.sh'"
check "bootstrap.sh idempotent note"     "grep -qi 'idempotent\|safe to re-run' '$ROOT/scripts/bootstrap.sh'"

# =============================================================================
# TASK 0.9 — doctor.sh
# =============================================================================
echo ""
printf "${YELLOW}Task 0.9 — scripts/doctor.sh${NC}\n"

check "doctor.sh exists"                 "[ -f '$ROOT/scripts/doctor.sh' ]"
check "doctor.sh is executable"          "[ -x '$ROOT/scripts/doctor.sh' ]"
check "doctor.sh has set -euo pipefail"  "grep -q 'set -euo pipefail' '$ROOT/scripts/doctor.sh'"
check "doctor.sh has shebang"            "head -1 '$ROOT/scripts/doctor.sh' | grep -q '#!/usr/bin/env bash'"
check "doctor.sh --help exits 0"         "bash '$ROOT/scripts/doctor.sh' --help"
check "doctor.sh --help prints USAGE"    "bash '$ROOT/scripts/doctor.sh' --help | grep -q 'USAGE'"
check "doctor.sh checks JDK"             "grep -qi 'jdk\|java' '$ROOT/scripts/doctor.sh'"
check "doctor.sh checks Android SDK"     "grep -qi 'android' '$ROOT/scripts/doctor.sh'"
check "doctor.sh checks Go"              "grep -q 'go' '$ROOT/scripts/doctor.sh'"
check "doctor.sh checks Docker"          "grep -qi 'docker' '$ROOT/scripts/doctor.sh'"
check "doctor.sh checks pre-commit"      "grep -q 'pre-commit' '$ROOT/scripts/doctor.sh'"
check "doctor.sh checks Postgres"        "grep -qi 'postgres' '$ROOT/scripts/doctor.sh'"
check "doctor.sh checks env vars"        "grep -qi 'env\|\.env' '$ROOT/scripts/doctor.sh'"
check "doctor.sh has exit code logic"    "grep -q 'FAIL' '$ROOT/scripts/doctor.sh'"

# =============================================================================
# TASK 0.10 — run-backend-local.sh
# =============================================================================
echo ""
printf "${YELLOW}Task 0.10 — scripts/run-backend-local.sh${NC}\n"

check "run-backend-local.sh exists"           "[ -f '$ROOT/scripts/run-backend-local.sh' ]"
check "run-backend-local.sh is executable"    "[ -x '$ROOT/scripts/run-backend-local.sh' ]"
check "run-backend-local.sh set -euo pipefail" "grep -q 'set -euo pipefail' '$ROOT/scripts/run-backend-local.sh'"
check "run-backend-local.sh has shebang"      "head -1 '$ROOT/scripts/run-backend-local.sh' | grep -q '#!/usr/bin/env bash'"
check "run-backend-local.sh --help exits 0"   "bash '$ROOT/scripts/run-backend-local.sh' --help"
check "run-backend-local.sh --help prints USAGE" "bash '$ROOT/scripts/run-backend-local.sh' --help | grep -q 'USAGE'"
check "run-backend-local.sh starts docker-compose" "grep -q 'docker compose' '$ROOT/scripts/run-backend-local.sh'"
check "run-backend-local.sh waits for Postgres" "grep -qi 'pg_isready\|postgres.*ready\|waiting.*postgres' '$ROOT/scripts/run-backend-local.sh'"
check "run-backend-local.sh runs goose"       "grep -q 'goose' '$ROOT/scripts/run-backend-local.sh'"
check "run-backend-local.sh starts air"       "grep -q 'air' '$ROOT/scripts/run-backend-local.sh'"
check "run-backend-local.sh prints LAN IP"    "grep -qi 'lan.*ip\|lan_ip\|LAN' '$ROOT/scripts/run-backend-local.sh'"
check "run-backend-local.sh Ctrl+C trap"      "grep -q 'trap.*INT\|trap.*TERM' '$ROOT/scripts/run-backend-local.sh'"
check "run-backend-local.sh leaves docker up" "grep -qi 'containers stay running\|leaves.*running' '$ROOT/scripts/run-backend-local.sh'"
check "run-backend-local.sh has --migrate-only" "grep -q 'migrate-only\|MIGRATE_ONLY' '$ROOT/scripts/run-backend-local.sh'"

# =============================================================================
# Results
# =============================================================================
TOTAL=$((PASS + FAIL))
echo ""
echo "============================================================"
if [ "$FAIL" -eq 0 ]; then
  printf "${GREEN}ALL $TOTAL CHECKS PASSED${NC}\n"
else
  printf "${RED}$FAIL/$TOTAL CHECKS FAILED${NC}\n"
  echo ""
  echo "Failed checks:"
  for f in "${FAILURES[@]}"; do
    printf "  ${RED}✗${NC} %s\n" "$f"
  done
fi
echo "============================================================"

exit "$FAIL"
