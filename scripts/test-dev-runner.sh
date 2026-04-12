#!/usr/bin/env bash
# =============================================================================
# ScanVault — dev runner lifecycle tests
# Validates lifecycle policy and Android launch robustness for dev scripts
# Usage: bash scripts/test-dev-runner.sh
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PASS=0
FAIL=0
FAILURES=()

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

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

# shellcheck disable=SC1090
source "$ROOT/scripts/lib/dev-lifecycle.sh"

echo ""
printf "${YELLOW}Lifecycle policy helper${NC}\n"

check "dev lifecycle helper exists" "[ -f '$ROOT/scripts/lib/dev-lifecycle.sh' ]"
check "auto + interrupted => stop" "should_stop_backend_on_exit auto true"
check "auto + normal exit => keep" "! should_stop_backend_on_exit auto false"
check "always => stop" "should_stop_backend_on_exit always false"
check "never => keep" "! should_stop_backend_on_exit never true"
check "invalid policy => keep (safe default)" "! should_stop_backend_on_exit invalid true"

echo ""
printf "${YELLOW}dev.sh interface and wiring${NC}\n"

check "dev.sh exists" "[ -f '$ROOT/scripts/dev.sh' ]"
check "dev.sh syntax valid" "bash -n '$ROOT/scripts/dev.sh'"
check "dev.sh help includes backend-exit-policy" "bash '$ROOT/scripts/dev.sh' --help | grep -q -- '--backend-exit-policy'"
check "dev.sh sources lifecycle helper" "grep -q 'scripts/lib/dev-lifecycle.sh' '$ROOT/scripts/dev.sh'"
check "dev.sh validates backend exit policy" "grep -q 'Invalid --backend-exit-policy' '$ROOT/scripts/dev.sh'"

echo ""
printf "${YELLOW}Android launch robustness${NC}\n"

check "dev.sh resolves launcher activity dynamically" "grep -q 'resolve-activity --brief' '$ROOT/scripts/dev.sh'"
check "dev.sh uses dynamic component launch" "grep -q 'start_component' '$ROOT/scripts/dev.sh'"
check "dev.sh has monkey fallback" "grep -q 'shell monkey -p' '$ROOT/scripts/dev.sh'"

check "run-phone.sh exists" "[ -f '$ROOT/scripts/run-phone.sh' ]"
check "run-phone.sh syntax valid" "bash -n '$ROOT/scripts/run-phone.sh'"
check "run-phone.sh resolves launcher activity dynamically" "grep -q 'resolve-activity --brief' '$ROOT/scripts/run-phone.sh'"
check "run-phone.sh has monkey fallback" "grep -q 'shell monkey -p' '$ROOT/scripts/run-phone.sh'"

TOTAL=$((PASS + FAIL))
echo ""
echo "============================================================"
if [ "$FAIL" -eq 0 ]; then
  printf "${GREEN}ALL $TOTAL CHECKS PASSED${NC}\n"
  echo "============================================================"
  exit 0
fi

printf "${RED}$FAIL / $TOTAL CHECKS FAILED${NC}\n"
printf "${YELLOW}Failures:${NC}\n"
for f in "${FAILURES[@]}"; do
  printf "  - %s\n" "$f"
done
echo "============================================================"
exit 1
