#!/usr/bin/env bash
# security-release-gates.sh — Paperkeep P5.7 security release gates
# Run before every release build to verify all security checks pass.
# Usage: ./scripts/security-release-gates.sh
# Exit code: 0 = all checks pass, 1 = one or more checks failed.

set -euo pipefail

ANDROID_DIR="android"
APP_DIR="${ANDROID_DIR}/app"
PASS=0
FAIL=1
OVERALL=0

print_header() { echo ""; echo "══ $1 ══"; }
pass() { echo "  ✓ $1"; }
fail() { echo "  ✗ $1"; OVERALL=1; }

# ── Gate 1: Detekt ────────────────────────────────────────────────────────────
print_header "Gate 1: Detekt static analysis"
if (cd "${ANDROID_DIR}" && ./gradlew detekt --quiet 2>&1); then
    pass "Detekt clean"
else
    fail "Detekt found issues — run ./gradlew detekt for details"
fi

# ── Gate 2: Log.* in release sources ─────────────────────────────────────────
print_header "Gate 2: No Log.* calls in release sources"
LOG_HITS=$(grep -rn "Log\.\(v\|d\|i\|w\|e\)\b" \
    --include="*.kt" \
    "${APP_DIR}/src/main" \
    "${ANDROID_DIR}/core" \
    "${ANDROID_DIR}/feature" \
    2>/dev/null | grep -v "//.*Log\." | wc -l || true)
if [ "${LOG_HITS}" -eq 0 ]; then
    pass "No Log.* calls found in release sources"
else
    fail "Found ${LOG_HITS} Log.* call(s) in release sources"
    grep -rn "Log\.\(v\|d\|i\|w\|e\)\b" \
        --include="*.kt" \
        "${APP_DIR}/src/main" \
        "${ANDROID_DIR}/core" \
        "${ANDROID_DIR}/feature" \
        2>/dev/null | grep -v "//.*Log\." | head -20
fi

# ── Gate 3: android:exported="true" justification comments ───────────────────
print_header "Gate 3: Manifest exported=true has justification comments"
EXPORTED_COUNT=$(grep -rn 'android:exported="true"' \
    --include="*.xml" \
    "${ANDROID_DIR}" 2>/dev/null | wc -l || true)
COMMENTED_COUNT=$(grep -rn -B2 'android:exported="true"' \
    --include="*.xml" \
    "${ANDROID_DIR}" 2>/dev/null | grep -c "<!--.*WHY" || true)
if [ "${EXPORTED_COUNT}" -eq 0 ] || [ "${COMMENTED_COUNT}" -ge "${EXPORTED_COUNT}" ]; then
    pass "All exported=true components have justification comments (${EXPORTED_COUNT} found)"
else
    fail "Some exported=true components missing justification comments"
    grep -rn 'android:exported="true"' --include="*.xml" "${ANDROID_DIR}" 2>/dev/null
fi

# ── Gate 4: No file:// URIs shared via Intent ────────────────────────────────
print_header "Gate 4: No file:// URIs in Intent actions"
FILE_URI_HITS=$(grep -rn '"file://' \
    --include="*.kt" \
    "${APP_DIR}/src/main" \
    "${ANDROID_DIR}/core" \
    "${ANDROID_DIR}/feature" \
    2>/dev/null | wc -l || true)
if [ "${FILE_URI_HITS}" -eq 0 ]; then
    pass "No file:// URIs found (FileProvider enforced)"
else
    fail "Found ${FILE_URI_HITS} file:// URI reference(s) — use FileProvider"
fi

# ── Gate 5: No hardcoded API keys or secrets ─────────────────────────────────
print_header "Gate 5: No hardcoded secrets"
SECRET_PATTERNS=(
    "AAAA[0-9A-Za-z_-]{140,}"   # FCM server key pattern
    "AIza[0-9A-Za-z_-]{35}"      # Google API key
    "sk-[a-zA-Z0-9]{20,}"        # Generic secret key
    "password.*=.*\"[^\"]{8,}\""  # Hardcoded passwords
    "secret.*=.*\"[^\"]{8,}\""    # Hardcoded secrets
)
SECRET_HITS=0
for pattern in "${SECRET_PATTERNS[@]}"; do
    HITS=$(grep -rn -E "${pattern}" \
        --include="*.kt" --include="*.xml" --include="*.properties" \
        "${ANDROID_DIR}" 2>/dev/null \
        | grep -v "keystore.properties" \
        | grep -v "test\|Test\|mock\|Mock" \
        | wc -l || true)
    SECRET_HITS=$((SECRET_HITS + HITS))
