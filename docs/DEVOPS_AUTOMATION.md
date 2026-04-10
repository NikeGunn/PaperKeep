# ScanVault — DevOps, Automation & Release Engineering

**Goal:** A solo dev (Nikhil) should be able to `git push` and have everything else happen automatically — tests, builds, versioning, deployment to Play Store, backend rollout, over-the-air config updates, changelogs, release notes. No manual clicking. No forgetting steps. No "it worked on my machine."

This document covers the whole machinery behind the two MVP specs (`FRONTEND_MVP.md` and `BACKEND_MVP.md`).

---

## 0. Philosophy (how big tech actually ships)

The automation in this doc is modeled on how companies like Shopify, Stripe, and Linear actually operate — adapted for a single developer on a private GitHub repo with zero budget.

Core principles:

1. **The main branch is always releasable.** If it builds green, it can ship.
2. **Every commit is a candidate for production.** Versioning is automatic, not negotiated.
3. **Push once, ship everywhere.** A single `git push` triggers frontend build, backend build, tests, staging deploy, and (when tagged) production promotion.
4. **Boring is good.** No clever scripts. Bash + GitHub Actions + Fastlane. Anyone could reproduce it from this doc.
5. **Rollback must be one command.** If prod breaks at 2am in Nepal, there's a single command that reverts everything.
6. **Secrets are never in the repo.** Ever. GitHub Secrets for CI, systemd EnvironmentFile for the VPS, Android Keystore for the signing key.
7. **Observability is part of the release, not an afterthought.** Every deploy gets a release marker in Sentry and Grafana so we can correlate problems with changes.
8. **OTA > Play Store updates where safe.** Play Store takes 2–24 hours per update and burns a review budget. Config, copy, feature flags, and AI models ship over-the-air in seconds.

---

## 1. Repository Layout (monorepo)

One private GitHub repo. Monorepo. Both frontend and backend in one place, plus shared tooling.

```
scanvault/
├── .github/
│   ├── workflows/                 # All CI/CD pipelines
│   │   ├── android-ci.yml
│   │   ├── android-release.yml
│   │   ├── backend-ci.yml
│   │   ├── backend-deploy-staging.yml
│   │   ├── backend-deploy-production.yml
│   │   ├── ota-config-push.yml
│   │   └── security-scan.yml
│   ├── ISSUE_TEMPLATE/
│   └── dependabot.yml
├── android/                       # Everything from FRONTEND_MVP.md
│   ├── app/
│   ├── core/
│   ├── feature/
│   ├── fastlane/
│   ├── gradle/
│   ├── build.gradle.kts
│   └── gradle.properties
├── backend/                       # Everything from BACKEND_MVP.md
│   ├── cmd/
│   ├── internal/
│   ├── db/
│   ├── deploy/
│   ├── go.mod
│   └── Makefile
├── ota/                           # Over-the-air config payloads
│   ├── remote-config.json
│   ├── feature-flags.json
│   ├── store-listing-copy.json
│   └── ml-models-manifest.json
├── scripts/                       # Dev shell scripts
│   ├── run-phone.sh
│   ├── run-emulator.sh
│   ├── run-backend-local.sh
│   ├── bootstrap.sh
│   ├── doctor.sh
│   ├── release.sh
│   ├── rollback.sh
│   └── generate-screenshots.sh
├── docs/
│   ├── FRONTEND_MVP.md
│   ├── BACKEND_MVP.md
│   ├── DEVOPS_AUTOMATION.md       # this file
│   ├── DESIGN_SYSTEM.md
│   ├── RUNBOOK.md
│   ├── CHANGELOG.md               # auto-generated
│   └── ARCHITECTURE_DECISIONS/
├── .gitignore
├── .editorconfig
├── .pre-commit-config.yaml
├── LICENSE                        # keep private repo; license TBD
├── README.md
└── Makefile                       # top-level Makefile delegates to subdirs
```

### Branching strategy: trunk-based

One long-lived branch: `main`. Feature work happens on short-lived branches (`feat/xyz`, `fix/abc`) merged via PR. No `develop`, no `release/*`, no git-flow nonsense. Releases are triggered by **tags**, not branches.

- `main` → continuous integration, continuous deployment to staging
- `v*.*.*` tag → production release (Play Store + production backend)
- `hotfix/*` branches only exist if main is broken and you need to ship a fix without the other pending work

---

## 2. Local Development Scripts (the "flexible dev loop")

All scripts live in `scripts/` and are bash (POSIX-compatible, macOS + Linux). Every script supports `--help`, runs `set -euo pipefail` at the top, and uses colored output so you can see status at a glance.

### `scripts/bootstrap.sh`

Run **once** after cloning the repo. Sets up everything a brand-new machine needs.

```bash
#!/usr/bin/env bash
# Bootstraps a fresh dev environment.
# Idempotent: safe to re-run.

set -euo pipefail

# Checks:
#   - macOS or Linux
#   - JDK 21 installed (installs via sdkman if missing)
#   - Android SDK cmdline tools
#   - Android NDK (for OpenCV)
#   - Go 1.23+
#   - Docker
#   - Fastlane (via bundler, not global gem)
#   - pre-commit hooks
#   - Postgres 16 via docker-compose
#   - goose + sqlc + golangci-lint + gosec + govulncheck
#   - ktlint + detekt
#
# Also:
#   - Copies .env.example → .env if missing
#   - Prompts for GitHub personal access token for private dependency access
#   - Accepts Android SDK licenses non-interactively
#   - Downloads OpenCV Android SDK and extracts into android/opencv/
#   - Runs `scripts/doctor.sh` at the end to verify everything
```

### `scripts/doctor.sh`

A health check for the dev environment. Runs when things feel broken.

```bash
# Reports status of:
#   ✅ JDK version = 21
#   ✅ Android SDK platform = 35
#   ✅ Android build tools = 35.0.0
#   ✅ NDK version = 27.0.x
#   ✅ Go version ≥ 1.23
#   ✅ Docker daemon running
#   ✅ Postgres container reachable
#   ✅ Gradle daemon healthy
#   ✅ ADB can see a device
#   ✅ All required env vars present
#   ✅ Pre-commit hooks installed
```

### `scripts/run-phone.sh` — the critical one

**This is the script Nikhil will run 50 times a day.** It installs and launches the debug build on a physically connected phone, with live-reload of Compose UI where possible, and streams logs.

