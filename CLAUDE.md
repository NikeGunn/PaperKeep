# ScanVault — Claude Code Brain

> **This file is the ONLY file you read first.** It tells you where everything is, what state the project is in, and what to do next. Do NOT read spec files unless directed here.

---

## Who

- **Developer:** Nikhil (solo dev, AI engineer + cybersecurity background)
- **Product:** ScanVault — Android document scanner that beats CamScanner
- **Stack:** Kotlin/Compose (Android) + Go (backend) + Python/FastAPI (intelligence) + DevOps (GitHub Actions)

---

## Project Root Map

```
ScanVault/
├── CLAUDE.md              ← YOU ARE HERE. Read this first, always.
├── PROGRESS.md            ← Build tracker. Check what's done, find next task.
├── prompt-guide.txt       ← 45-session roadmap (copy-paste prompts per session)
├── docs/
│   ├── FRONTEND_MVP.md        ← Android spec (5 phases). Read ONLY when building android/.
│   ├── BACKEND_MVP.md         ← Go backend spec (5 phases). Read ONLY when building backend/.
│   ├── INTELLIGENCE_LAYER.md  ← Python AI spec (3 phases). Read ONLY when building intelligence/.
│   ├── DESIGN_SYSTEM.md       ← UI/UX spec. Read ONLY when implementing UI components.
│   └── DEVOPS_AUTOMATION.md   ← CI/CD spec. Read ONLY when building .github/ or scripts/.
├── android/               ← Kotlin Android app
├── backend/               ← Go API
├── intelligence/          ← Python FastAPI AI services (stubs exist, being built Phase 3C+)
├── .github/workflows/     ← CI/CD pipelines
├── ota/                   ← Over-the-air config
├── scripts/               ← Dev scripts
└── VERSION                ← Single version source
```

---

## Three-Layer Architecture (READ THIS — it prevents the biggest mistakes)

```
┌─────────────────────────────────────┐
│     Android App  (Kotlin/Compose)    │  ← Phases 1B–5 (FRONTEND_MVP.md)
│  On-device: ML Kit, OpenCV, Room    │
│  Phase 4B+: Ktor Client, E2E crypto │
└──────────────┬──────────────────────┘
               │ HTTPS (Ktor + cert pinning, Phase 4B+)
               ▼
┌─────────────────────────────────────┐
│       Go Backend  (chi + pgx)        │  ← Phases 1A–5 (BACKEND_MVP.md)
│  accounts / vault / sync / audit    │
│  Phase 4C+: internal/intelligence/  │  ← NEW proxy package
└──────────────┬──────────────────────┘
               │ HTTP (internal only) + Redis queue (Phase 4C+)
               ▼
┌─────────────────────────────────────┐
│  Python Intelligence  (FastAPI/ARQ)  │  ← Phases 3C–5 (INTELLIGENCE_LAYER.md)
│  OCR / Vision / AI / Classification │
│  Port 8100 — NEVER exposed to net   │
└─────────────────────────────────────┘
```

### Critical interconnection rules — never violate these:

1. **Android NEVER talks to Python directly.** All AI requests go Android → Go → Python.
2. **Go proxies intelligence calls** via `internal/intelligence/` package (built in Phase 4C).
3. **Android only calls Go** at `https://api.scanvault.app/v1/` (or staging equivalent).
4. **Intelligence endpoints in Go:** `POST /v1/intelligence/classify`, `POST /v1/intelligence/tasks`, `GET /v1/intelligence/tasks/{id}`, `DELETE /v1/intelligence/tasks/{id}` — all require Paseto auth.
5. **Python service port:** 8100, Docker container, only reachable from Go on the same host.
6. **Zero-knowledge is preserved:** Go never decrypts vault data. For server-side AI, user opts in per-document, app decrypts locally, uploads plaintext to **separate** `processing/` R2 prefix (1-hour TTL).
7. **Phases 1–3 are 100% offline.** No network calls on Android until Phase 4B.
8. **Intelligence layer is optional.** If Python is down, app works. Go degrades gracefully.

