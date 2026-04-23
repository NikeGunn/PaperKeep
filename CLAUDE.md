# Paperkeep — Claude Code Brain

> **This file is the ONLY file you read first.** It tells you where everything is, the product state, and what to do next. Do NOT read spec files unless this file or `PROGRESS.md` directs you there.

---

## Who and what

- **Developer:** Nikhil (solo dev, AI engineer + cybersecurity background)
- **Product:** **Paperkeep** — Android document scanner that beats CamScanner on trust, speed, and feel
- **Stack:** Kotlin 2.0 + Jetpack Compose Material 3 Expressive + Hilt (KSP) + Room + CameraX + ML Kit + TFLite + OpenCV
- **Platform:** **Android only.** No backend. No cloud. No telemetry.
- **Repo layout:** a single Android project under `android/` plus design docs under `docs/`. Everything else is legacy waiting to be deleted (see §Cleanup below).

**Naming history:** the working codename was "ScanVault" but collided with existing Play Store apps. Product name is now **Paperkeep**, package `app.paperkeep`. Rename across the codebase is Phase 1, task P1.1.

---

## Project Root Map

```
Paperkeep/
├── CLAUDE.md                       ← YOU ARE HERE. Read this first, always.
├── PROGRESS.md                     ← v2 build tracker. Find the next unchecked task here.
├── VERSION                         ← single version source (reset to 2.0.0-alpha.1 in P1.1)
├── docs/
│   ├── PAPERKEEP_DESIGN.md         ← master system design (5 phases + top-notch security §6)
│   ├── PROMPT.md                   ← reference prompt Claude reads per session
│   ├── PRIVACY_POLICY.md           ← in-app privacy policy (needs Paperkeep rename)
│   └── TERMS_OF_SERVICE.md         ← Play Store ToS (needs Paperkeep rename)
├── android/                        ← the product
│   ├── app/
│   ├── core/
│   │   ├── ui, common, crypto, security, data, domain,
│   │   ├── imaging, ml, pdf, ads
│   │   └── backup                  ← NEW in v2 (replaces :core:network)
│   ├── feature/
│   │   ├── scanner, library, reader, settings, onboarding
│   │   └── (account, sync → DELETED in P1.2)
│   ├── benchmark/                  ← Macrobenchmark module
│   └── store/                      ← Play Store metadata
└── .github/workflows/              ← one unified Android workflow (others deleted in P1.3)

# Legacy v1 trees live in scrape/ (archived 2026-04-23). Do NOT read, do NOT build against.
# scrape/ gets deleted entirely AFTER task P1.0 (terraform destroy) confirms AWS = $0/mo.
# See scrape/README.md for what's in there and when it's safe to rm -rf.
```

---

## The One-Layer Architecture (read this — it prevents the biggest mistakes)

```
┌────────────────────────────────────────────────┐
│          Paperkeep Android App                  │
│  (Kotlin / Compose / Material 3 Expressive)     │
│                                                 │
│  Capture → OpenCV → ML Kit/TFLite → Room       │
│  Encrypted on disk (AES-256-GCM + Keystore)    │
│  Optional: AdMob interstitials, Play Billing   │
└────────────────────────────────────────────────┘
         │                             ▲
         │ (user-initiated export)     │ (user-initiated backup)
         ▼                             │
  Share sheet / FileProvider      SAF → any location
   (system-handled)             (Drive / Dropbox / SD / USB-OTG)
```

### Critical rules — never violate these

1. **No backend. No server. No API we own.** There is no Go, no Python, no AWS. If a task description mentions any of these, the task description is wrong — stop and ask.
2. **No network traffic except Google SDKs.** AdMob, UMP, Play Billing, Play Integrity, ML Kit module download. That is the complete list. Any other outbound request is a bug.
3. **Nothing auto-uploads.** Every data exit from the device is a user tap on share / export / backup.
4. **All on-disk data is encrypted.** Every page image, thumbnail, OCR blob, signature, crash log → AES-256-GCM ciphertext in `filesDir/`. Plaintext-on-disk is a bug. The only exception is `cacheDir/exports/*.pdf` which lives for 60 seconds during a share.
5. **Keys live in Keystore only.** Hardware-backed (StrongBox when available). Never in DataStore, never in a file, never logged.
6. **Android-only in v2.** iOS is a future rewrite; do not try to "make things portable" at the expense of Android quality.
7. **No telemetry.** No Firebase Analytics, no Crashlytics, no Sentry, no AppsFlyer, no Adjust. AdMob + UMP + Play are the only Google SDKs with network access.

---

## Rules for Claude Code

### Token-saving rules (critical)

