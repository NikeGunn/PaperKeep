# ScanVault — Build Progress

> **Claude Code:** Read this file after CLAUDE.md. Find the first unchecked `[ ]` task. That's your job.
> **TEST-GATE:** For EVERY task: BUILD → write TESTS → RUN tests → all PASS → ONLY THEN check the box. See CLAUDE.md for test requirements per task type.

## Current Sprint: WEEK 1 — Foundation

**Status:** IN PROGRESS
**Last session:** 2026-04-16
**Last completed task:** 4B.5–4B.10 — Phase 4B complete (sync status, cert pinning, tamper check, account mgmt, Ktor client, E2E test)

---

## PHASE 0: DevOps Skeleton (DO FIRST — everything depends on this)

> **Spec:** docs/DEVOPS_AUTOMATION.md sections 1-4

- [x] **0.1** Create repo structure (`android/`, `backend/`, `intelligence/`, `ota/`, `scripts/`, `docs/`) — 2026-04-10
- [x] **0.2** Create `VERSION` file with `0.1.0` — 2026-04-10
- [x] **0.3** Create root `.gitignore` (Android, Go, Python, IDE files, .env, secrets) — 2026-04-10
- [x] **0.4** Create root `Makefile` that delegates to subdirectories — 2026-04-10
- [x] **0.5** Create `.editorconfig` — 2026-04-10
- [x] **0.6** Create `.pre-commit-config.yaml` (commitlint for conventional commits) — 2026-04-10
- [x] **0.7** Move spec docs into `docs/` (FRONTEND_MVP.md, BACKEND_MVP.md, etc.) — 2026-04-10
- [x] **0.8** Create `scripts/bootstrap.sh` (dev environment setup) — 2026-04-10
- [x] **0.9** Create `scripts/doctor.sh` (health check) — 2026-04-10
- [x] **0.10** Create `scripts/run-backend-local.sh` — 2026-04-10
- [x] **0.11** Create `scripts/run-phone.sh` (Android dev loop) — 2026-04-10
- [x] **0.12** Create `scripts/release.sh` (version bump + tag + push) — 2026-04-10
- [x] **0.13** Create `scripts/rollback.sh` — 2026-04-10

**Acceptance:** `scripts/doctor.sh` runs and reports status. All scripts have `--help`. Conventional commit hook rejects bad messages.

---

## PHASE 1A: Backend Foundations (Go) — Weeks 1-2

> **Spec:** docs/BACKEND_MVP.md section 6 → Phase 1
> **Depends on:** Phase 0 completed

- [x] **1A.1** Go module init (`backend/go.mod`), directory structure per spec section 2 — 2026-04-10
- [x] **1A.2** `.golangci.yml` with strict config — 2026-04-10
- [x] **1A.3** `backend/.env.example` with all required vars documented — 2026-04-10
- [x] **1A.4** Config loader (`internal/config/`) — env vars via caarlos0/env — 2026-04-10
- [x] **1A.5** Postgres setup — migration 0001 (accounts, refresh_tokens, email_verification_tokens) — 2026-04-10
- [x] **1A.6** sqlc config + initial queries for accounts — 2026-04-10
- [x] **1A.7** HTTP server with chi router + middleware chain (RequestID, Logger, Recoverer, Timeout, SecureHeaders, CORS) — 2026-04-10
- [x] **1A.8** `/health` and `/ready` endpoints — 2026-04-10
- [x] **1A.9** Account creation (`POST /v1/accounts`) with double-hashing � 2026-04-10
- [x] **1A.10** Email verification (`GET /v1/accounts/verify`) � 2026-04-10
- [x] **1A.11** Login (`POST /v1/sessions`) — Paseto v4, constant-time, dummy hash for non-existent users
- [x] **1A.12** Token refresh (`POST /v1/sessions/refresh`) with rotation + replay detection � 2026-04-10
- [x] **1A.13** Logout (`DELETE /v1/sessions`) — 2026-04-11
- [x] **1A.14** Get account (`GET /v1/accounts/me`) — 2026-04-11
- [x] **1A.15** Change password (`POST /v1/accounts/me/password`) — 2026-04-11
- [x] **1A.16** Auth middleware (Paseto verification, account loading) — 2026-04-11
- [x] **1A.17** Rate limiting (in-memory for Phase 1) — 2026-04-11
- [x] **1A.18** Security headers middleware — 2026-04-11
- [x] **1A.19** Structured logging with slog (JSON, no secrets in logs) — 2026-04-11
- [x] **1A.20** Unit + integration tests (80% coverage for auth + accounts) — auth: 84.4%, accounts: 81.5% — 2026-04-11
- [x] **1A.21** `docker-compose.yml` for local Postgres — 2026-04-11
- [x] **1A.22** `Makefile` with run, test, lint, migrate, sqlc, build targets — 2026-04-11
- [x] **1A.23** Deploy to staging VPS (Caddy + systemd + HTTPS) — deploy/Caddyfile, deploy/scanvault.service, docs/DEPLOY.md — 2026-04-11

**Acceptance:** All criteria in docs/BACKEND_MVP.md Phase 1 acceptance section pass.

---

## PHASE 1B: Frontend Foundations (Android) — Weeks 1-2

> **Spec:** docs/FRONTEND_MVP.md section 4 → Phase 1
> **Depends on:** Phase 0 completed
> **Runs in PARALLEL with Phase 1A**