### Integration Points by Phase (when things connect)

| Phase | What connects | Spec section |
|---|---|---|
| **4B.1** | Android account screens call Go `/v1/accounts` + `/v1/sessions` | FRONTEND_MVP.md Phase 4 |
| **4B.2** | Android E2E crypto (libsodium) — keys never leave device | FRONTEND_MVP.md Phase 4, BACKEND_MVP.md §4 |
| **4B.3** | Android WorkManager sync calls Go `/v1/vault/*` endpoints | FRONTEND_MVP.md Phase 4 |
| **4B.6** | Android cert pinning against Go backend TLS cert SHA-256 | FRONTEND_MVP.md Phase 4 |
| **4C.4** | Go `internal/intelligence/` HTTP client calls Python `:8100` | INTELLIGENCE_LAYER.md §3.3 |
| **4C.5** | Go migration 0004 adds `intelligence_tasks` table | INTELLIGENCE_LAYER.md §2.1 |
| **4C.6** | Go `/v1/intelligence/*` endpoints proxy to Python | INTELLIGENCE_LAYER.md §4 |
| **3C.5** | Python ARQ worker reads from Redis queue `scanvault:intelligence:tasks` | INTELLIGENCE_LAYER.md §3.1 |
| **3C.6** | Python reads/writes R2 `processing/<account>/<task>/` prefix | INTELLIGENCE_LAYER.md §5 |

---

## Rules for Claude Code

### Token Saving Rules (CRITICAL)

1. **NEVER read a spec file unless PROGRESS.md points you there.** Each spec is 500-800 lines. Read only the section you need.
2. **Read PROGRESS.md FIRST** to find the current task and which spec section to reference.
3. **After completing any task**, update PROGRESS.md immediately — check the box, write the date, add notes if needed.
4. **One task per session.** Don't try to do everything. Do one PROGRESS.md task well.
5. **When a session starts**, read only: this file (CLAUDE.md) + PROGRESS.md. That's ~200 lines total vs ~3000 lines if you read all specs.

### Test-Gate Rule (MANDATORY — NO EXCEPTIONS)

Every task follows this exact sequence. You CANNOT skip steps.

```
1. BUILD   → Write the implementation code
2. TEST    → Write tests for every behavior (see test requirements below)
3. RUN     → Execute ALL tests (new + existing)
4. PASS    → ALL tests must pass. If any fail, fix code and re-run.
5. UPDATE  → ONLY after step 4 passes, check the box in PROGRESS.md
```

**If tests fail, DO NOT update PROGRESS.md. Fix the code first.**
**If you skip writing tests, the task is NOT done even if the code works.**

### What Tests to Write

| Stack | Framework | Test types required |
|---|---|---|
| Go backend | `go test` + testcontainers-go | Unit tests for every function. Integration tests for every endpoint (real Postgres). |
| Android | JUnit 5 + Turbine + MockK + Compose UI tests | Unit tests for ViewModels, UseCases, Repositories. UI tests for every screen flow. |
| Python | pytest + pytest-asyncio + httpx TestClient | Unit tests for every service method. API tests for every endpoint. |

### Test Requirements Per Task Type