1. **Never read `docs/PAPERKEEP_DESIGN.md` in full unless truly needed.** It's ~800 lines. Read only the section the current task requires. The file-reading map below says exactly what to open.
2. **Read `PROGRESS.md` first** to find the current task and which design section to reference.
3. **Update `PROGRESS.md` immediately** after completing a task — check the box, write the date, add a one-line note.
4. **One task per session.** Do one task well.
5. At session start, read only: this file + `PROGRESS.md` + `docs/PROMPT.md`. That's ~500 lines total. Everything else is on-demand.

### Test-gate rule (mandatory — no exceptions)

```
1. BUILD   → write the implementation
2. TEST    → write tests for every behavior
3. RUN     → execute ALL tests (new + existing)
4. PASS    → every test green. If any fail, fix code and re-run.
5. UPDATE  → ONLY after step 4 passes, check the box in PROGRESS.md
```

If tests fail → do not check the box. Fix the code.
If you skip tests → the task is not done even if the code works.

Per-task-type test requirements live in `docs/PROMPT.md` §4.

### File reading map (saves ~80% tokens)

| Task area | Read ONLY these sections |
|---|---|
| Any security-touching code (crypto, keys, storage, biometric, integrity, FLAG_SECURE) | `docs/PAPERKEEP_DESIGN.md` §6 — **in full, mandatory** |
| Phase N deliverables | `docs/PAPERKEEP_DESIGN.md` §5 → Phase N subsection only |
| UI / theme / motion / haptics | `docs/PAPERKEEP_DESIGN.md` §7 |
| Data model / Room / FTS | §4 + §6.4 |
| Module layout / Gradle | §2 + §3 |
| v1→v2 migration (deleting legacy modules) | §10 |
| Privacy policy / ToS copy | `docs/PRIVACY_POLICY.md`, `docs/TERMS_OF_SERVICE.md` |

### Code rules

- Jetpack Compose only. No XML layouts except the splash.
- **Hilt via KSP**, never kapt (Windows path bug).
- Material 3 with Expressive motion/shape tokens where available.
- Navigation Compose with type-safe routes.
- Coroutines + Flow only. No Thread, no AsyncTask, no RxJava.
- Feature modules depend on `:core:*` only, never on each other.
- Security is non-negotiable. Re-read `PAPERKEEP_DESIGN.md` §6 before any PR touching keys, storage, auth, or integrity.
- Never rewrite earlier-phase code. New phases ADD modules — they do not replace.
- Tests live next to the code: `src/test/` for unit, `src/androidTest/` for instrumented.

### Banned

- No WebView (RCE history)
- No Ktor/OkHttp/Retrofit in app code — we have no backend
- No libsodium, no zxcvbn-kotlin — no passwords to strengthen
- No Firebase Analytics, Crashlytics, Sentry, AppsFlyer, Adjust, or any telemetry SDK
- No `Log.*` in release (Detekt rule enforces this)
- No `execSQL` with user input — use Room typed queries
- No `file://` URIs shared across apps — FileProvider only
- No `android:exported="true"` without a comment explaining why

### Commit convention

`<type>(<scope>): <subject>`
- `type` ∈ {feat, fix, perf, refactor, docs, test, build, ci, chore}
- `scope` ∈ {android, core, feature, docs, deps}

---

## Known platform gotchas (save hours per session)

| Problem | Root cause | Fix |
|---|---|---|
| `kapt` fails with "Invalid relative name: META-INF\proguard\..." | Windows kapt + Hilt backslash bug | Use `alias(libs.plugins.ksp)` + `ksp(libs.hilt.compiler)` |
| Shell tool runs bash — avoid Windows-style paths | — | Use forward slashes, `/dev/null`, not `NUL` |
| Instrumented tests for Keystore-backed crypto can't run on JVM | Keys only exist on-device/emulator | Put them in `src/androidTest/`, start an emulator or connect a phone |
| ML Kit language model not present | Not bundled for non-Latin scripts | Use `ModuleInstallClient` to download on demand; test with a fixture image in English first |
| StrongBox not available on the test device | Pixel 6a has it, older emulators don't | Try StrongBox → catch `StrongBoxUnavailableException` → fall back to TEE |

---

## Build order (the execution roadmap)

Paperkeep ships in **5 phases, ~25 sessions total, one stack**.

```
PHASE 1 — Foundation, rename, capture, encrypted storage      (Sessions 1–5)
PHASE 2 — Library, OCR, PDF export, biometric lock            (Sessions 6–11)
PHASE 3 — Smart modes (ID/receipt/whiteboard/book), AdMob     (Sessions 12–16)
PHASE 4 — Local backup (SAF encrypted ZIP), polish, a11y,    (Sessions 17–21)
          i18n, widgets, storage manager
PHASE 5 — On-device summarizer, Pro IAP, perf pass, launch    (Sessions 22–25)
```