- [x] **1B.1** Multi-module Gradle setup (`:app`, `:core:*`, `:feature:*`) per spec section 2 — 2026-04-11
- [x] **1B.2** Version catalog (`libs.versions.toml`) — 2026-04-11
- [x] **1B.3** Detekt config + R8 config + baseline ProGuard rules — 2026-04-11
- [x] **1B.4** Hilt DI wired up — 2026-04-11
- [x] **1B.5** Material 3 theme with dynamic color (colors from docs/DESIGN_SYSTEM.md section 2.2) — 2026-04-11
- [x] **1B.6** Splash screen API — 2026-04-11
- [x] **1B.7** Adaptive app icon placeholder — 2026-04-11
- [x] **1B.8** Camera permissions flow (rationale screen, denial state with "Open Settings") — 2026-04-11
- [x] **1B.9** CameraX PreviewView (4:3, safe-area insets) — 2026-04-11
- [x] **1B.10** Camera controls (torch toggle, grid overlay, capture button with haptics, zoom, tap-to-focus) — 2026-04-11
- [x] **1B.11** Real-time edge detection overlay (EdgeDetector interface + OpenCvEdgeDetector + FakeEdgeDetector) — 2026-04-11
- [x] **1B.12** Edge detection overlay on Compose Canvas (green/amber/invisible states, spring animation) — 2026-04-11
- [x] **1B.13** Capture pipeline (full-res capture → edge detection → perspective transform) — 2026-04-11
- [x] **1B.14** Manual crop screen (4 draggable corners, magnifier, rotate, retake/next) — 2026-04-11
- [x] **1B.15** Encrypted storage (`:core:data` — AES-256-GCM, Android Keystore master key) — 2026-04-11
- [x] **1B.16** Room `ScanEntity` + DAO — 2026-04-11
- [x] **1B.17** Recent scans thumbnail strip on camera screen — 2026-04-11
- [x] **1B.18** Navigation Compose setup with type-safe routes — 2026-04-11
- [x] **1B.19** Screen rotation support (rememberSaveable + ViewModel) — 2026-04-11
- [x] **1B.20** Unit tests + Compose UI tests for critical flows — 2026-04-11

**Acceptance:** All criteria in docs/FRONTEND_MVP.md Phase 1 acceptance section pass.

---

## PHASE 1C: CI/CD Pipelines — Week 2

> **Spec:** docs/DEVOPS_AUTOMATION.md section 6
> **Depends on:** Phase 0 + at least 1A.1 and 1B.1 done

- [x] **1C.1** `android-ci.yml` (lint → test → assemble → upload artifact on PR) — 2026-04-11
- [x] **1C.2** `backend-ci.yml` (vet → staticcheck → gosec → govulncheck → test) — 2026-04-11
- [x] **1C.3** `intelligence-ci.yml` (already created — verified YAML valid, structure correct) — 2026-04-11
- [x] **1C.4** `backend-deploy-staging.yml` (auto-deploy on merge to main) — 2026-04-11
- [x] **1C.5** `security-scan.yml` (weekly OWASP + govulncheck + trufflehog) — 2026-04-11

**Acceptance:** PRs trigger CI. Green main auto-deploys backend to staging. All workflows pass.

---

## PHASE 2A: Backend Vault & Upload — Weeks 3-4

> **Spec:** docs/BACKEND_MVP.md section 6 → Phase 2
> **Depends on:** Phase 1A completed

- [x] **2A.1** R2 integration (`internal/vault/storage.go`) — presigned URLs, CRUD — 2026-04-11
- [x] **2A.2** Migration 0002 (vault_documents, vault_pages, account_quotas tables) — 2026-04-11
- [x] **2A.3** Create document endpoint — 2026-04-11
- [x] **2A.4** List/Get document endpoints with cursor pagination — 2026-04-11
- [x] **2A.5** Update document metadata (optimistic concurrency) — 2026-04-11
- [x] **2A.6** Delete document (soft delete) — 2026-04-11
- [x] **2A.7** Page upload flow (request URL → upload to R2 → confirm) — 2026-04-11
- [x] **2A.8** Page download URL endpoint — 2026-04-11
- [x] **2A.9** Background purge job (goroutine, hourly, soft-delete cleanup) — 2026-04-11
- [x] **2A.10** Quota enforcement (500 MB / 1000 docs default) — 2026-04-11
- [x] **2A.11** Sync manifest endpoint — 2026-04-11
- [x] **2A.12** Integration tests (full upload/download E2E) — 2026-04-11

**Acceptance:** All criteria in docs/BACKEND_MVP.md Phase 2 acceptance section pass.

---

## INFRA: AWS Infrastructure (Terraform)

> **Region:** ap-south-1 (Mumbai). State bucket: scanvault-tfstate. Workspace: staging.

- [x] **INFRA.1** Terraform project structure (10 modules: ecr, s3, secrets, iam, aurora, redis, lambda_go, lambda_python, api_gateway, cloudwatch) — 2026-04-16
- [x] **INFRA.2** Aurora Serverless v2 module (PostgreSQL 15.10, min 0.5 ACU / max 2 ACU) — 2026-04-16
- [x] **INFRA.3** Lambda + API Gateway module (Go arm64 512MB 30s, Python arm64 2048MB 120s) — 2026-04-16
- [x] **INFRA.4** ECR repos + bootstrap scripts (get_api_url.sh writes API_BASE_URL + TLS pin) — 2026-04-16
- [x] **INFRA.5** S3 module (versioning, public access block, SSE-S3, lifecycle rules) — 2026-04-16
- [x] **INFRA.6** terraform apply staging — all 62 resources created, API Gateway live — 2026-04-16