**Backend endpoint task:**
- Test happy path (correct input → correct output)
- Test auth required (no token → 401)
- Test invalid input (bad JSON, missing fields → 400)
- Test not found (wrong UUID → 404)
- Test authorization (other user's resource → 404, never 403)
- Test rate limiting (exceed limit → 429)
- Test idempotency (same request twice → same result)
- Test concurrent access where applicable (race conditions)

**Backend intelligence proxy task (Phase 4C+):**
- Test Go calls Python classify endpoint (mock Python, use httptest server)
- Test Go publishes task to Redis (mock Redis)
- Test Go handles Python service timeout gracefully
- Test Go handles Python service down (returns 503, not panic)
- Test rate limiting on intelligence endpoints (20/min sync, 100/hr async)
- Test auth required (all intelligence endpoints need valid Paseto token)

**Frontend screen task:**
- Test screen renders without crash
- Test all user interactions (tap, swipe, long-press, back)
- Test state survives rotation
- Test empty state
- Test error state (permission denied, disk full)
- Test accessibility (contentDescription exists, touch targets ≥ 48dp)

**Frontend sync/network task (Phase 4B+):**
- Test successful API call returns expected response
- Test network failure → WorkManager retries with exponential backoff
- Test offline state → operation queued, not dropped
- Test cert pinning rejects wrong cert
- Test E2E encrypt → upload → download → decrypt → same plaintext

**Frontend data task (Room, encryption, storage):**
- Test round-trip (write → read → same data)
- Test encryption (raw file is unreadable)
- Test migration (old schema → new schema preserves data)
- Test concurrent access (two coroutines writing)
- Test edge cases (empty string, max size, unicode)

**Python service task:**
- Test happy path (valid image → correct result)
- Test invalid input (corrupted image → graceful error, not crash)
- Test model not loaded (first request triggers lazy load)
- Test timeout (processing exceeds limit → timeout error)
- Test large input (50MB image → handled or rejected)

**CI/CD task:**
- Validate YAML syntax
- Verify all secrets are referenced (not hardcoded)
- Dry-run if possible

### Code Rules

- `from __future__ import annotations` in all Python files
- Go: no ORM, use sqlc + raw SQL. Router is chi. No Gin/Echo/Fiber.
- Kotlin: Jetpack Compose only, no XML layouts. Hilt for DI. Material 3.
- **Android networking (Phase 4B+):** Ktor Client with OkHttp engine. No Retrofit, no Volley.
- **Hilt on Windows:** use KSP plugin (`alias(libs.plugins.ksp)`), NOT kapt. kapt has a Windows path bug with Hilt's ProGuard resource generation.
- Security is non-negotiable. Read section 3 of docs/FRONTEND_MVP.md and section 4 of docs/BACKEND_MVP.md before writing any code that touches auth, storage, or network.
- Every phase must pass its acceptance criteria before moving to the next.
- Never rewrite earlier phase code. New phases ADD modules, they don't replace.
- **Tests live next to the code they test.** Go: `_test.go` files. Android: `src/test/` and `src/androidTest/`. Python: `tests/` directory.

### Known Platform Gotchas (save 2+ hours per session)

| Problem | Root cause | Fix |
|---|---|---|
| `kapt` fails with "Invalid relative name: META-INF\proguard\..." | Windows kapt + Hilt backslash bug | Replace `alias(libs.plugins.kotlin.kapt)` + `kapt(libs.hilt.compiler)` with `alias(libs.plugins.ksp)` + `ksp(libs.hilt.compiler)` |
| `go test -race` fails with CGO errors | Race detector needs GCC | `PATH="/c/msys64/mingw64/bin:$PATH" CGO_ENABLED=1 go test -race ./...` |
| `sqlc generate` not in PATH | Go bin not in shell PATH | `/c/Users/Nautilus/go/bin/sqlc generate` |
| Paseto key too short | 32 ASCII chars ≠ 32 decoded bytes | Use base64 of 32 bytes: `"QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="` |

### File Reading Strategy (saves ~80% tokens)

When PROGRESS.md says to work on a task:

| Task area | Read ONLY these sections |
|---|---|
| DevOps setup | docs/DEVOPS_AUTOMATION.md sections 1-3 only |
| CI workflows | docs/DEVOPS_AUTOMATION.md section 6 only |
| Backend Phase N | docs/BACKEND_MVP.md section 6 → Phase N subsection only |
| Frontend Phase N | docs/FRONTEND_MVP.md section 4 → Phase N subsection only |
| Intelligence Phase N | docs/INTELLIGENCE_LAYER.md section 9 → Phase N only |
| Go intelligence proxy (4C) | docs/INTELLIGENCE_LAYER.md sections 2-4 |
| UI components | docs/DESIGN_SYSTEM.md section 2.7 only |
| Colors/Theme | docs/DESIGN_SYSTEM.md sections 2.2-2.3 only |
| Any security work | docs/BACKEND_MVP.md section 4 (always, full) |
| Android sync (4B) | docs/FRONTEND_MVP.md Phase 4 + docs/BACKEND_MVP.md §5 API contract |

### Commit Convention

All commits: `<type>(<scope>): <subject>` where type is feat/fix/perf/refactor/docs/test/build/ci/chore and scope is android/backend/intelligence/ci/docs/deps.

---

## Build Order (the execution roadmap)

```
WEEK 1-2: Foundation (Sessions 1-16)
  ├── DevOps skeleton (repo structure, scripts, VERSION, .gitignore)
  ├── Backend Phase 1A (Go scaffold, auth, Postgres, deploy to staging)
  ├── Frontend Phase 1B (Android scaffold, camera, edge detection, encrypted storage)
  └── CI/CD Phase 1C (GitHub Actions for all three stacks)

WEEK 3-4: Phase 2 (Sessions 17-23)
  ├── Backend Phase 2A (vault, R2, upload/download, sync manifest) ← DONE
  └── Frontend Phase 2B (library, OCR, PDF export, biometric lock) ← IN PROGRESS

WEEK 5-6: Phase 3 (Sessions 24-30)
  ├── Backend Phase 3A (sync engine, conflict resolution, abuse prevention)
  ├── Frontend Phase 3B (AI modes, AdMob, onboarding, Play Store prep)
  └── Intelligence Phase 3C (Python scaffold, classify, enhanced OCR, vision, Redis queue)
        ↑ Intelligence stubs already exist in intelligence/ — just needs implementation

WEEK 7-9: Phase 4 (Sessions 31-36)
  ├── Backend Phase 4A (observability, performance, backups)
  ├── Frontend Phase 4B (cloud sync, E2E crypto, account screens)
  │     ↑ THIS IS WHERE ANDROID FIRST CALLS THE GO BACKEND
  └── Intelligence Phase 4C (layout, extraction, super-res + Go proxy integration)
        ↑ THIS IS WHERE GO FIRST CALLS PYTHON

WEEK 10-11: Phase 5 (Sessions 37-45)
  ├── Backend Phase 5 (production hardening, launch)
  ├── Frontend Phase 5 (polish, accessibility, Play Store submission)
  └── Intelligence Phase 3 (summarization, embeddings, LLM proxy — post-launch v2)
```

**Backend and Frontend run in PARALLEL** — they're independent until:
- Phase 4B: Android starts calling Go endpoints for the first time
- Phase 4C: Go starts calling Python for the first time

**Intelligence layer starts at Week 5 (Phase 3C)** — it's additive, never a blocker.

---

## Current State (as of last session)

- **Phase 0:** COMPLETE
- **Phase 1A (Backend):** COMPLETE — accounts, auth, vault, deploy
- **Phase 1B (Android):** COMPLETE — camera, encryption, Room, navigation
- **Phase 1C (CI/CD):** COMPLETE — all 5 workflows validated
- **Phase 2A (Backend Vault):** COMPLETE — R2, upload/download, quota, manifest, purge
- **Phase 2B (Android Library):** IN PROGRESS — 2B.1/2B.2/2B.3 done, 2B.4+ pending
- **Phase 3C (Intelligence):** Stubs created, real implementation starts at Session 29

---

## Daily Prompt (copy-paste this to start a session)

```
Read CLAUDE.md and PROGRESS.md. Find the next unchecked task. Do it. Update PROGRESS.md when done.
```