Phases are sequential. Do not start phase N+1 until phase N's acceptance criteria pass (see `docs/PAPERKEEP_DESIGN.md` §5).

---

## Current State (post-pivot, as of 2026-04-23)

### Product state
- **Pivot decision:** v1 (Go backend + Python intelligence + AWS infra) abandoned for cost. Paperkeep v2 is Android-only, backend-free.
- **v1 Android work partially reusable:** existing modules `:core:{ui,common,crypto,data,domain,imaging,ml,pdf,security,ads}` and `:feature:{scanner,library,reader,settings,onboarding}` carry over. Phase 1 and 2 of the old FRONTEND_MVP were substantially built — most of that code is still valuable after the rename.
- **v1 Android modules to DELETE:** `:core:network`, `:feature:account`, `:feature:sync`. Their code is coupled to the dead backend.
- **v1 non-Android directories:** moved to `scrape/` on 2026-04-23 (backend, intelligence, infra, ota, deploy, old scripts, 7 backend-focused workflows, Makefile, nuke-config.yml, costs.csv). See `scrape/README.md`.
- **AWS infra:** needs teardown (`terraform destroy` against `scrape/infra/`) BEFORE `scrape/` is deleted, so billing stops immediately. Tracked as task P1.0.

### What Claude Code should do on the very next session
Open `PROGRESS.md`. The first unchecked task is `P1.1 — Rename codebase (ScanVault → Paperkeep)` across `android/`. Do it.

---

## Cleanup status (what's already done, what's pending)

Already done (2026-04-23):
- ✅ Deleted obsolete spec docs: `BACKEND_MVP.md`, `INTELLIGENCE_LAYER.md`, `DEVOPS_AUTOMATION.md`, `TERRAFORM_GUIDE.md`, `DEPLOY.md`, `RUNBOOK.md`, `POST_LAUNCH_MONITORING.md`, `FRONTEND_MVP.md` (v1), `DESIGN_SYSTEM.md` (v1)
- ✅ Deleted old prompt files at root: `prompt-guide.txt`, `scanvault prompt 1.txt`, `Scanvault Prompt 2.txt`
- ✅ Renamed `v2_Frontend_design.md` → `PAPERKEEP_DESIGN.md`
- ✅ Created `docs/PROMPT.md` (Claude reference prompt)
- ✅ Rewrote `CLAUDE.md` (this file) and `PROGRESS.md` for Paperkeep v2

Already done (2026-04-23, continued):
- ✅ Moved v1 legacy trees to `scrape/` (backend, intelligence, infra, ota, deploy, 12 backend scripts, 7 backend workflows, Makefile, nuke-config.yml, costs.csv) with a `scrape/README.md` explaining what's there and when it's safe to delete
- ✅ Pruned `scripts/` to Android-only: `dev.sh`, `dev-check.sh`, `dev-wifi-pair.sh`, `run-phone.sh`
- ✅ Pruned `.github/workflows/` to Android-only: `android-ci.yml`, `android-release.yml`, `security-scan.yml`
- ✅ `P1.0` — AWS teardown complete. User manually removed every resource from the AWS account (Aurora, Lambda, API Gateway, S3, ECR, Secrets Manager, CloudWatch, VPC — all gone). Cost should be $0 going forward.
- ✅ `P1.5` — Privacy Policy & ToS rewritten for Paperkeep v2 (not just renamed — full rewrite to reflect no-server / no-account / on-device-only reality)

Pending (these are the first tasks in `PROGRESS.md`):
- ⏳ `P1.1` — Rename codebase (ScanVault → Paperkeep, `com.scanvault.app` → `app.paperkeep`), reset `VERSION` to `2.0.0-alpha.1`
- ⏳ `P1.2` — Delete dead Android modules (`:core:network`, `:feature:account`, `:feature:sync`) and all their references
- ⏳ `P1.3` — `rm -rf scrape/` (AWS is clean, so this is unblocked — just do a final cost-explorer check first)
- ⏳ `P1.4` — Update `android-ci.yml` to reflect Android-only flow (unit → instrumented → assembleRelease → Firebase App Distribution)

---

## Daily prompt (copy-paste this at session start)

```
Read CLAUDE.md, PROGRESS.md, and docs/PROMPT.md. Find the next unchecked task.
Do it per the test-gate rule. Update PROGRESS.md when done.
The product is Paperkeep — Android-only, no backend, top-notch security.
```
