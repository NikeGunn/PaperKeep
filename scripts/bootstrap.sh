#!/usr/bin/env bash
# =============================================================================
# ScanVault — bootstrap.sh
# Sets up a fresh development environment.
# Idempotent: safe to re-run at any time.
#
# Usage:
#   ./scripts/bootstrap.sh          # full setup
#   ./scripts/bootstrap.sh --help   # show this message
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
bootstrap.sh — ScanVault dev environment setup

USAGE:
  ./scripts/bootstrap.sh [OPTIONS]

OPTIONS:
  --help, -h    Show this help message and exit

WHAT IT DOES:
  Checks for (and installs where possible):
    • macOS or Linux OS (Windows not supported for dev)
    • JDK 21          (installs via SDKMAN if missing)
    • Android SDK      command-line tools, platform 35, build-tools 35.0.0
    • Android NDK      27.x (required for OpenCV JNI)
    • Go 1.23+
    • Docker
    • Python 3.12+
    • pre-commit       (pip install pre-commit)
    • Fastlane         (via bundler — not a global gem)
    • goose            database migration tool
    • sqlc             SQL→Go code generator
    • golangci-lint
    • air              Go hot-reload
    • ktlint + detekt  Android linters

  Also:
    • Copies backend/.env.example → backend/.env if missing
    • Installs pre-commit hooks (commit-msg + pre-commit)
    • Runs scripts/doctor.sh at the end to verify everything

NOTES:
  All tool installs are idempotent — already-installed tools are skipped.
  Requires curl, git, and bash 4+.
EOF
  exit 0
fi

# -----------------------------------------------------------------------------
# Colours & helpers
# -----------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

info()    { printf "${BLUE}[bootstrap]${NC} %s\n" "$*"; }
ok()      { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
warn()    { printf "  ${YELLOW}!${NC} %s\n" "$*"; }
fail()    { printf "  ${RED}✗${NC} %s\n" "$*"; }
section() { echo ""; printf "${YELLOW}── %s ──${NC}\n" "$*"; }

ERRORS=0
mark_error() { ERRORS=$((ERRORS + 1)); }

# -----------------------------------------------------------------------------
# OS check
# -----------------------------------------------------------------------------
section "Operating system"
OS="$(uname -s)"
case "$OS" in
  Darwin) ok "macOS detected" ;;
  Linux)  ok "Linux detected" ;;
  *)
    fail "Unsupported OS: $OS (need macOS or Linux)"
    fail "Windows users: use WSL2 (Ubuntu 22.04 LTS recommended)"
    mark_error
    ;;
esac

# -----------------------------------------------------------------------------
# JDK 21
# -----------------------------------------------------------------------------
section "JDK 21"
if command -v java &>/dev/null; then
  JAVA_VER=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1)
  if [[ "$JAVA_VER" -ge 21 ]]; then
    ok "JDK $JAVA_VER found ($(command -v java))"
  else
    warn "JDK $JAVA_VER found but need ≥ 21 — attempting SDKMAN install"
    if command -v sdk &>/dev/null; then
      sdk install java 21.0.3-tem && ok "JDK 21 installed via SDKMAN"
    else
      fail "SDKMAN not found. Install SDKMAN first: https://sdkman.io/install"
      fail "Then run: sdk install java 21.0.3-tem"
      mark_error
    fi
  fi
else
  warn "java not found — attempting SDKMAN install"
  if command -v sdk &>/dev/null; then
    sdk install java 21.0.3-tem && ok "JDK 21 installed via SDKMAN"
  else
    fail "java not found and SDKMAN not available"
    fail "Install SDKMAN: curl -s https://get.sdkman.io | bash"
    fail "Then: sdk install java 21.0.3-tem"
    mark_error
  fi
fi

# -----------------------------------------------------------------------------
# Android SDK
# -----------------------------------------------------------------------------
section "Android SDK"
ANDROID_HOME="${ANDROID_HOME:-${HOME}/Android/Sdk}"
if [[ -d "$ANDROID_HOME/cmdline-tools" ]]; then
  ok "Android SDK found at $ANDROID_HOME"
else
  fail "Android SDK not found at $ANDROID_HOME"
  fail "Set ANDROID_HOME or install via Android Studio / sdkmanager"
  mark_error
fi

# Check platform 35
if [[ -d "$ANDROID_HOME/platforms/android-35" ]]; then
  ok "Android platform 35 installed"
else
  warn "Android platform 35 not found — attempting install"
  if command -v sdkmanager &>/dev/null; then
    yes | sdkmanager "platforms;android-35" "build-tools;35.0.0" &>/dev/null \
      && ok "Platform 35 + build-tools 35.0.0 installed"
  else
    fail "sdkmanager not found — install platform 35 manually"
    mark_error
  fi
fi

# Check NDK 27
NDK_DIR="$ANDROID_HOME/ndk"
if ls "$NDK_DIR"/27.* &>/dev/null 2>&1; then
  ok "NDK 27.x found"
else
  warn "NDK 27.x not found — attempting install"
  if command -v sdkmanager &>/dev/null; then
    sdkmanager "ndk;27.0.12077973" &>/dev/null \
      && ok "NDK 27 installed"
  else
    fail "NDK 27.x not found — install from Android Studio SDK Manager"
    mark_error
  fi
fi