```bash
#!/usr/bin/env bash
# Build and run on the first connected physical device.
# Usage:
#   ./scripts/run-phone.sh                  # debug build, default flavor
#   ./scripts/run-phone.sh --variant debug  # explicit
#   ./scripts/run-phone.sh --clean          # clean build first
#   ./scripts/run-phone.sh --release        # install release-signed debug
#   ./scripts/run-phone.sh --profile        # with baseline profile + tracing
#   ./scripts/run-phone.sh --benchmark      # run macrobenchmark suite
#   ./scripts/run-phone.sh --wifi           # connect to phone over Wi-Fi ADB

set -euo pipefail

# Step 1: Find a connected device. If none, print helpful ADB setup instructions.
# Step 2: Check backend — if running-phone.sh wants to point at local backend,
#         make sure `scripts/run-backend-local.sh` is already running.
#         Inject the local backend URL into a build config field (BuildConfig.API_BASE_URL).
#         Use the machine's LAN IP so the phone can reach the laptop.
# Step 3: Gradle assembleDebug with parallel workers and configuration cache on.
# Step 4: ADB install -r (replace) the APK on the device.
# Step 5: ADB shell am start the main activity.
# Step 6: Open logcat filtered to our package and pipe it through `pidcat` style formatting.
#         Colorize by log level. Suppress system spam.
# Step 7: Register a SIGINT trap so Ctrl+C kills logcat cleanly.
#
# Flags:
#   --wifi       Pairs over Wi-Fi ADB (uses `adb pair` + `adb connect`) so the phone
#                doesn't need to stay tethered. Critical for camera testing — you can't
#                hold a phone naturally with a USB cable sticking out.
#   --profile    Enables method tracing via Perfetto and pulls the trace file when you
#                stop the app. Opens Perfetto UI in the browser.
#   --clean      Runs `./gradlew clean` first. Slow. Only when builds get weird.
#   --backend    URL override, e.g. `--backend https://api-staging.scanvault.app`
```

**Why this matters for a camera app:** you cannot properly test a document scanner on an emulator — emulator cameras give synthetic frames and edge detection looks perfect when it isn't. You must test on real hardware constantly. The `--wifi` flag is what makes this tolerable: you tether once to pair, then scan freely while the laptop streams logs.

### `scripts/run-emulator.sh`

Same idea but for an emulator. Used for UI testing, not camera testing.

```bash
# Boots a pre-configured AVD called "scanvault-pixel-6" (created by bootstrap.sh).
# Waits for it to be ready, then delegates to run-phone.sh internals.
# Useful for: UI regression testing, screenshot generation, TalkBack testing.
```

### `scripts/run-backend-local.sh`

Starts the backend for local development.

```bash
# - Starts docker-compose (Postgres + Redis)
# - Waits for them to be healthy
# - Runs goose migrations
# - Runs `sqlc generate` if queries changed
# - Starts the Go API with hot reload via `air`
# - Prints the LAN IP + port so the phone script can connect
# - On Ctrl+C, gracefully shuts down the Go process but LEAVES docker-compose up
#   (so the next run is fast)
```

### `scripts/run-all.sh`

The nuclear option — brings up everything.

```bash
# Opens two tmux panes (or iTerm2 tabs, or Terminal.app windows — auto-detects):
#   Pane 1: ./scripts/run-backend-local.sh
#   Pane 2: ./scripts/run-phone.sh --backend http://<lan-ip>:8080 --wifi
# You get backend logs on one side and phone logs on the other, side by side.
```

### `scripts/release.sh`

The one-command release. We'll define it fully in section 5.

```bash
# Usage:
#   ./scripts/release.sh patch   # 1.2.3 → 1.2.4
#   ./scripts/release.sh minor   # 1.2.3 → 1.3.0
#   ./scripts/release.sh major   # 1.2.3 → 2.0.0
#
# Asks for confirmation, then:
#   1. Verifies working tree is clean and on main
#   2. Pulls latest main
#   3. Runs tests (aborts on failure)
#   4. Bumps version in ONE place (see Single Source of Truth section)
#   5. Generates changelog from conventional commits
#   6. Commits the version bump + changelog
#   7. Tags with vX.Y.Z
#   8. Pushes commit and tag
#   9. GitHub Actions takes over from here — you close the terminal and relax
```

### `scripts/rollback.sh`

```bash
# Usage:
#   ./scripts/rollback.sh android         # Halts rollout, reverts to previous Play track
#   ./scripts/rollback.sh backend         # systemctl start scanvault@previous on the VPS
#   ./scripts/rollback.sh ota             # Reverts the current OTA config to previous
#   ./scripts/rollback.sh all             # All three, in order
```

### `scripts/generate-screenshots.sh`

Automated Play Store screenshots. No more manual phone-posing.

```bash
# Uses Fastlane Screengrab to generate localized screenshots in all supported languages.
# Output: fastlane/metadata/android/<locale>/images/phoneScreenshots/
# Then uploads them via `fastlane supply` during the release workflow.
```

---

## 3. Single Source of Truth for Versioning

**Problem:** without discipline, the app version ends up scattered across `build.gradle.kts`, `AndroidManifest.xml`, Fastlane config, GitHub release notes, backend `go.mod`, OTA manifest, and the changelog. Everything drifts. This is how bugs ship.

**Solution:** one file. `VERSION` at the repo root. Everything else reads from it.

### `/VERSION`

```
2.4.1
```

That's the whole file. No newline at the end. Semver only.

### How each system consumes it

**Gradle (`android/app/build.gradle.kts`):**

```kotlin
val projectVersion = rootProject.file("../VERSION").readText().trim()
val (major, minor, patch) = projectVersion.split(".").map { it.toInt() }
val versionCodeCalculated = major * 10000 + minor * 100 + patch

android {
    defaultConfig {
        versionName = projectVersion
        versionCode = versionCodeCalculated
    }
}
```

The `versionCode` calculation gives us: `2.4.1 → 20401`. Monotonically increasing forever, human-readable, no collision until version 99.99.99.

**Go backend (`backend/internal/config/version.go`):**

Generated at build time by ldflags:

```bash
go build -ldflags "-X main.Version=$(cat ../VERSION) -X main.Commit=$(git rev-parse --short HEAD) -X main.BuildDate=$(date -u +%Y-%m-%dT%H:%M:%SZ)" ./cmd/api
```

Exposed via `GET /v1/version` and baked into every log line via `slog` attribute.

**Fastlane (`android/fastlane/Fastfile`):**

```ruby
VERSION = File.read("../../VERSION").strip
```

**OTA manifest (`ota/remote-config.json`):**

Has a `min_app_version` field that's compared against the running app. Pushed via CI.

**GitHub release:**

The workflow reads `VERSION` to tag and title the release.

**Changelog:**

Auto-generated into `docs/CHANGELOG.md` by the release script, organized under a heading like `## [2.4.1] - 2026-04-15`.

### Version bump rules (semver, enforced by convention)

- `fix:` commits → patch bump
- `feat:` commits → minor bump
- `feat!:` or `BREAKING CHANGE:` → major bump
- `chore:`, `docs:`, `refactor:`, `test:` → no bump (unless explicitly released)

The release script inspects the commit log since the last tag and suggests a version, but the developer confirms.

---

## 4. Conventional Commits + Automatic Changelog