done
if [ "${SECRET_HITS}" -eq 0 ]; then
    pass "No hardcoded secrets detected"
else
    fail "Possible hardcoded secrets found (${SECRET_HITS} match(es)) — review manually"
fi

# ── Gate 6: No OkHttp/Ktor/Retrofit in app sources (no backend) ──────────────
print_header "Gate 6: Zero network client imports in app sources"
NETWORK_HITS=$(grep -rn \
    -e "import okhttp3\." \
    -e "import io.ktor.client" \
    -e "import retrofit2\." \
    --include="*.kt" \
    "${APP_DIR}/src/main" \
    "${ANDROID_DIR}/core" \
    "${ANDROID_DIR}/feature" \
    2>/dev/null | wc -l || true)
if [ "${NETWORK_HITS}" -eq 0 ]; then
    pass "No OkHttp/Ktor/Retrofit imports (zero-network policy enforced)"
else
    fail "Found ${NETWORK_HITS} network client import(s) — violates zero-network policy"
fi

# ── Gate 7: allowBackup=false in manifest ────────────────────────────────────
print_header "Gate 7: android:allowBackup=false"
ALLOW_BACKUP=$(grep -r 'android:allowBackup="false"' \
    --include="*.xml" "${ANDROID_DIR}" 2>/dev/null | wc -l || true)
if [ "${ALLOW_BACKUP}" -ge 1 ]; then
    pass "android:allowBackup=false set"
else
    fail "android:allowBackup must be false (encrypted files must not leak via ADB backup)"
fi

# ── Gate 8: R8/minify enabled in release build ───────────────────────────────
print_header "Gate 8: R8 minification enabled in release"
MINIFY_ENABLED=$(grep -n 'isMinifyEnabled = true' \
    "${APP_DIR}/build.gradle.kts" 2>/dev/null | wc -l || true)
if [ "${MINIFY_ENABLED}" -ge 1 ]; then
    pass "R8 minification enabled"
else
    fail "isMinifyEnabled must be true for release build"
fi

# ── Gate 9: V2/V3 signing enabled ────────────────────────────────────────────
print_header "Gate 9: V2 and V3 APK signing enabled"
V2=$(grep -n 'enableV2Signing = true' "${APP_DIR}/build.gradle.kts" 2>/dev/null | wc -l || true)
V3=$(grep -n 'enableV3Signing = true' "${APP_DIR}/build.gradle.kts" 2>/dev/null | wc -l || true)
if [ "${V2}" -ge 1 ] && [ "${V3}" -ge 1 ]; then
    pass "V2 and V3 APK signing configured"
else
    fail "Both enableV2Signing and enableV3Signing must be true"
fi

# ── Gate 10: No WebView usage ─────────────────────────────────────────────────
print_header "Gate 10: No WebView in app sources"
WEBVIEW_HITS=$(grep -rn "WebView\|webview\|webkit.WebView" \
    --include="*.kt" --include="*.xml" \
    "${APP_DIR}/src/main" \
    "${ANDROID_DIR}/core" \
    "${ANDROID_DIR}/feature" \
    2>/dev/null | wc -l || true)
if [ "${WEBVIEW_HITS}" -eq 0 ]; then
    pass "No WebView usage (RCE vector eliminated)"
else
    fail "Found ${WEBVIEW_HITS} WebView reference(s) — WebView is banned"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
print_header "Summary"
if [ "${OVERALL}" -eq 0 ]; then
    echo "  ALL GATES PASSED — safe to build release APK"
else
    echo "  ONE OR MORE GATES FAILED — fix before release"
fi

exit "${OVERALL}"
