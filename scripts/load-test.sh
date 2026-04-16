#!/usr/bin/env bash
# =============================================================================
# ScanVault — load-test.sh
# Load test the API Gateway staging endpoint with 500 concurrent users.
# Uses k6 if available, falls back to vegeta, falls back to curl-based test.
#
# Usage:
#   ./scripts/load-test.sh               # full load test (500 VU, 30s)
#   ./scripts/load-test.sh --dry-run     # print URL and exit (no actual test)
#   ./scripts/load-test.sh --duration 60 # custom duration in seconds
#   ./scripts/load-test.sh --vus 100     # custom virtual users
#   ./scripts/load-test.sh --help
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS_FILE="$ROOT/android/core/network/api_base_url.properties"

# ── Help ──────────────────────────────────────────────────────────────────────
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
load-test.sh — ScanVault API load tester

USAGE:
  ./scripts/load-test.sh [OPTIONS]

OPTIONS:
  --help, -h         Show this help
  --dry-run          Print the target URL and exit without running test
  --duration SECS    Test duration in seconds (default: 30)
  --vus N            Virtual users / concurrent requests (default: 500)
  --target PATH      API path to test (default: /health)

TOOLS (tried in order):
  1. k6    — https://k6.io/docs/get-started/installation/
  2. vegeta — https://github.com/tsenart/vegeta
  3. curl loop (fallback, less accurate)

REPORT:
  Results saved to /tmp/scanvault-load-test-report.txt
EOF
  exit 0
fi

# ── Parse flags ───────────────────────────────────────────────────────────────
DRY_RUN=false
DURATION=30
VUS=500
TARGET_PATH="/health"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)   DRY_RUN=true; shift ;;
    --duration)  DURATION="$2"; shift 2 ;;
    --vus)       VUS="$2"; shift 2 ;;
    --target)    TARGET_PATH="$2"; shift 2 ;;
    *) echo "Unknown flag: $1 (use --help)"; exit 1 ;;
  esac
done

# ── Read API base URL ─────────────────────────────────────────────────────────
if [[ ! -f "$PROPS_FILE" ]]; then
  echo "ERROR: $PROPS_FILE not found."
  echo "Run: cd infra && terraform output -raw api_gateway_url (or scripts/get_api_url.sh)"
  exit 1
fi

API_BASE_URL=""
while IFS= read -r line; do
  if [[ "$line" =~ ^API_BASE_URL=(.+)$ ]]; then
    API_BASE_URL="${BASH_REMATCH[1]}"
    break
  fi
done < "$PROPS_FILE"

if [[ -z "$API_BASE_URL" ]]; then
  echo "ERROR: API_BASE_URL not found in $PROPS_FILE"
  exit 1
fi

TARGET_URL="${API_BASE_URL%/}${TARGET_PATH}"
REPORT_FILE="/tmp/scanvault-load-test-report.txt"

echo "=========================================="
echo " ScanVault Load Test"
echo "=========================================="
echo " Target URL : $TARGET_URL"
echo " VUs        : $VUS"
echo " Duration   : ${DURATION}s"
echo " Report     : $REPORT_FILE"
echo "=========================================="
echo ""

if $DRY_RUN; then
  echo "[dry-run] Would test: $TARGET_URL"
  echo "[dry-run] VUs=$VUS Duration=${DURATION}s"
  echo "Dry run complete — no requests sent."
  exit 0
fi

REPORT_HEADER="ScanVault Load Test Report
Target: $TARGET_URL
VUs: $VUS  Duration: ${DURATION}s
Date: $(date -u)
"

# ── Tool detection ─────────────────────────────────────────────────────────────
run_k6() {
  echo "Using k6..."
  K6_SCRIPT="/tmp/scanvault-k6-script.js"
  cat > "$K6_SCRIPT" <<EOF
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: ${VUS},
  duration: '${DURATION}s',
  thresholds: {
    http_req_duration: ['p(99)<3000'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.get('${TARGET_URL}');
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 3s': (r) => r.timings.duration < 3000,
  });
  sleep(0.1);
}
EOF

  k6 run "$K6_SCRIPT" 2>&1 | tee "$REPORT_FILE"
  echo "$REPORT_HEADER" >> "$REPORT_FILE"
  echo "k6 report saved to $REPORT_FILE"
}

run_vegeta() {
  echo "Using vegeta..."
  echo "GET $TARGET_URL" | \
    vegeta attack -rate="${VUS}/1s" -duration="${DURATION}s" | \
    vegeta report | tee "$REPORT_FILE"
  echo "$REPORT_HEADER" >> "$REPORT_FILE"
  echo "Vegeta report saved to $REPORT_FILE"
}

run_curl_fallback() {
  echo "Using curl fallback (no k6 or vegeta found)..."
  echo "WARNING: curl fallback is not suitable for accurate load testing."
  echo "Install k6 (https://k6.io) or vegeta for production-grade tests."
  echo ""

  SUCCESS=0
  FAIL=0
  TOTAL=0
  START_TIME=$(date +%s)

  for _ in $(seq 1 100); do
    HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 5 "$TARGET_URL" 2>/dev/null || echo "000")
    if [[ "$HTTP_CODE" == "200" ]]; then
      SUCCESS=$((SUCCESS+1))
    else
      FAIL=$((FAIL+1))
    fi
    TOTAL=$((TOTAL+1))
  done

  END_TIME=$(date +%s)
  ELAPSED=$((END_TIME-START_TIME))

  REPORT="${REPORT_HEADER}
=== curl fallback results (100 sequential requests) ===
Success : $SUCCESS
Failed  : $FAIL
Total   : $TOTAL
Elapsed : ${ELAPSED}s
RPS     : $(echo "scale=1; $TOTAL/$ELAPSED" | bc 2>/dev/null || echo "N/A")
"
  echo "$REPORT" | tee "$REPORT_FILE"
  echo "Report saved to $REPORT_FILE"
}

# ── Run appropriate tool ──────────────────────────────────────────────────────
if command -v k6 &>/dev/null; then
  run_k6
elif command -v vegeta &>/dev/null; then
  run_vegeta
else
  run_curl_fallback
fi

echo ""
echo "Load test complete. Report: $REPORT_FILE"