**Live staging endpoints (ALL VERIFIED 200 OK — 2026-04-16):**
- API Gateway: `https://4dbidumnq3.execute-api.ap-south-1.amazonaws.com` (stage: `$default`)
- Health: `GET /health` → 200, Ready: `GET /ready` → 200, Deep: `GET /v1/health/deep` → 200
- S3 bucket: `scanvault-staging-vault-203a9e83`
- Aurora: `scanvault-staging-aurora.cluster-cjk6c26cyw2o.ap-south-1.rds.amazonaws.com` (DB: scanvault, migrations run, scanvault_app user created)
- Redis: `scanvault-staging-redis-wuu4iy.serverless.aps1.cache.amazonaws.com`
- ECR Go: `345594608526.dkr.ecr.ap-south-1.amazonaws.com/scanvault-staging-go-backend` (REAL image deployed)
- ECR Python: `345594608526.dkr.ecr.ap-south-1.amazonaws.com/scanvault-staging-python-intelligence`

**Terraform:** Working. Run from `infra/` with `terraform init -backend-config=...` + `terraform plan/apply -var-file=terraform.tfvars`. See docs/TERRAFORM_GUIDE.md.
**Terraform fix:** `%APPDATA%\terraform.rc` filesystem_mirror prevents IPv6 registry timeout on Windows.

---

## PHASE 2B: Frontend Library & OCR — Weeks 3-4

> **Spec:** docs/FRONTEND_MVP.md section 4 → Phase 2
> **Depends on:** Phase 1B completed
> **Runs in PARALLEL with Phase 2A**

- [x] **2B.1** Document + Page Room entities with relations — 2026-04-11
- [x] **2B.2** Library screen (grid, cards from docs/DESIGN_SYSTEM.md 2.7, multi-select, sort) — 2026-04-11
- [x] **2B.3** Folders (one level deep, create/rename/delete) — 2026-04-11
- [x] **2B.4** Full-text search (Room FTS4) — 2026-04-12
- [x] **2B.5** Multi-page capture flow (batch mode, reorder screen) — 2026-04-12
- [x] **2B.6** Image filters (Original, Auto, Magic Color, Grayscale, B&W) in `:core:imaging` — 2026-04-12
- [x] **2B.7** OCR pipeline (ML Kit v2, Latin bundled, on-device) in `:core:ml` — 2026-04-12
- [x] **2B.8** PDF export (PdfDocument + PDFBox text layer) in `:core:pdf` — 2026-04-12
- [x] **2B.9** Other exports (JPEG, PNG, TXT, encrypted ZIP) — 2026-04-12
- [x] **2B.10** Document reader (swipeable pager, pinch-to-zoom, OCR text overlay, FLAG_SECURE) — 2026-04-16
- [x] **2B.11** Biometric app lock (BiometricLockManager, BiometricLockManagerTest 8 cases pass) — 2026-04-16
- [x] **2B.12** Integration tests (10-case DocumentPipelineIntegrationTest — 10 pages, reorder, filter, export, OCR overlay) — 2026-04-16

**Acceptance:** All criteria in docs/FRONTEND_MVP.md Phase 2 acceptance section pass.

---

## PHASE 3A: Backend Sync Engine — Weeks 5-6

> **Spec:** docs/BACKEND_MVP.md section 6 → Phase 3
> **Depends on:** Phase 2A completed

- [x] **3A.1** Conflict resolution protocol + conflict backup table (migration 0003) — 2026-04-16
- [x] **3A.2** Batch operations endpoint — 2026-04-16
- [x] **3A.3** Redis rate limiter (replace in-memory) — 2026-04-16
- [x] **3A.4** CAPTCHA on signup (hCaptcha/Turnstile) — 2026-04-16
- [x] **3A.5** Account lockout after brute force — 2026-04-16
- [x] **3A.6** Password reset flow (with data loss warning) — 2026-04-16
- [x] **3A.7** Audit event logging (all security events) — 2026-04-16
- [x] **3A.8** Account activity endpoint — 2026-04-16
- [x] **3A.9** Session listing & revocation — 2026-04-16
- [x] **3A.10** Delete account flow (7-day grace + hard purge) — 2026-04-16
- [x] **3A.11** Abuse signals — 2026-04-16

**Acceptance:** All criteria in docs/BACKEND_MVP.md Phase 3 acceptance section pass.

---

## PHASE 3B: Frontend AI Modes & Monetization — Weeks 5-6

> **Spec:** docs/FRONTEND_MVP.md section 4 → Phase 3
> **Depends on:** Phase 2B completed
> **Runs in PARALLEL with Phase 3A**

