# ScanVault — Claude Code Brain

> **This file is the ONLY file you read first.** It tells you where everything is, what state the project is in, and what to do next. Do NOT read spec files unless directed here.

## Who

- **Developer:** Nikhil (solo dev, AI engineer + cybersecurity background)
- **Product:** ScanVault — Android document scanner that beats CamScanner
- **Stack:** Kotlin/Compose (Android) + Go (backend) + Python/FastAPI (intelligence) + DevOps (GitHub Actions)

## Project Root Map

```
ScanVault/
├── CLAUDE.md              ← YOU ARE HERE. Read this first, always.
├── PROGRESS.md            ← Build tracker. Check what's done, find next task.
├── docs/
│   ├── FRONTEND_MVP.md        ← Android spec (5 phases). Read ONLY when building android/.
│   ├── BACKEND_MVP.md         ← Go backend spec (5 phases). Read ONLY when building backend/.
│   ├── INTELLIGENCE_LAYER.md  ← Python AI spec (3 phases). Read ONLY when building intelligence/.
│   ├── DESIGN_SYSTEM.md       ← UI/UX spec. Read ONLY when implementing UI components.
│   └── DEVOPS_AUTOMATION.md   ← CI/CD spec. Read ONLY when building .github/ or scripts/.
├── android/               ← Kotlin Android app (TO BE CREATED)
├── backend/               ← Go API (TO BE CREATED)
├── intelligence/          ← Python AI services (stubs created)
├── .github/workflows/     ← CI/CD pipelines
├── ota/                   ← Over-the-air config
├── scripts/               ← Dev scripts
└── VERSION                ← Single version source
```

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

**Frontend screen task:**
- Test screen renders without crash
- Test all user interactions (tap, swipe, long-press, back)
- Test state survives rotation
- Test empty state
- Test error state (permission denied, disk full)
- Test accessibility (contentDescription exists, touch targets ≥ 48dp)

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
- Security is non-negotiable. Read section 3 of docs/FRONTEND_MVP.md and section 4 of docs/BACKEND_MVP.md before writing any code that touches auth, storage, or network.
- Every phase must pass its acceptance criteria before moving to the next.
- Never rewrite earlier phase code. New phases ADD modules, they don't replace.
- **Tests live next to the code they test.** Go: `_test.go` files. Android: `src/test/` and `src/androidTest/`. Python: `tests/` directory.

### File Reading Strategy (saves ~80% tokens)

When PROGRESS.md says to work on a task:

| Task area | Read ONLY these sections |
|---|---|
| DevOps setup | docs/DEVOPS_AUTOMATION.md sections 1-3 only |
| CI workflows | docs/DEVOPS_AUTOMATION.md section 6 only |
| Backend Phase N | docs/BACKEND_MVP.md section 6 → Phase N subsection only |
| Frontend Phase N | docs/FRONTEND_MVP.md section 4 → Phase N subsection only |
| Intelligence Phase N | docs/INTELLIGENCE_LAYER.md section 9 → Phase N only |
| UI components | docs/DESIGN_SYSTEM.md section 2.7 only |
| Colors/Theme | docs/DESIGN_SYSTEM.md sections 2.2-2.3 only |
| Any security work | docs/BACKEND_MVP.md section 4 (always, full) |

### Commit Convention

All commits: `<type>(<scope>): <subject>` where type is feat/fix/perf/refactor/docs/test/build/ci/chore and scope is android/backend/intelligence/ci/docs/deps.

## Build Order (the execution roadmap)

```
WEEK 1: Foundation
  ├── DevOps skeleton (repo structure, scripts, VERSION, .gitignore)
  ├── Backend Phase 1 (Go scaffold, auth, Postgres, deploy to staging)
  └── Frontend Phase 1 (Android scaffold, camera, edge detection, encrypted storage)

WEEK 2: Continue Phase 1
  ├── Backend Phase 1 completion (all acceptance criteria)
  └── Frontend Phase 1 completion (all acceptance criteria)

WEEK 3-4: Phase 2
  ├── Backend Phase 2 (vault, R2, upload/download, sync manifest)
  └── Frontend Phase 2 (library, OCR, PDF export, biometric lock)

WEEK 5-6: Phase 3
  ├── Backend Phase 3 (sync engine, conflict resolution, abuse prevention)
  ├── Frontend Phase 3 (AI modes, AdMob, onboarding, Play Store prep)
  └── Intelligence Phase 1 (Python scaffold, classify, enhanced OCR, vision)

WEEK 7-9: Phase 4
  ├── Backend Phase 4 (observability, performance, backups)
  ├── Frontend Phase 4 (cloud sync, E2E crypto, account screens)
  └── Intelligence Phase 2 (layout analysis, field extraction, super-res)

WEEK 10-11: Phase 5
  ├── Backend Phase 5 (production hardening, launch)
  ├── Frontend Phase 5 (polish, accessibility, Play Store submission)
  └── Intelligence Phase 3 (summarization, embeddings, LLM proxy)
```

**DevOps goes FIRST** because both backend and frontend depend on it (repo structure, CI, scripts).

**Backend and Frontend run in PARALLEL** after DevOps — they're independent until Backend Phase 3 / Frontend Phase 4 where sync integration happens.

**Intelligence layer starts at Week 5** — it's additive and not needed until the core app works.

## Daily Prompt (copy-paste this to start a session)

```
Read CLAUDE.md and PROGRESS.md. Find the next unchecked task. Do it. Update PROGRESS.md when done.
```

That's it. 14 words. Claude Code reads ~200 lines (this file + PROGRESS.md), finds the task, reads only the relevant spec section, builds, and checks the box.