Every commit to `main` must follow [Conventional Commits](https://www.conventionalcommits.org). A pre-commit hook and a CI check enforce this.

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:** `feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`, `revert`
**Scopes:** `android`, `backend`, `ota`, `ci`, `docs`, `deps`

### Examples

```
feat(android): add ID card dual-capture mode

Captures front and back of an ID, auto-composes into a single A4 page.
Closes #42.
```

```
fix(backend): prevent refresh token rotation race condition

Two concurrent refresh calls could both issue new tokens. Now guarded by
SELECT FOR UPDATE.
```

```
feat(android)!: require Android 8.0 minimum

BREAKING CHANGE: dropping Android 7 support. Affects <1% of Play Store traffic.
```

### Enforcement

- `commitlint` pre-commit hook rejects malformed messages locally
- GitHub Action on PR validates the PR title (since we use squash merges)
- Release script parses the commit history to build the changelog

### Changelog format (generated into `docs/CHANGELOG.md`)

```markdown
# Changelog

## [2.4.1] - 2026-04-15

### 🐛 Fixed
- **android**: prevent crash when capturing with front camera ([#89](...))
- **backend**: handle R2 503 errors with retry backoff ([#91](...))

### ⚡ Performance
- **android**: 30% faster edge detection on Snapdragon 6xx ([#88](...))

### 🔒 Security
- **backend**: upgrade libsodium to fix CVE-2026-xxxx ([#92](...))

## [2.4.0] - 2026-04-10
...
```

Icons + categories make it scannable. Links go to the actual PRs.

---

## 5. The Release Flow (one command, end to end)

This is the flow Nikhil uses every time he ships. Once the automation is set up, the entire thing is `./scripts/release.sh minor` + going to make tea.

### What happens after `./scripts/release.sh minor` runs

```
┌─────────────────────────────────────────────────────────────────┐
│  1. LOCAL (release.sh on Nikhil's laptop)                       │
│                                                                  │
│  - Confirm branch = main, working tree clean                    │
│  - git pull --ff-only                                            │
│  - Run `make test-fast` (unit tests both sides)                 │
│  - Read VERSION file, bump per semver                           │
│  - Generate changelog from commits since last tag               │
│  - Write new VERSION, update CHANGELOG.md                       │
│  - git commit -m "chore(release): v2.4.1"                       │
│  - git tag -a v2.4.1 -m "Release 2.4.1"                         │
│  - git push origin main --follow-tags                           │
│  - Exit — the rest is GitHub's problem now                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. GITHUB ACTIONS — tag push triggers android-release.yml      │
│                     AND backend-deploy-production.yml           │
│                                                                  │
│  Both workflows run in PARALLEL, gated on earlier CI success    │
└─────────────────────────────────────────────────────────────────┘
                              │
                ┌─────────────┴─────────────┐
                ▼                           ▼
┌────────────────────────────┐  ┌───────────────────────────────┐
│  ANDROID RELEASE WORKFLOW  │  │  BACKEND PRODUCTION WORKFLOW  │
│                            │  │                               │
│  - Checkout code + tag     │  │  - Checkout code + tag        │
│  - Set up JDK 21           │  │  - Set up Go 1.23             │
│  - Restore Gradle cache    │  │  - go vet, staticcheck, gosec │
│  - Restore upload keystore │  │  - go test ./... with race    │
│    from GitHub Secret      │  │  - govulncheck                │
│  - ./gradlew test          │  │  - Build binary with ldflags  │
│  - ./gradlew lint          │  │  - SCP to prod VPS (dry run)  │
│  - Detekt                  │  │  - SSH: systemctl stop api    │
│  - ./gradlew bundleRelease │  │  - SSH: backup current binary │
│    (AAB, not APK — Play    │  │  - SSH: deploy new binary     │
│    Store requires it)      │  │  - SSH: run migrations        │
│  - Generate native symbols │  │  - SSH: systemctl start api   │
│  - Fastlane supply:        │  │  - Curl /health + /ready      │
│    * Upload AAB to Play    │  │  - On failure: SSH auto       │
│      Console INTERNAL track│  │    rollback to previous       │
│    * Upload screenshots    │  │    binary                     │
│    * Upload changelog as   │  │  - Create Sentry release      │
│      release notes         │  │    marker                     │
│    * Upload native symbols │  │  - Post to Telegram: "Backend │
│  - Wait for Play processing│  │    2.4.1 deployed"            │
│  - Fastlane promotes       │  │                               │
│    INTERNAL → ALPHA if     │  │                               │
│    pre-launch report OK    │  │                               │
│  - Staged rollout to       │  │                               │
│    PRODUCTION at 10%       │  │                               │
│  - Create Sentry release   │  │                               │
│    marker                  │  │                               │
│  - Post to Telegram        │  │                               │
└────────────────────────────┘  └───────────────────────────────┘
                │                           │
                └─────────────┬─────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. POST-RELEASE AUTOMATED CHECKS                                │
│                                                                  │
│  A scheduled job 30 min after release:                          │
│    - Queries Sentry: any new error signatures since deploy?     │
│    - Queries Play Console: ANR rate, crash rate still < 0.5%?   │
│    - Queries Grafana: backend p99 latency still < 1s?           │
│  If all green → promote Play rollout 10% → 50% next day         │
│  If anything red → auto-halt rollout + alert Nikhil             │
└─────────────────────────────────────────────────────────────────┘
```

**Nikhil's actual involvement after `./scripts/release.sh minor`:** none, unless something breaks and Telegram pings him.

---

## 6. GitHub Actions Workflows (in detail)

All workflows go in `.github/workflows/`. Each one is a standalone `.yml` file.

### `android-ci.yml` — runs on every PR and push to main

```yaml
name: Android CI

on:
  pull_request:
    paths: ['android/**', '.github/workflows/android-ci.yml']
  push:
    branches: [main]
    paths: ['android/**']

concurrency:
  group: android-ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint-and-test:
    runs-on: ubuntu-latest
    timeout-minutes: 25
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Gradle setup with cache
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-home-cache-cleanup: true

      - name: Restore ccache for NDK builds
        uses: actions/cache@v4
        with:
          path: ~/.ccache
          key: ccache-ndk-${{ runner.os }}

      - name: Detekt
        run: ./gradlew detekt
        working-directory: android

      - name: Ktlint
        run: ./gradlew ktlintCheck
        working-directory: android

      - name: Unit tests
        run: ./gradlew testDebugUnitTest
        working-directory: android

      - name: Lint (Android)
        run: ./gradlew lintDebug
        working-directory: android

      - name: Assemble debug
        run: ./gradlew assembleDebug
        working-directory: android

      - name: Upload APK (PR preview)
        if: github.event_name == 'pull_request'
        uses: actions/upload-artifact@v4
        with:
          name: pr-${{ github.event.pull_request.number }}-debug.apk
          path: android/app/build/outputs/apk/debug/*.apk
          retention-days: 7

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: android-test-results
          path: android/**/build/reports/**

  instrumented-tests:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    needs: lint-and-test
    strategy:
      matrix:
        api-level: [26, 30, 34]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Enable KVM (for emulator)
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: AVD cache
        uses: actions/cache@v4
        with:
          path: |
            ~/.android/avd/*
            ~/.android/adb*
          key: avd-${{ matrix.api-level }}

      - name: Run instrumented tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          arch: x86_64
          profile: pixel_6
          script: cd android && ./gradlew connectedDebugAndroidTest
```

**Critical bits:** PR artifacts (anyone reviewing the PR — future collaborators, or Nikhil on another machine — can download the debug APK directly), matrix-tested on three Android versions, concurrency cancellation to save CI minutes.

### `android-release.yml` — runs on version tag push

```yaml
name: Android Release

on:
  push:
    tags: ['v*.*.*']

concurrency:
  group: android-release
  cancel-in-progress: false   # never cancel a release mid-flight

jobs:
  release:
    runs-on: ubuntu-latest
    timeout-minutes: 60
    environment: production   # requires manual approval if protection rules set
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0      # for changelog generation

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Set up Ruby for Fastlane
        uses: ruby/setup-ruby@v1
        with:
          ruby-version: '3.3'
          bundler-cache: true
          working-directory: android

      - name: Decode upload keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.ANDROID_UPLOAD_KEYSTORE_BASE64 }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 -d > android/app/upload-keystore.jks

      - name: Decode Play service account JSON
        env:
          PLAY_JSON_BASE64: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON_BASE64 }}
        run: |
          echo "$PLAY_JSON_BASE64" | base64 -d > android/fastlane/play-service-account.json

      - name: Build & upload to Play Console (internal track, staged rollout)
        env:
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        run: |
          cd android
          bundle exec fastlane release

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          body_path: docs/CHANGELOG.md
          files: |
            android/app/build/outputs/bundle/release/*.aab
            android/app/build/outputs/mapping/release/mapping.txt
          generate_release_notes: false

      - name: Create Sentry release marker
        uses: getsentry/action-release@v1
        env:
          SENTRY_AUTH_TOKEN: ${{ secrets.SENTRY_AUTH_TOKEN }}
          SENTRY_ORG: ${{ secrets.SENTRY_ORG }}
          SENTRY_PROJECT: scanvault-android
        with:
          environment: production
          version: ${{ github.ref_name }}

      - name: Clean up secrets
        if: always()
        run: |
          rm -f android/app/upload-keystore.jks
          rm -f android/fastlane/play-service-account.json

      - name: Notify Telegram
        if: always()
        env:
          TELEGRAM_TOKEN: ${{ secrets.TELEGRAM_BOT_TOKEN }}
          TELEGRAM_CHAT: ${{ secrets.TELEGRAM_CHAT_ID }}
        run: |
          STATUS="${{ job.status }}"
          VERSION="${{ github.ref_name }}"
          curl -s "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage" \
            -d "chat_id=$TELEGRAM_CHAT" \
            -d "text=📱 Android $VERSION: $STATUS"
```

### `backend-ci.yml` — runs on every PR and push touching `backend/**`

```yaml
name: Backend CI

on:
  pull_request:
    paths: ['backend/**', '.github/workflows/backend-ci.yml']
  push:
    branches: [main]
    paths: ['backend/**']

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: test
        ports: ['5432:5432']
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with:
          go-version: '1.23'
          cache-dependency-path: backend/go.sum

      - name: Install tools
        run: |
          go install github.com/pressly/goose/v3/cmd/goose@latest
          go install github.com/sqlc-dev/sqlc/cmd/sqlc@latest
          go install honnef.co/go/tools/cmd/staticcheck@latest

      - name: Verify sqlc up to date
        run: |
          cd backend && sqlc generate
          git diff --exit-code || (echo "sqlc generated files are stale. Run 'sqlc generate'." && exit 1)

      - name: Vet
        run: cd backend && go vet ./...

      - name: Staticcheck
        run: cd backend && staticcheck ./...

      - name: Gosec
        uses: securego/gosec@master
        with:
          args: -exclude-dir=backend/db -fmt=sarif -out=gosec.sarif ./backend/...

      - name: Upload gosec SARIF
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: gosec.sarif

      - name: govulncheck
        run: |
          go install golang.org/x/vuln/cmd/govulncheck@latest
          cd backend && govulncheck ./...

      - name: Run migrations
        env:
          DATABASE_URL: postgres://postgres:test@localhost:5432/postgres?sslmode=disable
        run: cd backend && goose -dir db/migrations postgres "$DATABASE_URL" up

      - name: Tests with race detector
        env:
          DATABASE_URL: postgres://postgres:test@localhost:5432/postgres?sslmode=disable
        run: cd backend && go test -race -cover ./...
```

### `backend-deploy-staging.yml` — auto-deploys to staging on every merge to main

```yaml
name: Backend Deploy Staging

on:
  push:
    branches: [main]
    paths: ['backend/**', 'ota/**']

concurrency:
  group: backend-staging
  cancel-in-progress: true

jobs:
  deploy:
    runs-on: ubuntu-latest
    needs: []   # but workflow_run dependency on backend-ci could be added
    environment: staging
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with:
          go-version: '1.23'

      - name: Build binary
        run: |
          cd backend
          VERSION=$(cat ../VERSION)
          COMMIT=$(git rev-parse --short HEAD)
          DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
          CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
            -ldflags "-s -w -X main.Version=$VERSION -X main.Commit=$COMMIT -X main.BuildDate=$DATE" \
            -o scanvault-api ./cmd/api

      - name: Set up SSH
        env:
          SSH_KEY: ${{ secrets.STAGING_SSH_KEY }}
        run: |
          mkdir -p ~/.ssh
          echo "$SSH_KEY" > ~/.ssh/id_ed25519
          chmod 600 ~/.ssh/id_ed25519
          ssh-keyscan -H ${{ secrets.STAGING_HOST }} >> ~/.ssh/known_hosts

      - name: Deploy
        env:
          HOST: ${{ secrets.STAGING_HOST }}
          USER: ${{ secrets.STAGING_USER }}
        run: |
          scp backend/scanvault-api $USER@$HOST:/opt/scanvault/scanvault-api.new
          ssh $USER@$HOST 'bash -s' << 'EOF'
            set -euo pipefail
            cd /opt/scanvault
            # Preserve previous binary for rollback
            cp scanvault-api scanvault-api.previous || true
            mv scanvault-api.new scanvault-api
            chmod +x scanvault-api
            # Run migrations
            ./scanvault-api migrate up
            # Reload
            sudo systemctl restart scanvault
            sleep 3
            # Smoke test
            curl -fsS http://localhost:8080/health
            curl -fsS http://localhost:8080/ready
          EOF

      - name: Notify Telegram
        if: always()
        run: |
          STATUS="${{ job.status }}"
          curl -s "https://api.telegram.org/bot${{ secrets.TELEGRAM_BOT_TOKEN }}/sendMessage" \
            -d "chat_id=${{ secrets.TELEGRAM_CHAT_ID }}" \
            -d "text=🔧 Backend staging deploy: $STATUS"
```

### `backend-deploy-production.yml` — triggers on version tag

Same as staging but:
- Deploys to the prod VPS
- Uses different secrets (`PROD_SSH_KEY`, `PROD_HOST`, `PROD_USER`)
- Has an explicit `environment: production` with manual approval gate on the first few releases (can disable later once confident)
- On failure, automatically SSHes in and runs `mv scanvault-api.previous scanvault-api && systemctl restart scanvault` to roll back
- Creates a Sentry release marker
- Pins the previous binary for 30 days before cleanup

### `ota-config-push.yml` — pushes OTA config changes without an app release

This is one of the most valuable workflows. Config changes (ad frequency, feature flags, remote copy, store listing text, ML model version pointers) deploy in **seconds**, not hours.

```yaml
name: OTA Config Push

on:
  push:
    branches: [main]
    paths: ['ota/**']

jobs:
  validate-and-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Validate JSON schemas
        run: |
          npm install -g ajv-cli
          ajv validate -s ota/schemas/remote-config.schema.json -d ota/remote-config.json
          ajv validate -s ota/schemas/feature-flags.schema.json -d ota/feature-flags.json

      - name: Validate min_app_version is not ahead of current VERSION
        run: |
          CURRENT=$(cat VERSION)
          MIN=$(jq -r .min_app_version ota/remote-config.json)
          # Fail if MIN > CURRENT using sort -V
          if [ "$(printf '%s\n%s' "$CURRENT" "$MIN" | sort -V | tail -1)" != "$CURRENT" ]; then
            echo "min_app_version ($MIN) is ahead of current VERSION ($CURRENT)"
            exit 1
          fi

      - name: Upload to R2 (production)
        env:
          AWS_ACCESS_KEY_ID: ${{ secrets.R2_ACCESS_KEY }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.R2_SECRET_KEY }}
          R2_ENDPOINT: ${{ secrets.R2_ENDPOINT }}
        run: |
          # Version the uploaded file so clients can detect changes
          TIMESTAMP=$(date -u +%Y%m%d%H%M%S)
          aws s3 cp ota/remote-config.json \
            s3://scanvault-ota/prod/remote-config-$TIMESTAMP.json \
            --endpoint-url "$R2_ENDPOINT"
          # Update the "latest" pointer atomically
          aws s3 cp ota/remote-config.json \
            s3://scanvault-ota/prod/remote-config-latest.json \
            --endpoint-url "$R2_ENDPOINT" \
            --cache-control "public, max-age=60"

      - name: Purge Cloudflare cache
        run: |
          curl -X POST "https://api.cloudflare.com/client/v4/zones/${{ secrets.CF_ZONE_ID }}/purge_cache" \
            -H "Authorization: Bearer ${{ secrets.CF_API_TOKEN }}" \
            -H "Content-Type: application/json" \
            --data '{"files":["https://ota.scanvault.app/prod/remote-config-latest.json"]}'

      - name: Notify Telegram
        run: |
          curl -s "https://api.telegram.org/bot${{ secrets.TELEGRAM_BOT_TOKEN }}/sendMessage" \
            -d "chat_id=${{ secrets.TELEGRAM_CHAT_ID }}" \
            -d "text=🔄 OTA config pushed to production"
```

### `security-scan.yml` — weekly scheduled run

```yaml
name: Security Scan

on:
  schedule:
    - cron: '0 3 * * 1'   # Monday 03:00 UTC
  workflow_dispatch:

jobs:
  android-deps:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: OWASP dependency check
        uses: dependency-check/Dependency-Check_Action@main
        with:
          project: scanvault-android
          path: android
          format: SARIF
          out: reports

      - uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: reports/dependency-check-report.sarif

  backend-deps:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with: { go-version: '1.23' }
      - run: go install golang.org/x/vuln/cmd/govulncheck@latest
      - run: cd backend && govulncheck ./...

  secrets-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: trufflesecurity/trufflehog@main
        with:
          path: ./
          base: main
          head: HEAD
          extra_args: --only-verified
```

---

## 7. Over-the-Air Update System (OTA)

**Why OTA:** a Play Store update takes 2–24 hours for review, plus the time it takes users to receive it. You burn a "release budget" each time you push. You also can't roll back quickly — you push a fix, which also takes 2–24 hours.

**What OTA can safely do:**
- Toggle feature flags ("ID card mode enabled for Tier 1 users only")
- Adjust AdMob frequency ("show interstitial every 5 exports" → "every 3")
- Update app copy and onboarding text without a rebuild
- Update the "min supported version" signal for forced upgrades
- Publish a changelog seen inside the app
- Swap ML model URLs (when we want to roll out a better dewarp model without a full release)
- Kill switch individual features if they start crashing

**What OTA must NEVER do:**
- Execute downloaded code. This is a Google Play policy violation AND a massive security hole.
- Load remote JavaScript or WebAssembly.
- Modify native behavior.
- Change security-critical defaults without user consent.

All OTA does is: download a signed JSON file, parse it, update in-memory config. The app's behavior changes because it reads that config. That's it.

### OTA file structure

```
ota/
├── schemas/
│   ├── remote-config.schema.json   # JSON schema for validation
│   ├── feature-flags.schema.json
│   └── ml-models-manifest.schema.json
├── remote-config.json              # The live file pushed to R2
├── feature-flags.json
├── store-listing-copy.json
└── ml-models-manifest.json
```

### `ota/remote-config.json` example

```json
{
  "schema_version": 1,
  "generated_at": "2026-04-15T09:00:00Z",
  "min_app_version": "2.0.0",
  "recommended_app_version": "2.4.1",
  "force_upgrade_below": "1.8.0",
  "ads": {
    "interstitial_after_n_exports": 5,
    "interstitial_cooldown_seconds": 180,
    "rewarded_ad_triggers": ["batch_export", "extra_ocr_language"]
  },
  "features": {
    "id_card_mode": { "enabled": true, "rollout_percent": 100 },
    "book_scan_mode": { "enabled": true, "rollout_percent": 50 },
    "experimental_dewarp": { "enabled": false, "rollout_percent": 0 }
  },
  "onboarding": {
    "title_en": "Scan anything. Keep it private.",
    "subtitle_en": "Edge detection in real time. No watermarks. No forced login.",
    "cta_en": "Start scanning"
  },
  "ml_models": {
    "dewarp": {
      "version": "1.2.0",
      "url": "https://ota.scanvault.app/models/dewarp-1.2.0.tflite",
      "sha256": "abc123..."
    }
  }
}
```

### Client-side integration (Android)

- On app launch (background, non-blocking) fetch `https://ota.scanvault.app/prod/remote-config-latest.json`
- Verify: HTTPS only, cert pinned, TTL respected
- Validate: schema, signature (optional HMAC in a future phase), `min_app_version` against running version
- Cache locally in encrypted DataStore; on first launch or offline, use the bundled default
- Expose to the rest of the app via a `RemoteConfigRepository` interface
- Feature flags use gradual rollout based on a stable hash of the account UUID (or install ID) modulo 100 — gives deterministic rollout without a server decision per call

### Rollout discipline

1. Change a value in `ota/remote-config.json`
2. Commit + push
3. `ota-config-push.yml` validates the schema, uploads to R2, purges the cache
4. Every running app picks up the new config within ~1 minute
5. If something breaks, revert the commit and push — the revert propagates in another minute

**This is the superpower.** A crisis at 3am looks like: open laptop, flip a feature flag from `true` to `false`, push, back to bed.

---

## 8. Fastlane Setup

Fastlane is the bridge between Gradle and Play Console. It handles:
- Building the signed AAB
- Uploading to Play Console
- Managing store listings (title, description, screenshots, feature graphic)
- Promoting between tracks (internal → alpha → beta → production)
- Managing staged rollouts

### `android/fastlane/Fastfile`

```ruby
fastlane_version "2.220.0"

default_platform :android

VERSION = File.read("../../VERSION").strip

platform :android do

  desc "Run unit tests"
  lane :test do
    gradle(task: "test", project_dir: "../")
  end

  desc "Build release AAB"
  lane :build_release do
    gradle(
      task: "bundle",
      build_type: "Release",
      project_dir: "../",
      properties: {
        "android.injected.signing.store.file" => File.expand_path("../app/upload-keystore.jks"),
        "android.injected.signing.store.password" => ENV["ANDROID_KEYSTORE_PASSWORD"],
        "android.injected.signing.key.alias" => ENV["ANDROID_KEY_ALIAS"],
        "android.injected.signing.key.password" => ENV["ANDROID_KEY_PASSWORD"],
      }
    )
  end

  desc "Upload to Play Console — internal track, then staged rollout to production"
  lane :release do
    test
    build_release

    # Upload to internal testing first — sanity check the binary is accepted
    upload_to_play_store(
      track: "internal",
      aab: "../app/build/outputs/bundle/release/app-release.aab",
      json_key: "play-service-account.json",
      release_status: "draft",
      skip_upload_apk: true,
      skip_upload_metadata: false,
      skip_upload_changelogs: false,
      skip_upload_images: false,
      skip_upload_screenshots: false,
      mapping: "../app/build/outputs/mapping/release/mapping.txt"
    )

    # Promote to production with a 10% staged rollout
    # First few releases: leave this as draft so Nikhil manually reviews in Play Console
    # Once confident: uncomment the production promotion
    #
    # upload_to_play_store(
    #   track: "production",
    #   track_promote_to: "production",
    #   rollout: "0.1",
    #   json_key: "play-service-account.json",
    #   skip_upload_aab: true,
    #   skip_upload_metadata: true,
    #   skip_upload_changelogs: false
    # )
  end

  desc "Promote current internal release to production (10%)"
  lane :promote_to_production do
    upload_to_play_store(
      track: "internal",
      track_promote_to: "production",
      rollout: "0.1",
      json_key: "play-service-account.json",
      skip_upload_aab: true,
      skip_upload_metadata: true
    )
  end

  desc "Bump production rollout percentage"
  lane :bump_rollout do |options|
    rollout = options[:rollout] || "0.5"
    upload_to_play_store(
      track: "production",
      rollout: rollout,
      json_key: "play-service-account.json",
      skip_upload_aab: true,
      skip_upload_metadata: true
    )
  end

  desc "Halt current production rollout"
  lane :halt_rollout do
    upload_to_play_store(
      track: "production",
      rollout: "0",
      json_key: "play-service-account.json",
      skip_upload_aab: true,
      skip_upload_metadata: true
    )
  end
end
```

### `android/fastlane/Appfile`

```ruby
json_key_file("play-service-account.json")
package_name("app.scanvault.android")
```

### Play Store metadata directory structure

```
android/fastlane/metadata/android/
├── en-US/
│   ├── title.txt                           # 30 chars max
│   ├── short_description.txt               # 80 chars max
│   ├── full_description.txt                # 4000 chars max
│   ├── video.txt                           # YouTube URL
│   ├── images/
│   │   ├── icon/en-US/icon.png             # 512x512
│   │   ├── featureGraphic/en-US/feature.png # 1024x500
│   │   ├── phoneScreenshots/
│   │   │   ├── 01_camera.png
│   │   │   ├── 02_edge_detection.png
│   │   │   ├── 03_filters.png
│   │   │   ├── 04_library.png
│   │   │   ├── 05_ocr_search.png
│   │   │   ├── 06_pdf_export.png
│   │   │   ├── 07_id_mode.png
│   │   │   └── 08_sync.png
│   │   └── tabletScreenshots/...
│   └── changelogs/
│       └── default.txt                     # Becomes "What's New" in Play Store
├── es-ES/
├── hi-IN/
├── ne-NP/
├── pt-BR/
├── id-ID/
└── ...
```

All metadata is version-controlled. Changes go through the same PR review as code.

---

## 9. Secrets Management

**Rule: no secret ever lives in the git repo, encrypted or otherwise.**

### Secret inventory and where it lives

| Secret                             | Purpose                                    | Stored in                            |
|------------------------------------|--------------------------------------------|--------------------------------------|
| Android upload keystore (.jks)     | Signs release AABs                         | GitHub Secret (base64)               |
| Android keystore password          | Unlocks the keystore                       | GitHub Secret                        |
| Android key alias + password       | Signs with the specific key                | GitHub Secret                        |
| Play service account JSON          | Fastlane upload auth                       | GitHub Secret (base64)               |
| Staging SSH private key            | Deploy to staging VPS                      | GitHub Secret                        |
| Production SSH private key         | Deploy to prod VPS                         | GitHub Secret (protected env)        |
| R2 access key + secret             | OTA uploads, backups                       | GitHub Secret                        |
| Sentry auth token                  | Create release markers                     | GitHub Secret                        |
| Telegram bot token + chat ID       | Deploy notifications                       | GitHub Secret                        |
| Postmark API token                 | Transactional email                        | systemd EnvironmentFile on prod VPS  |
| Paseto signing key                 | Session tokens                             | systemd EnvironmentFile on prod VPS  |
| Postgres password                  | DB access                                  | systemd EnvironmentFile on prod VPS  |
| Argon2id pepper                    | Extra auth layer                           | systemd EnvironmentFile on prod VPS  |
| IP hash HMAC key                   | Audit log IP anonymization                 | systemd EnvironmentFile on prod VPS  |
| age encryption key (public)        | Backup encryption                          | systemd EnvironmentFile on prod VPS  |
| age encryption key (private)       | Backup decryption                          | OFFLINE — USB stick in Nikhil's desk |
| Cloudflare API token               | Cache purge                                | GitHub Secret                        |
| AdMob app ID                       | Not secret, just config                    | BuildConfig field                    |

### Backing up the signing keystore (CRITICAL)

**Losing the Android upload keystore means you can never update your app again. Google will not help you.** Back it up immediately in three places:

1. GitHub Secret (primary, used by CI)
2. Encrypted offline backup on a USB drive kept in Nikhil's desk
3. Encrypted cloud backup in a personal password manager (Bitwarden/1Password) as a file attachment

The process for creating it:

```bash
keytool -genkey -v \
  -keystore upload-keystore.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias upload \
  -storetype PKCS12
# Use a strong password, save it in the password manager
# Then base64 encode for GitHub:
base64 -i upload-keystore.jks -o upload-keystore.jks.b64
# Paste contents into GitHub Secret ANDROID_UPLOAD_KEYSTORE_BASE64
```

Also enable Play App Signing so Google keeps a copy of the actual signing key — the upload key is then recoverable from Google if lost. **Do this during initial app setup.** It's a one-time decision.

---

## 10. Environments and Promotion

### Three environments

| Environment | Android build          | Backend                           | OTA                            |
|-------------|------------------------|-----------------------------------|--------------------------------|
| Dev         | Debug APK on laptop    | Local Docker Compose              | `ota/dev/` on R2              |
| Staging     | Internal Play track    | Staging VPS (`api-staging...`)    | `ota/staging/` on R2          |
| Production  | Production Play track  | Production VPS (`api....`)        | `ota/prod/` on R2             |

### Promotion rules

- Every push to `main` → **automatic** deploy to staging (backend) and OTA staging
- Every `v*.*.*` tag → **automatic** deploy to production backend, and Play Store internal track
- Play Store internal → production is **manual** for the first 5 releases, then can be automated
- Production rollout starts at 10%, scales to 50% after 24h, to 100% after 72h — automated based on crash-free rate and Sentry alerts

### Environment detection in the app

Release builds **always** point at production. There is no env switcher in release builds — that's a security risk (malicious flag-flipping).

Debug builds have a hidden developer screen (unlock by tapping the version number 7 times) that lets you switch between: local backend, staging backend, production backend. Useful for diagnosing "works on staging, broken on prod" issues.

---

## 11. Observability Integration with Releases

Every release must be observable. A bug that shows up 10 minutes after deploy needs to be traceable to that deploy instantly.

### Sentry release markers

Both Android and backend create a Sentry release marker on every deploy with:
- Version number
- Commit SHA
- Environment
- Files with source maps / mapping.txt uploaded

Result: any crash or error in the following days is grouped by release, and Sentry shows "this bug was introduced in 2.4.1."

### Grafana annotations

Every backend deploy posts an annotation to Grafana via its HTTP API:

```bash
curl -X POST "https://grafana.scanvault.app/api/annotations" \
  -H "Authorization: Bearer $GRAFANA_TOKEN" \
  -d '{
    "text": "Backend deployed: v2.4.1 (abc123)",
    "tags": ["deploy", "backend", "production"],
    "time": '$(date +%s000)'
  }'
```

Result: any metric spike in Grafana shows a vertical line labeled with the release that caused it.

### Play Console vitals monitoring

A scheduled GitHub Action runs every 30 minutes after a production release and queries the Google Play Developer API:

- Crash rate in the last hour
- ANR rate in the last hour
- Installs vs uninstalls

If crash rate exceeds 2%, the workflow automatically calls `fastlane halt_rollout` and pings Telegram.

---

## 12. Pre-commit Hooks

Prevent bad commits from ever being made locally. Uses [pre-commit framework](https://pre-commit.com).

### `.pre-commit-config.yaml`

```yaml
repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v5.0.0
    hooks:
      - id: trailing-whitespace
      - id: end-of-file-fixer
      - id: check-yaml
      - id: check-added-large-files
        args: ['--maxkb=1000']
      - id: check-merge-conflict
      - id: detect-private-key

  - repo: https://github.com/gitleaks/gitleaks
    rev: v8.20.0
    hooks:
      - id: gitleaks

  - repo: https://github.com/commitizen-tools/commitizen
    rev: v3.30.0
    hooks:
      - id: commitizen
        stages: [commit-msg]

  - repo: local
    hooks:
      - id: ktlint
        name: ktlint
        entry: bash -c 'cd android && ./gradlew ktlintFormat'
        language: system
        files: ^android/.*\.kt$
        pass_filenames: false

      - id: gofmt
        name: gofmt
        entry: bash -c 'cd backend && gofmt -w .'
        language: system
        files: ^backend/.*\.go$
        pass_filenames: false

      - id: json-schema-validate
        name: validate OTA configs
        entry: bash -c 'scripts/validate-ota.sh'
        language: system
        files: ^ota/.*\.json$
        pass_filenames: false
```

`scripts/bootstrap.sh` installs pre-commit and runs `pre-commit install --hook-type commit-msg --hook-type pre-commit`.

---

## 13. Dependabot

`.github/dependabot.yml`:

```yaml
version: 2
updates:
  - package-ecosystem: gradle
    directory: /android
    schedule:
      interval: weekly
      day: monday
    open-pull-requests-limit: 5
    reviewers: [nikhil]
    labels: [dependencies, android]
    groups:
      androidx:
        patterns: ['androidx.*']
      kotlin:
        patterns: ['org.jetbrains.kotlin*']

  - package-ecosystem: gomod
    directory: /backend
    schedule:
      interval: weekly
      day: monday
    open-pull-requests-limit: 5
    labels: [dependencies, backend]

  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: monthly
```

Dependabot opens PRs. The Android CI workflow runs on them. If they're green, Nikhil merges. Done.

---

## 14. Rollback Procedures

### Android rollback

Play Store doesn't have "revert to previous version" — it has **halt rollout**. So:

1. Immediately halt the current rollout: `bundle exec fastlane halt_rollout`
2. Users on the old version stay on the old version
3. Users who already received the new broken version are stuck until the next release
4. Therefore: **the real rollback is a hotfix release.** Branch from the previous tag, cherry-pick the fix, tag a patch version, ship. The whole pipeline runs, new AAB goes out, users on the broken version auto-update within 24 hours.

This is why **staged rollouts at 10% are non-negotiable**. At 10% you halt and hotfix; at 100% you've already affected everyone.

### Backend rollback

Built in to the deploy workflow. On failure it auto-reverts. Manually:

```bash
./scripts/rollback.sh backend
```

which SSHes in and does:

```bash
cd /opt/scanvault
sudo systemctl stop scanvault
mv scanvault-api scanvault-api.bad
mv scanvault-api.previous scanvault-api
sudo systemctl start scanvault
curl -fsS http://localhost:8080/health
```

If the rollback involves a schema change, it's trickier — see "migration safety" below.

### OTA rollback

```bash
./scripts/rollback.sh ota
```

Reads the previous committed version of `ota/remote-config.json` from git, writes it back, commits, pushes. The OTA push workflow deploys it within a minute.

### Migration safety (the hard one)

**Rule: every database migration must be backward compatible with the previous app version for at least one release cycle.**

Forbidden:
- `DROP COLUMN` in the same release as the code that stops using it
- `DROP TABLE` in the same release
- `NOT NULL` added to an existing column without a default
- Type changes (`TEXT → INT`) in a single migration

Required pattern:
1. Release N: add the new column/table/thing. Old code keeps working.
2. Release N+1: code starts using the new thing. Old code still works.
3. Release N+2: verify nothing uses the old thing. Drop it.

Migrations are named like `0015_add_quota_tier_column.sql` and go through `goose`. Every migration has an `-- +goose Up` and `-- +goose Down` section so rollback is possible, but in practice we never run `down` in production — we roll forward with another migration.

---

## 15. Performance & Cost Guardrails in CI

Every PR that changes the Android app runs Macrobenchmark on an API 34 emulator and posts the results as a PR comment:

```
📊 Macrobenchmark results vs main

Cold start:        412ms  (main: 425ms)  ✅ −3%
Warm start:        178ms  (main: 175ms)  ⚠️  +2%
Library scroll:    98.4% frames @ 60fps  ✅
APK size (release): 23.4 MB  (main: 23.1 MB)  ⚠️  +300KB
```

If cold start regresses by more than 10% or APK size grows by more than 1 MB in a single PR, the CI fails and requires a `performance-override` label to merge.

For the backend, every PR runs a quick load test against a throwaway staging instance and posts:

```
⚡ Backend performance vs main

/v1/sessions POST    p99: 145ms  (main: 148ms)  ✅
/v1/vault/documents  p99: 89ms   (main: 82ms)   ⚠️  +9%
Error rate:          0.00%
```

---

## 16. Feature Flags vs A/B Tests

Feature flags in the OTA config are for:
- **Kill switches:** turn off a broken feature without a release
- **Gradual rollouts:** ship to 10% → 50% → 100% over a few days
- **Market-specific features:** enable a feature only in Tier 1 countries
- **Beta testing:** opt-in flag exposed in settings for early testers

A/B tests are **not** in MVP. They require server-side bucketing, event collection, and statistical analysis — all of which violate the zero-knowledge architecture or require heavy analytics SDKs. Post-launch, if needed, we add Firebase Remote Config for experiments only on users who opt in to analytics.

---

## 17. First-Time Setup Checklist

Before any of this automation runs, Nikhil has to do some one-time setup. Document this in `SETUP.md`:

- [ ] Create private GitHub repo `scanvault`
- [ ] Run `scripts/bootstrap.sh` on laptop
- [ ] Create Google Play Console account (one-time $25 fee)
- [ ] Register package name `app.scanvault.android`
- [ ] Generate Android upload keystore (instructions in section 9)
- [ ] Enable Play App Signing during first upload
- [ ] Create Play Console API service account → download JSON
- [ ] Link service account to Play Console project with "Release manager" permission
- [ ] Create Cloudflare account, set up R2 bucket: `scanvault-prod`, `scanvault-ota`, `scanvault-backups`
- [ ] Create Cloudflare API token scoped to those buckets only
- [ ] Buy domain `scanvault.app` (or whatever name you pick)
- [ ] Point DNS to Cloudflare
- [ ] Provision staging VPS (Hetzner CX22 — €4/mo)
- [ ] Provision production VPS (Hetzner CX32 — €8/mo)
- [ ] Run `deploy/bootstrap-server.sh` on each VPS (creates user, installs Caddy, Postgres, Redis, systemd unit)
- [ ] Generate SSH keys for CI deploys, add to VPS `authorized_keys`
- [ ] Create Sentry project
- [ ] Create Postmark account
- [ ] Create Telegram bot via @BotFather, get chat ID
- [ ] Add all secrets to GitHub repo settings → Secrets and variables → Actions
- [ ] Protect `main` branch: require PR + CI pass before merge
- [ ] Protect `production` environment: require manual approval (remove after first 5 clean releases)
- [ ] Enable GitHub Advanced Security on the repo (free for private repos up to a limit)
- [ ] Set up Dependabot (already in config, just enable in UI)
- [ ] Back up the signing keystore three ways (see section 9)

One-time. Takes ~half a day. After that, everything is `git push`.

---

## 18. What "Day in the Life" Looks Like

Here's what Nikhil's actual workflow looks like with this system:

**Morning, Kathmandu, 09:30:**
```bash
cd ~/code/scanvault
git pull
./scripts/run-all.sh
```
Backend starts in one pane, phone connects over Wi-Fi ADB, app installs, logs stream.

**Making a change:**
Edit a Kotlin file. Save. Gradle picks it up, phone reinstalls in ~8 seconds. Test on device.

**Committing:**
```bash
git add .
git commit -m "feat(android): add rotate-left gesture on crop screen"
```
Commitlint validates the message. Ktlint formats the code. Gitleaks scans for secrets. All happens in milliseconds.

**Pushing:**
```bash
git push
```
Android CI runs. Turns green. Backend doesn't care (the commit was Android-only, so `paths:` filter skips it).

**End of the week — shipping a release:**
```bash
./scripts/release.sh minor
```
Confirms. Bumps version. Generates changelog. Commits. Tags. Pushes. Logs:
```
✓ VERSION bumped: 2.4.0 → 2.5.0
✓ CHANGELOG updated
✓ Tagged v2.5.0
✓ Pushed to origin

GitHub Actions will now:
  - Build and upload Android AAB to Play Console (internal track)
  - Deploy backend to production VPS
  - Create Sentry release markers
  - Notify Telegram when done

Watch progress: https://github.com/nikhil/scanvault/actions
```

Nikhil closes the terminal, goes for tea. Twenty minutes later, Telegram pings:
```
📱 Android v2.5.0: success
🔧 Backend v2.5.0 deployed to production
📊 Play Console: crash rate 0.1%, proceeding with 10% rollout
```

**Something breaks at 23:00:**
Sentry alert → Telegram ping. Nikhil opens laptop, sees the bug is in the new filter logic. Flips the feature flag:
```bash
# Edit ota/remote-config.json
vim ota/remote-config.json
# Change "experimental_filter": {"enabled": true} to false
git add ota/remote-config.json
git commit -m "chore(ota): disable experimental_filter, crashing on Android 8"
git push
```
One minute later, all running apps read the new config and stop using the broken filter. Crisis over. Fix the bug properly tomorrow.

This is the whole point of the automation. The developer experience is **fast feedback loops** + **calm crisis response**.

---

## 19. Things I Deliberately Did NOT Add (to keep the MVP sharp)

- **Firebase Remote Config / App Distribution** — redundant with our OTA system and pulls in a big SDK
- **CircleCI / Jenkins / self-hosted runners** — GitHub Actions free tier is plenty for one developer
- **Kubernetes / Terraform** — overkill for one VPS
- **Separate staging domain infrastructure** — sharing the same Caddy config with a subdomain works fine
- **Nightly performance trending dashboards** — can add later if perf regressions become a pattern
- **Automatic dependency updates with merge bots** — Dependabot opens PRs, Nikhil reviews and merges manually (safer for a solo dev)
- **Canary deployments / blue-green** — the Play Store staged rollout IS our canary; the backend is small enough that systemd restart is fine
- **Snapshot testing of UI** — nice-to-have, can add later
- **Release signing with Sigstore / SLSA** — overkill for a solo dev but would be a nice Phase 6

---

## 20. Definition of Done for the Automation Setup

The automation is "done" when Nikhil can honestly say:

1. [ ] I have never once manually opened Play Console to upload an AAB
2. [ ] I have never once SSH'd into the production VPS to deploy a new binary
3. [ ] I have never once lost a deploy because I forgot to bump a version number somewhere
4. [ ] I can push a feature flag change and see it live in under 90 seconds
5. [ ] I can roll back a broken release in under 5 minutes
6. [ ] I know, from Telegram alerts, within 5 minutes of anything being broken in production
7. [ ] My commit history reads like a changelog because commitlint forces it
8. [ ] I have never once committed a secret (gitleaks would have blocked me)
9. [ ] I can onboard a second developer in under 2 hours via `scripts/bootstrap.sh` + this doc
10. [ ] I sleep at night knowing backups run nightly and I've restored from one at least once
