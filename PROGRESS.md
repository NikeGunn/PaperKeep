# ScanVault — Build Progress

> **Claude Code:** Read this file after CLAUDE.md. Find the first unchecked `[ ]` task. That's your job.
> **TEST-GATE:** For EVERY task: BUILD → write TESTS → RUN tests → all PASS → ONLY THEN check the box. See CLAUDE.md for test requirements per task type.

## Current Sprint: WEEK 1 — Foundation

**Status:** IN PROGRESS
**Last session:** 2026-04-10
**Last completed task:** 0.13

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

- [ ] **1A.1** Go module init (`backend/go.mod`), directory structure per spec section 2
- [ ] **1A.2** `.golangci.yml` with strict config
- [ ] **1A.3** `backend/.env.example` with all required vars documented
- [ ] **1A.4** Config loader (`internal/config/`) — env vars via caarlos0/env
- [ ] **1A.5** Postgres setup — migration 0001 (accounts, refresh_tokens, email_verification_tokens)
- [ ] **1A.6** sqlc config + initial queries for accounts
- [ ] **1A.7** HTTP server with chi router + middleware chain (RequestID, Logger, Recoverer, Timeout, SecureHeaders, CORS)
- [ ] **1A.8** `/health` and `/ready` endpoints
- [ ] **1A.9** Account creation (`POST /v1/accounts`) with double-hashing
- [ ] **1A.10** Email verification (`GET /v1/accounts/verify`)
- [ ] **1A.11** Login (`POST /v1/sessions`) — Paseto v4, constant-time, dummy hash for non-existent users
- [ ] **1A.12** Token refresh (`POST /v1/sessions/refresh`) with rotation + replay detection
- [ ] **1A.13** Logout (`DELETE /v1/sessions`)
- [ ] **1A.14** Get account (`GET /v1/accounts/me`)
- [ ] **1A.15** Change password (`POST /v1/accounts/me/password`)
- [ ] **1A.16** Auth middleware (Paseto verification, account loading)
- [ ] **1A.17** Rate limiting (in-memory for Phase 1)
- [ ] **1A.18** Security headers middleware
- [ ] **1A.19** Structured logging with slog (JSON, no secrets in logs)
- [ ] **1A.20** Unit + integration tests (80% coverage for auth + accounts)
- [ ] **1A.21** `docker-compose.yml` for local Postgres
- [ ] **1A.22** `Makefile` with run, test, lint, migrate, sqlc, build targets
- [ ] **1A.23** Deploy to staging VPS (Caddy + systemd + HTTPS)

**Acceptance:** All criteria in docs/BACKEND_MVP.md Phase 1 acceptance section pass.

---

## PHASE 1B: Frontend Foundations (Android) — Weeks 1-2

> **Spec:** docs/FRONTEND_MVP.md section 4 → Phase 1
> **Depends on:** Phase 0 completed
> **Runs in PARALLEL with Phase 1A**

- [ ] **1B.1** Multi-module Gradle setup (`:app`, `:core:*`, `:feature:*`) per spec section 2
- [ ] **1B.2** Version catalog (`libs.versions.toml`)
- [ ] **1B.3** Detekt config + R8 config + baseline ProGuard rules
- [ ] **1B.4** Hilt DI wired up
- [ ] **1B.5** Material 3 theme with dynamic color (colors from docs/DESIGN_SYSTEM.md section 2.2)
- [ ] **1B.6** Splash screen API
- [ ] **1B.7** Adaptive app icon placeholder
- [ ] **1B.8** Camera permissions flow (rationale screen, denial state with "Open Settings")
- [ ] **1B.9** CameraX PreviewView (4:3, safe-area insets)
- [ ] **1B.10** Camera controls (torch toggle, grid overlay, capture button with haptics, zoom, tap-to-focus)
- [ ] **1B.11** Real-time edge detection overlay (OpenCV pipeline: grayscale→blur→Canny→contours→corners)
- [ ] **1B.12** Edge detection overlay on Compose Canvas (green/amber/invisible states, spring animation)
- [ ] **1B.13** Capture pipeline (full-res capture → edge detection → perspective transform)
- [ ] **1B.14** Manual crop screen (4 draggable corners, magnifier, rotate, retake/next)
- [ ] **1B.15** Encrypted storage (`:core:data` — AES-256-GCM, Android Keystore master key)
- [ ] **1B.16** Room `ScanEntity` + DAO
- [ ] **1B.17** Recent scans thumbnail strip on camera screen
- [ ] **1B.18** Navigation Compose setup with type-safe routes
- [ ] **1B.19** Screen rotation support (rememberSaveable + ViewModel)
- [ ] **1B.20** Unit tests + Compose UI tests for critical flows

**Acceptance:** All criteria in docs/FRONTEND_MVP.md Phase 1 acceptance section pass.

---

## PHASE 1C: CI/CD Pipelines — Week 2

> **Spec:** docs/DEVOPS_AUTOMATION.md section 6
> **Depends on:** Phase 0 + at least 1A.1 and 1B.1 done