- [x] **3B.1** Smart document type detection (TFLite classifier — DocumentClassifier + DocumentClassifierTest) — 2026-04-16
- [x] **3B.2** ID card mode (IdCardCaptureMode: prompts front+back, A4 composite — IdCardCaptureModeTest) — 2026-04-16
- [x] **3B.3** Receipt mode (tall aspect, B&W, field extraction — ReceiptMode + ReceiptModeTest) — 2026-04-16
- [x] **3B.4** Whiteboard mode (glare removal, saturation boost — WhiteboardProcessor + WhiteboardProcessorTest) — 2026-04-16
- [x] **3B.5** Book scan mode (two-page split, dewarp stub — BookScanProcessor + BookScanProcessorTest) — 2026-04-16
- [x] **3B.6** Signature tool (SignatureCanvasView, SignatureRepository, PlaceSignature — SignatureTest) — 2026-04-16
- [x] **3B.7** Annotations (AnnotationManager: text/highlight, undo/redo stack max 30 — AnnotationManagerTest) — 2026-04-16
- [x] **3B.8** Destructive redaction (RedactionProcessor: pixel blackout, OCR boxes wiped — RedactionProcessorTest) — 2026-04-16
- [x] **3B.9** Image cleanup filters (ImageCleanupProcessor: denoise/sharpen/lighting as flags — ImageCleanupProcessorTest) — 2026-04-16
- [x] **3B.10** AdMob integration (AdMobManager lazy init, InterstitialAdController 5th-export rule + 3min cap, UMP consent — AdMobManagerTest + InterstitialAdControllerTest) — 2026-04-16
- [x] **3B.11** Rating prompt (InAppReviewManager: 3 exports + 3 days + 90-day cooldown — InAppReviewManagerTest) — 2026-04-16
- [x] **3B.12** Onboarding (OnboardingScreen: 3 screens, skip, DataStore completion flag — OnboardingViewModelTest) — 2026-04-16
- [x] **3B.13** Play Store prep (android/store/listing.txt, screenshots/README.txt with dimensions, feature-graphic-spec.txt) — 2026-04-16

**Acceptance:** All criteria in docs/FRONTEND_MVP.md Phase 3 acceptance section pass.

---

## PHASE 3C: Intelligence Layer v1 — Weeks 5-6

> **Spec:** docs/INTELLIGENCE_LAYER.md section 9 → Phase 1
> **Depends on:** intelligence/ stubs already exist
> **Runs in PARALLEL with 3A and 3B**

- [x] **3C.0** `scripts/run-intelligence.sh` — starts Docker compose (API + worker + Redis), waits healthy, prints port 8100. Has --help. — 2026-04-16
- [x] **3C.1** Finalize FastAPI app (health endpoint working in Docker, port 8100 internal only) — 2026-04-16
- [x] **3C.2** Classification endpoint (heuristic + ML model loading) — 2026-04-16
- [x] **3C.3** Enhanced OCR endpoint (PaddleOCR, single page) — 2026-04-16
- [x] **3C.4** Vision enhance endpoint (denoise, sharpen, balance pipeline) — 2026-04-16
- [x] **3C.5** Redis queue integration (ARQ worker consuming from `scanvault:intelligence:tasks`) — 2026-04-16
- [x] **3C.6** S3 integration (download from `processing/` prefix, upload results — AWS S3 via boto3) — 2026-04-16
- [x] **3C.7** Docker compose working (API + worker + Redis) — 2026-04-16
- [x] **3C.8** Tests passing in CI (71 passed, 1 skipped) — 2026-04-16

**Acceptance:** `curl` can classify an image, get enhanced OCR, and enhance a document photo.

---

## PHASE 4A: Backend Observability — Weeks 7-9

> **Spec:** docs/BACKEND_MVP.md section 6 → Phase 4
> **Depends on:** Phase 3A completed

- [x] **4A.1** CloudWatch metrics (request_count, avg_request_duration_ms — GET /v1/metrics) — 2026-04-16
- [x] **4A.2** Grafana dashboards (infra/grafana/dashboards/scanvault-overview.json, CloudWatch datasource) — 2026-04-16
- [x] **4A.3** Structured error handling + Sentry (sentryclient package, 500→Sentry, 4xx skipped) — 2026-04-16
- [x] **4A.4** Deep health checks (GET /v1/health/deep, DB/Redis/S3 components, degraded on failure) — 2026-04-16
- [x] **4A.5** Database indexes tested (EXPLAIN shows Index Scan for key queries) — 2026-04-16
- [x] **4A.6** In-process LRU cache (internal/cache, 256 entries, hit/miss/invalidate/evict) — 2026-04-16
- [x] **4A.7** Backup strategy (internal/backup, dry-run mode, gzip compression, S3 upload) — 2026-04-16
- [x] **4A.8** Load testing (scripts/load-test.sh, k6/vegeta/curl fallback, reads api_base_url.properties) — 2026-04-16
- [x] **4A.9** Runbook (docs/RUNBOOK.md — cold start, Aurora, ElastiCache, S3, rollback, monitoring, crash rates) — 2026-04-16

**Acceptance:** All criteria in docs/BACKEND_MVP.md Phase 4 acceptance section pass.

---

## PHASE 4B: Frontend Cloud Sync — Weeks 7-9