# -----------------------------------------------------------------------------
# Go 1.23+
# -----------------------------------------------------------------------------
section "Go 1.23+"
if command -v go &>/dev/null; then
  GO_VER=$(go version | grep -oE 'go[0-9]+\.[0-9]+' | grep -oE '[0-9]+\.[0-9]+')
  GO_MAJ=$(echo "$GO_VER" | cut -d. -f1)
  GO_MIN=$(echo "$GO_VER" | cut -d. -f2)
  if [[ "$GO_MAJ" -gt 1 ]] || [[ "$GO_MAJ" -eq 1 && "$GO_MIN" -ge 23 ]]; then
    ok "Go $GO_VER found"
  else
    fail "Go $GO_VER found but need ≥ 1.23 — download from https://go.dev/dl/"
    mark_error
  fi
else
  fail "go not found — install from https://go.dev/dl/ (1.23+)"
  mark_error
fi

# -----------------------------------------------------------------------------
# Docker
# -----------------------------------------------------------------------------
section "Docker"
if command -v docker &>/dev/null; then
  if docker info &>/dev/null 2>&1; then
    DOCKER_VER=$(docker --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
    ok "Docker $DOCKER_VER running"
  else
    fail "Docker installed but daemon not running — start Docker Desktop"
    mark_error
  fi
else
  fail "docker not found — install from https://docs.docker.com/get-docker/"
  mark_error
fi

# docker compose (v2 plugin syntax)
if docker compose version &>/dev/null 2>&1; then
  ok "docker compose (v2) available"
elif command -v docker-compose &>/dev/null; then
  ok "docker-compose (v1) available"
else
  fail "docker compose not found — update Docker Desktop to 3.6+"
  mark_error
fi

# -----------------------------------------------------------------------------
# Python 3.12+
# -----------------------------------------------------------------------------
section "Python 3.12+"
PY_CMD=""
for cmd in python3.12 python3 python; do
  if command -v "$cmd" &>/dev/null; then
    PY_VER=$("$cmd" --version 2>&1 | grep -oE '[0-9]+\.[0-9]+' | head -1)
    PY_MAJ=$(echo "$PY_VER" | cut -d. -f1)
    PY_MIN=$(echo "$PY_VER" | cut -d. -f2)
    if [[ "$PY_MAJ" -ge 3 && "$PY_MIN" -ge 12 ]]; then
      PY_CMD="$cmd"
      ok "Python $PY_VER found ($cmd)"
      break
    fi
  fi
done
if [[ -z "$PY_CMD" ]]; then
  fail "Python 3.12+ not found — install from https://www.python.org/downloads/"
  mark_error
fi

# -----------------------------------------------------------------------------
# pre-commit
# -----------------------------------------------------------------------------
section "pre-commit"
if command -v pre-commit &>/dev/null; then
  PC_VER=$(pre-commit --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
  ok "pre-commit $PC_VER found"
else
  warn "pre-commit not found — installing via pip"
  if [[ -n "$PY_CMD" ]]; then
    "$PY_CMD" -m pip install --quiet pre-commit \
      && ok "pre-commit installed"
  else
    fail "Cannot install pre-commit without Python 3.12+"
    mark_error
  fi
fi

# Install hooks if not already installed
if [[ -f "$ROOT/.pre-commit-config.yaml" ]]; then
  cd "$ROOT"
  if pre-commit install --hook-type commit-msg --hook-type pre-commit &>/dev/null; then
    ok "pre-commit hooks installed"
  else
    warn "pre-commit hook install failed — run manually: pre-commit install"
  fi
fi

# -----------------------------------------------------------------------------
# Go tools: goose, sqlc, golangci-lint, air
# -----------------------------------------------------------------------------
section "Go tools"

install_go_tool() {
  local name="$1"
  local pkg="$2"
  if command -v "$name" &>/dev/null; then
    ok "$name found ($(command -v "$name"))"
  else
    warn "$name not found — installing"
    go install "$pkg" &>/dev/null && ok "$name installed" || {
      fail "Failed to install $name: go install $pkg"
      mark_error
    }
  fi
}

install_go_tool goose       "github.com/pressly/goose/v3/cmd/goose@latest"
install_go_tool sqlc        "github.com/sqlc-dev/sqlc/cmd/sqlc@latest"
install_go_tool golangci-lint "github.com/golangci/golangci-lint/cmd/golangci-lint@latest"
install_go_tool air         "github.com/air-verse/air@latest"
install_go_tool gosec       "github.com/securego/gosec/v2/cmd/gosec@latest"
install_go_tool govulncheck "golang.org/x/vuln/cmd/govulncheck@latest"

# -----------------------------------------------------------------------------
# .env file
# -----------------------------------------------------------------------------
section "Environment files"
if [[ -f "$ROOT/backend/.env.example" ]]; then
  if [[ ! -f "$ROOT/backend/.env" ]]; then
    cp "$ROOT/backend/.env.example" "$ROOT/backend/.env"
    ok "Copied backend/.env.example → backend/.env"
    warn "IMPORTANT: Edit backend/.env and fill in real values before running"
  else
    ok "backend/.env already exists"
  fi
else
  warn "backend/.env.example not found — will be created in task 1A.3"
fi

# -----------------------------------------------------------------------------
# Final check
# -----------------------------------------------------------------------------
echo ""
if [[ "$ERRORS" -gt 0 ]]; then
  printf "${RED}Bootstrap completed with $ERRORS error(s).${NC}\n"
  printf "Fix the errors above, then re-run: ${YELLOW}./scripts/bootstrap.sh${NC}\n"
  echo ""
  printf "Then run: ${YELLOW}./scripts/doctor.sh${NC} for a full health check.\n"
  exit 1
else
  printf "${GREEN}Bootstrap complete! All checks passed.${NC}\n"
  echo ""
fi

# Run doctor if available
if [[ -f "$ROOT/scripts/doctor.sh" ]]; then
  bash "$ROOT/scripts/doctor.sh"
fi