- [ ] **1C.1** `android-ci.yml` (lint → test → assemble → upload artifact on PR)
- [ ] **1C.2** `backend-ci.yml` (vet → staticcheck → gosec → govulncheck → test)
- [ ] **1C.3** `intelligence-ci.yml` (already created — verify it works)
- [ ] **1C.4** `backend-deploy-staging.yml` (auto-deploy on merge to main)
- [ ] **1C.5** `security-scan.yml` (weekly OWASP + govulncheck + trufflehog)

**Acceptance:** PRs trigger CI. Green main auto-deploys backend to staging. All workflows pass.

---

## PHASE 2A: Backend Vault & Upload — Weeks 3-4

> **Spec:** docs/BACKEND_MVP.md section 6 → Phase 2
> **Depends on:** Phase 1A completed

- [ ] **2A.1** R2 integration (`internal/vault/storage.go`) — presigned URLs, CRUD
- [ ] **2A.2** Migration 0002 (vault_documents, vault_pages tables)
- [ ] **2A.3** Create document endpoint
- [ ] **2A.4** List/Get document endpoints with cursor pagination
- [ ] **2A.5** Update document metadata (optimistic concurrency)
- [ ] **2A.6** Delete document (soft delete)
- [ ] **2A.7** Page upload flow (request URL → upload to R2 → confirm)
- [ ] **2A.8** Page download URL endpoint
- [ ] **2A.9** Background purge job (goroutine, hourly, soft-delete cleanup)
- [ ] **2A.10** Quota enforcement (500 MB / 1000 docs default)
- [ ] **2A.11** Sync manifest endpoint
- [ ] **2A.12** Integration tests (full upload/download E2E)

**Acceptance:** All criteria in docs/BACKEND_MVP.md Phase 2 acceptance section pass.

---

## PHASE 2B: Frontend Library & OCR — Weeks 3-4

> **Spec:** docs/FRONTEND_MVP.md section 4 → Phase 2
> **Depends on:** Phase 1B completed
> **Runs in PARALLEL with Phase 2A**

- [ ] **2B.1** Document + Page Room entities with relations
- [ ] **2B.2** Library screen (grid, cards from docs/DESIGN_SYSTEM.md 2.7, multi-select, sort)
- [ ] **2B.3** Folders (one level deep, create/rename/delete)
- [ ] **2B.4** Full-text search (Room FTS4)
- [ ] **2B.5** Multi-page capture flow (batch mode, reorder screen)
- [ ] **2B.6** Image filters (Original, Auto, Magic Color, Grayscale, B&W) in `:core:imaging`
- [ ] **2B.7** OCR pipeline (ML Kit v2, Latin bundled, on-device) in `:core:ml`
- [ ] **2B.8** PDF export (PdfDocument + PDFBox text layer) in `:core:pdf`
- [ ] **2B.9** Other exports (JPEG, PNG, TXT, encrypted ZIP)
- [ ] **2B.10** Document reader (swipeable pager, pinch-to-zoom, OCR text overlay, FLAG_SECURE)
- [ ] **2B.11** Biometric app lock
- [ ] **2B.12** Integration tests (10-page scan → reorder → filter → export → share)

**Acceptance:** All criteria in docs/FRONTEND_MVP.md Phase 2 acceptance section pass.

---

## PHASE 3A: Backend Sync Engine — Weeks 5-6

> **Spec:** docs/BACKEND_MVP.md section 6 → Phase 3
> **Depends on:** Phase 2A completed

- [ ] **3A.1** Conflict resolution protocol + conflict backup table (migration 0003)
- [ ] **3A.2** Batch operations endpoint
- [ ] **3A.3** Redis rate limiter (replace in-memory)
- [ ] **3A.4** CAPTCHA on signup (hCaptcha/Turnstile)
- [ ] **3A.5** Account lockout after brute force
- [ ] **3A.6** Password reset flow (with data loss warning)
- [ ] **3A.7** Audit event logging (all security events)
- [ ] **3A.8** Account activity endpoint
- [ ] **3A.9** Session listing & revocation
- [ ] **3A.10** Delete account flow (7-day grace + hard purge)
- [ ] **3A.11** Abuse signals

**Acceptance:** All criteria in docs/BACKEND_MVP.md Phase 3 acceptance section pass.

---

## PHASE 3B: Frontend AI Modes & Monetization — Weeks 5-6

> **Spec:** docs/FRONTEND_MVP.md section 4 → Phase 3
> **Depends on:** Phase 2B completed
> **Runs in PARALLEL with Phase 3A**

- [ ] **3B.1** Smart document type detection (TFLite classifier)
- [ ] **3B.2** ID card mode (front + back → single A4)
- [ ] **3B.3** Receipt mode (tall aspect, B&W, field extraction)
- [ ] **3B.4** Whiteboard mode (glare removal, marker boost)
- [ ] **3B.5** Book scan mode (two-page split, DewarpNet)
- [ ] **3B.6** Signature tool (draw, save, place on PDF)
- [ ] **3B.7** Annotations (text, highlighter, redaction)
- [ ] **3B.8** Destructive redaction (pixels gone, OCR boxes wiped)
- [ ] **3B.9** Image cleanup filters (denoise, sharpen, fix lighting)
- [ ] **3B.10** AdMob integration (lazy init, UMP consent, placements per spec)
- [ ] **3B.11** Rating prompt (in-app review API with trigger conditions)
- [ ] **3B.12** Onboarding (3 screens, skippable, permission request)
- [ ] **3B.13** Play Store prep (listing copy, screenshots, feature graphic, privacy policy)