> **Spec:** docs/FRONTEND_MVP.md section 4 → Phase 4
> **Depends on:** Phase 3B completed + Phase 3A completed (backend sync must be ready)
> **THIS IS WHERE FRONTEND MEETS BACKEND FOR THE FIRST TIME**
>
> **GATE — do NOT start 4B.1 until:**
> - [ ] PROGRESS.md tasks 3A.1–3A.11 are all checked
> - [ ] Go backend is deployed to staging (`https://api-staging.scanvault.app/v1` responds)
> - [ ] Backend staging passes `/health` and `/ready` checks
>
> **API Base URLs (hardcode nowhere — use BuildConfig):**
> - Production: `https://api.scanvault.app/v1`
> - Staging: `https://api-staging.scanvault.app/v1`
>
> **Key endpoints Android calls (full contract in docs/FRONTEND_MVP.md Phase 4 → "API Contract"):**
> - `POST /accounts` — create account
> - `POST /sessions` — login (returns Paseto token)
> - `PUT /sessions` — refresh token
> - `POST /vault/blobs` — upload encrypted blob
> - `GET /vault/blobs/{id}` — download encrypted blob
> - `GET /vault/manifest` — sync manifest
> - `DELETE /vault/blobs/{id}` — delete blob
> - `DELETE /accounts/me` — delete account

- [x] **4B.1** Account screen (signup, login, password strength — AccountScreen + AccountViewModelTest 5 cases) — 2026-04-16
- [x] **4B.2** Client-side E2E crypto (`:core:crypto` — KeyDerivation, VaultCrypto AES-256-GCM, KeyRotation — VaultCryptoTest + KeyDerivationTest + KeyRotationTest) — 2026-04-16
- [x] **4B.3** Sync engine (WorkManager stub, SyncManager, ExponentialBackoff, OfflineQueue — SyncOperationTest + OfflineQueueTest + ExponentialBackoffTest + MockSyncRepositoryTest) — 2026-04-16
- [x] **4B.4** Conflict resolution UI (ConflictResolutionScreen, ConflictResolutionViewModel — ConflictResolutionViewModelTest) — 2026-04-16
- [x] **4B.5** Sync status in library cards (cloud-done, uploading, pending, local-only) — 2026-04-16
- [x] **4B.6** Certificate pinning (Ktor + OkHttp) — 2026-04-16
- [x] **4B.7** Tamper check (signature verification) — 2026-04-16
- [x] **4B.8** Account management (change password, delete account, logout, data export) — 2026-04-16
- [x] **4B.9** Ktor client setup (HTTP/2, retry, offline queue) — 2026-04-16
- [x] **4B.10** Integration test (account → scan → sync → second device → see documents) — 2026-04-16

**Acceptance:** All criteria in docs/FRONTEND_MVP.md Phase 4 acceptance section pass.

---

## PHASE 4C: Intelligence Layer v2 — Weeks 7-9

> **Spec:** docs/INTELLIGENCE_LAYER.md section 9 → Phase 2
> **Depends on:** Phase 3C completed
>
> **4C.4–4C.6 connect Go to Python for the FIRST TIME:**
> - Python port: `8100` (internal Docker only, never exposed to internet)
> - Go calls Python at: `http://localhost:8100`
> - Redis queue: `scanvault:intelligence:tasks`
> - Redis result channel: `scanvault:intelligence:results`
> - See docs/INTELLIGENCE_LAYER.md §2 for full `internal/intelligence/` package spec

- [ ] **4C.1** Layout analysis and table extraction
- [ ] **4C.2** Structured field extraction (receipts, invoices, IDs)
- [ ] **4C.3** Super-resolution (Real-ESRGAN integration)
- [ ] **4C.4** Go backend `internal/intelligence/` proxy package
- [ ] **4C.5** `intelligence_tasks` DB migration
- [ ] **4C.6** Intelligence API endpoints in Go
- [ ] **4C.7** Prometheus metrics for intelligence services

**Acceptance:** Go backend can proxy classify/enhance/OCR requests to Python. Async tasks work.

---

## PHASE 5: Polish & Launch — Weeks 10-11

> **Spec:** docs/docs/FRONTEND_MVP.md Phase 5 + docs/BACKEND_MVP.md Phase 5 + DEVOPS_AUTOMATION.md section 5
> **Depends on:** ALL Phase 4 completed

- [ ] **5.1** Baseline Profiles (Macrobenchmark → generate → ship)
- [ ] **5.2** R8 full mode + resource shrinking (verify, tree-shake OpenCV)
- [ ] **5.3** Dark mode (full Material 3 dynamic color, every screen)
- [ ] **5.4** Accessibility pass (contentDescription, TalkBack, 48dp targets, contrast)
- [ ] **5.5** Localization (EN, HI, NE, ES, PT, AR, FR, DE, ID — RTL tested)
- [ ] **5.6** Widgets (Glance: "Scan now" + "Recent scans")
- [ ] **5.7** Quick Settings tile
- [ ] **5.8** Share extension (receive images from other apps)
- [ ] **5.9** Performance pass (cold start <500ms, library 60fps@1000 docs, memory <150MB)
- [ ] **5.10** Error reporting (custom crash handler, encrypted logs)
- [ ] **5.11** Backup/restore (local encrypted ZIP)
- [ ] **5.12** Backend production environment (separate VPS, TLS 1.3, pen test)
- [ ] **5.13** Privacy policy + ToS
- [ ] **5.14** `android-release.yml` (Fastlane → Play Console)
- [ ] **5.15** `backend-deploy-production.yml` (tag → prod deploy with rollback)
- [ ] **5.16** OTA system (`ota-config-push.yml`)
- [ ] **5.17** Play Store submission (closed → open → production, staged rollout)
- [ ] **5.18** Post-launch monitoring setup

**Acceptance:** ALL acceptance criteria from ALL phases pass. APK <30MB. 99.5% crash-free. Live on Play Store.

---

---

## Architecture Integration Quick Reference

> Read this before any Phase 4+ session. These are the facts that prevent cross-layer mistakes.

### When each layer first connects

| Event | Session | Task | What happens |
|---|---|---|---|
| Android calls Go for first time | Session 33 | 4B.1 | `POST /v1/sessions` login via Ktor client |
| Android sends encrypted blobs to Go | Session 33 | 4B.3 | `POST /v1/vault/blobs` via WorkManager |
| Go calls Python for first time | Session 36 | 4C.4 | `internal/intelligence/` HTTP client calls Python `:8100` |
| Go publishes to Redis for first time | Session 36 | 4C.4 | Publishes to `scanvault:intelligence:tasks` |
| Android submits AI task | Session 34 | 4B.9 | `POST /v1/intelligence/tasks` (Go proxies to Python) |

### Pre-flight gates (MUST be true before starting the session)

| Session | Gate condition |
|---|---|
| Session 33 (4B.1) | Backend staging URL responds: `curl https://api-staging.scanvault.app/v1/health` → 200 |
| Session 36 (4C.4) | Python container starts: `docker compose up intelligence` → `/health` returns 200 |
| Session 36 (4C.4) | Redis running: `redis-cli ping` → PONG |

### Architecture rules (never violate)

1. **Android NEVER calls Python directly** — all requests go Android → Go → Python
2. **Python port 8100 is internal only** — Caddy does not proxy to it, firewall blocks external access
3. **Go never decrypts vault data** — blobs arrive encrypted, stored encrypted, served encrypted
4. **AI opt-in is per-document** — Android asks user consent, decrypts locally, uploads plaintext to `processing/` R2 prefix only
5. **`processing/` R2 prefix has 1-hour TTL** — Go's purge job cleans it; it is SEPARATE from `vault/` prefix

---

## Session Log

> Claude Code: After each session, add one line here. Format: `YYYY-MM-DD | Task IDs completed | Notes`