**Acceptance:** All criteria in docs/FRONTEND_MVP.md Phase 3 acceptance section pass.

---

## PHASE 3C: Intelligence Layer v1 — Weeks 5-6

> **Spec:** docs/INTELLIGENCE_LAYER.md section 9 → Phase 1
> **Depends on:** intelligence/ stubs already exist
> **Runs in PARALLEL with 3A and 3B**

- [ ] **3C.1** Finalize FastAPI app (health endpoint working in Docker)
- [ ] **3C.2** Classification endpoint (heuristic + ML model loading)
- [ ] **3C.3** Enhanced OCR endpoint (PaddleOCR, single page)
- [ ] **3C.4** Vision enhance endpoint (denoise, sharpen, balance pipeline)
- [ ] **3C.5** Redis queue integration (ARQ worker consuming tasks)
- [ ] **3C.6** R2 integration (download input, upload output)
- [ ] **3C.7** Docker compose working (API + worker + Redis)
- [ ] **3C.8** Tests passing in CI

**Acceptance:** `curl` can classify an image, get enhanced OCR, and enhance a document photo.

---

## PHASE 4A: Backend Observability — Weeks 7-9

> **Spec:** docs/BACKEND_MVP.md section 6 → Phase 4
> **Depends on:** Phase 3A completed

- [ ] **4A.1** Prometheus metrics exposition
- [ ] **4A.2** Grafana dashboards
- [ ] **4A.3** Structured error handling + Sentry
- [ ] **4A.4** Deep health checks
- [ ] **4A.5** Database performance tuning
- [ ] **4A.6** Caching layer (in-process LRU)
- [ ] **4A.7** Backup strategy (nightly pg_dump → age → R2)
- [ ] **4A.8** Load testing (k6/vegeta, 500 concurrent users)
- [ ] **4A.9** Runbook

**Acceptance:** All criteria in docs/BACKEND_MVP.md Phase 4 acceptance section pass.

---

## PHASE 4B: Frontend Cloud Sync — Weeks 7-9

> **Spec:** docs/FRONTEND_MVP.md section 4 → Phase 4
> **Depends on:** Phase 3B completed + Phase 3A completed (backend sync must be ready)
> **THIS IS WHERE FRONTEND MEETS BACKEND**

- [ ] **4B.1** Account screen (signup, login, password strength)
- [ ] **4B.2** Client-side E2E crypto (`:core:network` — libsodium, K_master, K_encrypt, HKDF)
- [ ] **4B.3** Sync engine (WorkManager, upload/download/delete/rename, exponential backoff)
- [ ] **4B.4** Conflict resolution UI (server vs local, user picks)
- [ ] **4B.5** Sync status in library cards (cloud-done, uploading, pending, local-only)
- [ ] **4B.6** Certificate pinning (Ktor + OkHttp)
- [ ] **4B.7** Tamper check (signature verification)
- [ ] **4B.8** Account management (change password, delete account, logout, data export)
- [ ] **4B.9** Ktor client setup (HTTP/2, retry, offline queue)
- [ ] **4B.10** Integration test (account → scan → sync → second device → see documents)

**Acceptance:** All criteria in docs/FRONTEND_MVP.md Phase 4 acceptance section pass.

---

## PHASE 4C: Intelligence Layer v2 — Weeks 7-9

> **Spec:** docs/INTELLIGENCE_LAYER.md section 9 → Phase 2
> **Depends on:** Phase 3C completed

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

## Session Log

> Claude Code: After each session, add one line here. Format: `YYYY-MM-DD | Task IDs completed | Notes`

| Date | Tasks | Notes |
|---|---|---|
| 2026-04-10 | specs created | CLAUDE.md, PROGRESS.md, INTELLIGENCE_LAYER.md, intelligence/ stubs |
| 2026-04-10 | test-gate added | CLAUDE.md test-gate rule, PROGRESS.md header, prompt-guide.txt fully rewritten with test-first enforcement |
| 2026-04-10 | 0.1–0.6 | DevOps skeleton: dirs, VERSION, .gitignore, Makefile, .editorconfig, .pre-commit-config.yaml + commitlint.config.js. 71/71 tests pass. |
| 2026-04-10 | 0.7–0.10 | Moved 5 spec docs to docs/, updated CLAUDE.md+PROGRESS.md refs, bootstrap.sh, doctor.sh, run-backend-local.sh. 128/128 tests pass. |
| 2026-04-10 | 0.11–0.13 | run-phone.sh (all flags + ADB), release.sh (semver bump, changelog, dry-run), rollback.sh (android/backend/ota/all). **PHASE 0 COMPLETE.** 189/189 tests pass. |