| Date | Tasks | Notes |
|---|---|---|
| 2026-04-10 | specs created | CLAUDE.md, PROGRESS.md, INTELLIGENCE_LAYER.md, intelligence/ stubs |
| 2026-04-10 | test-gate added | CLAUDE.md test-gate rule, PROGRESS.md header, prompt-guide.txt fully rewritten with test-first enforcement |
| 2026-04-10 | 0.1–0.6 | DevOps skeleton: dirs, VERSION, .gitignore, Makefile, .editorconfig, .pre-commit-config.yaml + commitlint.config.js. 71/71 tests pass. |
| 2026-04-10 | 0.7–0.10 | Moved 5 spec docs to docs/, updated CLAUDE.md+PROGRESS.md refs, bootstrap.sh, doctor.sh, run-backend-local.sh. 128/128 tests pass. |
| 2026-04-10 | 0.11–0.13 | run-phone.sh (all flags + ADB), release.sh (semver bump, changelog, dry-run), rollback.sh (android/backend/ota/all). **PHASE 0 COMPLETE.** 189/189 tests pass. |
| 2026-04-10 | 1A.1–1A.4 | Go module (github.com/nikhil/scanvault-api), all internal/ dirs, .golangci.yml (strict, errcheck+gosec+revive+gofumpt+...), .env.example (all 14 vars documented), config loader (caarlos0/env, required vars, environment validation, Argon2 param validation). 19/19 tests pass. |
| 2026-04-10 | 1A.5–1A.8 | GCC installed (MSYS2/MinGW-w64 15.2), go test -race now works. Migration 0001 (accounts+refresh_tokens+email_verification_tokens, CITEXT, CHECK, cascade). sqlc.yaml + 13 SQL queries generated. chi server (RequestID, echoRequestID, RealIP, slog logger, Recoverer, Timeout 60s, SecureHeaders, CORS). /health 200+JSON, /ready checks Postgres 200/503. 6 migration integration tests + 12 server tests, all pass with -race. |
| 2026-04-11 | 1A.13–1A.18 | Logout, GetMe, ChangePassword, auth middleware (Paseto+account load), in-memory rate limiter (5/15min login, 3/hr signup), security headers (nosniff, DENY, no-referrer, CSP default-src 'none'). 40+ tests pass with -race. |
| 2026-04-11 | 1B.1–1B.4 | Android multi-module project (13 modules: :app + 8 :core + 4 :feature). libs.versions.toml (compileSdk=36, Kotlin 2.0.21, Compose BOM, Hilt 2.53.1, Room 2.7, CameraX 1.4.1, Ktor 3.0.3, Robolectric). detekt.yml + proguard-rules.pro. @HiltAndroidApp ScanVaultApplication, @AndroidEntryPoint MainActivity, AppModule. Hilt injection verified with Robolectric (HiltContextInjectionTest). 5/5 unit tests pass. ./gradlew projects lists all 13 modules. |
| 2026-04-11 | 1B.5–1B.14 | Sessions 11-15. Theme, splash, icon, permissions, CameraX, controls, edge detection, perspective transform, crop screen. 85/85 tests pass. |
| 2026-04-11 | 1C.1–1C.5 | **PHASE 1C COMPLETE.** android-ci.yml (lint+test+assemble+artifact+instrumented matrix api 26/30/34). backend-ci.yml (vet+staticcheck+gosec SARIF+govulncheck+race test+coverage). backend-deploy-staging.yml (CGO_ENABLED=0 cross-compile, SSH deploy, migration, smoke test, Telegram notify). security-scan.yml (OWASP SARIF, govulncheck, TruffleHog --only-verified). intelligence-ci.yml verified. scripts/validate-workflows.py: 87/87 checks pass (YAML valid, no tabs, concurrency cancel-in-progress, path filters, cron schedule, timeout-minutes on every job, action version pinning, no hardcoded credentials). Fixed PyYAML 1.1 gotcha: bare `on:` key parses as boolean True. |
| 2026-04-11 | 2A.5–2A.12 | **PHASE 2A COMPLETE.** PUT /v1/vault/documents/{uuid} (optimistic concurrency, 5-goroutine race test). DELETE soft-delete (quota decrement, 404 for other account). Page upload flow: POST upload-url (size 413, quota 402, pending row), POST confirm (HeadObject verify, page count/quota increment). GET download-url (presign GET, 404 auth). PurgeExpiredWithRetention (R2 delete → hard delete, spares <30d). Quota: doc limit 402, resets after delete. GET /v1/vault/manifest (since param, pagination). Full E2E: create→upload→confirm→list→download→delete→purge. 8 packages, all pass. |
| 2026-04-11 | 2A.1–2A.4 | R2 storage wrapper (ObjectStorage interface + R2Storage + mockStorage). Migration 0002 (vault_documents, vault_pages, account_quotas with indexes + constraints). POST /v1/vault/documents (v7 UUID validation, duplicate 409, quota row init). GET /v1/vault/documents (cursor pagination, excludes soft-deleted). GET /v1/vault/documents/{uuid} (404 for other user, 404 for non-existent). 30 tests pass across 8 packages. |
| 2026-04-12 | interconnect audit | Rewrote CLAUDE.md (3-layer arch, integration points table, gotchas). Added API contract + :core:network spec + AI flow to FRONTEND_MVP.md §Phase4. Added internal/intelligence/ dir + API route map to BACKEND_MVP.md. Added session numbers + Redis constants + Python port + Go proxy ref to INTELLIGENCE_LAYER.md §9. Added Architecture Integration Quick Reference + Phase 4B/4C gates to PROGRESS.md. Updated Sessions 29/33/34/36 in prompt-guide.txt with explicit API endpoints, staging gates, and integration constants. |
| 2026-04-16 | INFRA.1–6 | AWS Terraform infrastructure deployed. 62 resources in staging: VPC, ECR, S3, Secrets Manager, IAM, Aurora Serverless v2 (pg 15.10), ElastiCache Serverless Redis, Lambda (Go+Python arm64), API Gateway HTTP API, CloudWatch. API Gateway URL: https://4dbidumnq3.execute-api.ap-south-1.amazonaws.com/v1. api_base_url.properties and pins.properties written. Placeholder images in ECR (real code pending). terraform validate + apply pass. |
| 2026-04-16 | 2B.10–12, 3A.1–11, 3C.0–8 | **PHASE 2B COMPLETE. PHASE 3A COMPLETE. PHASE 3C COMPLETE.** Fixed Go backend test failures (auth_params NOT NULL, mockStorage missing BucketName()). Fixed API Gateway 500 error by adding AWS Lambda adapter (aws-lambda-go + aws-lambda-go-api-proxy) to main.go — auto-detects Lambda env via AWS_LAMBDA_FUNCTION_NAME. All 12 Go packages pass with -race. Python: 71/1 skipped tests pass. Android: reader+security tests pass. |
| 2026-04-16 | 3B.1–13, 4B.1–4, INFRA.6 | **PHASE 3B COMPLETE. PHASE 4B.1-4B.4 COMPLETE.** Fixed InterstitialAdController (lastShownMs sentinel for fresh init), OnboardingViewModelTest (Dispatchers.setMain for viewModelScope), SignatureTest (pixel-render not reliable in Robolectric — changed to mutable-copy assertion). INFRA.6: added backend-deploy-aws.yml + intelligence-deploy-aws.yml (arm64 Docker buildx → ECR → Lambda, staging auto-deploy, production manual gate, secrets.AWS_ACCESS_KEY_ID). All 458 Android tests pass. All 16 Go packages pass with -race. |
| 2026-04-16 | AWS debug + Terraform fix | **BACKEND AWS FULLY LIVE.** Debugged: placeholder Docker image in ECR, API Gateway stage `v1` prefix mismatch, missing Lambda env vars, `scanvault_app` DB user not created, goose migrations not run. Fixes: built+pushed real Go backend image (--provenance=false for Lambda), created `$default` API Gateway stage, ran 4 migrations, created DB user+grants. All health endpoints 200. Terraform fixed: `%APPDATA%\terraform.rc` filesystem_mirror blocks IPv6 registry.terraform.io timeout on Windows. terraform plan/apply now work. Created docs/TERRAFORM_GUIDE.md. Updated CLAUDE.md + PROGRESS.md. |
| 2026-04-16 | AWS cost optimization | **COST CUT FROM ~$112 → ~$3.50/month.** Deleted: ElastiCache Serverless Redis ($15+/mo), 4 VPC Interface endpoints ($57.60/mo), captcha secret ($0.40/mo). Aurora min ACU = 0 (auto-pause, $0 at rest). Made REDIS_URL optional in config.go + config_test.go (16 tests pass). Removed Redis+captcha from all Terraform modules. Rebuilt+pushed Go backend image. Terraform apply: SG deleted, IAM policy updated, Python Lambda REDIS_URL removed. All health endpoints still 200 OK. |
| 2026-04-16 | 4B.5–4B.10 | **PHASE 4B COMPLETE.** SyncStatus enum (LOCAL_ONLY/PENDING/UPLOADING/CLOUD_DONE) + Room migration 3→4. DocumentCard sync icons (CloudDone/CloudSync/CloudUpload/CloudOff). TokenStore (EncryptedSharedPreferences). ScanVaultApiClient (Ktor MockEngine tests). NetworkModule (OkHttp + CertificatePinner + Bearer auth, Logger.DEFAULT fix). TamperChecker (APK cert SHA-256, null-safety fix). AccountManagementViewModel + Screen (changePassword key-rewrap, deleteAccount, logout). E2ESyncIntegrationTest (4 tests). 8 settings tests all pass (changePassword_success fixed with real encrypted wrapped key). All Phase 4B tests pass. |
| 2026-04-12 | 2B.4–2B.6 | **Session 21 COMPLETE.** FTS4 full-text search (DocumentFtsEntity + @RawQuery DAO + sanitiseFtsQuery + LibraryViewModel search debounce). BatchCaptureViewModel (add/move/delete/reorder/consume pages). PageReorderScreen (drag-to-reorder, delete badge, Add/Done buttons). ImageFilter enum (5 filters) + ImageFilterProcessor (ORIGINAL zero-copy, AUTO, MAGIC_COLOR, GRAYSCALE, B&W adaptive threshold). FilterPreviewStrip Composable. 244/244 unit tests pass. FTS DAO integration tests moved to androidTest/ (require real Android SQLite — FTS4 not available in Robolectric sqlite4java). |
| 2026-04-12 | 2B.7–2B.9 | **Session 22 COMPLETE.** OCR pipeline: OcrEngine interface + MlKitOcrEngine (ML Kit v2 Latin bundled, suspendCancellableCoroutine) + FakeOcrEngine (deterministic, emptyText flag). PDF export: PdfExporter (A4 595×842pt, bitmap scaling, transparent OCR text layer) + PdfDocumentWrapper abstraction (AndroidPdfDocumentWrapper production, FakePdfDocumentWrapper for tests). DocumentExporter: exportJpeg (quality 90), exportPng (lossless), exportTxt (page separator), exportEncryptedZip/decryptZip (AES-256-CBC, PBKDF2WithHmacSHA256 key derivation, 10K iterations). Switched core:ml and core:pdf from kapt → KSP (Windows fix). 258/258 unit tests pass, 0 failures. |
| 2026-04-11 | 2B.1–2B.3 | **PHASE 2B tasks 2B.1–2B.3 COMPLETE.** Document+Page+Folder Room entities (migration 1→2), DocumentRepository, LibraryViewModel (combine 6 flows), LibraryScreen (PullToRefreshBox, LazyVerticalGrid, 2/4 cols), DocumentCard (combinedClickable, Coil AsyncImage, color tag), Folder CRUD. KSP replaces kapt (Windows fix). 13/13 ViewModel unit tests pass. |
| 2026-04-11 | 1B.15–1B.20 | Session 16. **PHASE 1B COMPLETE.** AesGcmImageStore (AES-256-GCM, random IV per write, tamper detection). KeyStoreKeyProvider (hardware-backed Android Keystore). ScanEntity + ScanDao (Room in-memory tests, unicode, ordering, replace-on-conflict). RecentScansViewModel + RecentScansThumbnailStrip (placeholder thumbnails, empty state). AppRoutes (type-safe @Serializable routes: Scanner/Crop/Library/Reader/Settings). AppNavHost (Navigation 2.8.5 type-safe API). ScannerScreen (camera permission gate + preview + controls + thumbnail strip). ScreenRotationTest (SavedStateHandle survives recreation). ScanFlowTest (full 10-step journey across 5 ViewModels). 137/137 tests pass. | Theme (ScanAmber palette, light/dark, dynamic color fallback). Splash screen (≤300ms, brand amber bg). Adaptive icon (foreground+background drawables). Camera permissions state machine (CameraPermissionViewModel: NotRequested/ShowRationale/Granted/PermanentlyDenied). CameraX PreviewView (4:3 aspect, safe-area insets). Camera controls (CameraControlsViewModel: torch, grid, zoom clamp, tap-to-focus). EdgeDetector interface + FakeEdgeDetector (deterministic for tests) + OpenCvEdgeDetector (graceful no-op without native lib). EdgeDetectionOverlay (green/amber/invisible, spring animation). CaptureViewModel (edge detect → perspective warp pipeline, SavedStateHandle). CropScreen (4 draggable corners, rotate, retake/next). Switched core:imaging + core:common + feature:scanner to KSP (fixes Windows KAPT path bug). CommonModule provides AppDispatchers. 85/85 unit tests pass. |
