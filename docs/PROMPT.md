# Paperkeep — Master Reference Prompt for Claude Code

> **This file is the single source of truth for Claude Code when building Paperkeep.** Read it once per session. It tells you what the product is, what the rules are, what to read next, and what to do.

---

## 1. What you are building

**Paperkeep** — an Android-only document scanner app that beats CamScanner on trust, speed, and feel.

- **Platform:** Android native (Kotlin 2.0, Jetpack Compose, Material 3 Expressive)
- **Backend:** NONE. The entire product lives on the user's phone. There is no server, no cloud database, no API.
- **Storage:** on-device only (Room + encrypted files), optional user-driven backup via SAF (Storage Access Framework) to any location the user picks (Google Drive, Dropbox, SD card, USB-OTG).
- **AI:** on-device only (ML Kit + TFLite). No network calls for intelligence, ever.
- **Monetization:** AdMob interstitials (non-intrusive, frequency-capped) + one-time $4.99 "Pro" IAP. No subscriptions.
- **Privacy posture:** the Data Safety form will read "no data collected." That is the literal truth and the #1 selling point.

**Why the name Paperkeep:** the old working codename was "ScanVault" but it was too close to existing Play Store listings. Paperkeep is the confirmed product name. Package ID: `app.paperkeep`.

---

## 2. The three rules that matter most

1. **There is no backend.** Do not write Go. Do not deploy to AWS. Do not add a network module for our own server. The only network traffic in the app is AdMob, UMP, Play Billing, and Play Integrity — all Google SDKs.
2. **Nothing leaves the device without the user's explicit tap.** Every share, every export, every backup is user-initiated and user-directed. No background sync. No telemetry. No analytics.
3. **Security is top-notch and non-negotiable.** Read `docs/PAPERKEEP_DESIGN.md` §6 in full before touching anything that handles keys, storage, auth, or network. That section is the security contract — PRs that violate it do not merge.

---

## 3. What to read, and when

Claude Code should read the **minimum** needed for the current task. Tokens are not free.

### Always read at session start
- `CLAUDE.md` (project brain — ~200 lines)
- `PROGRESS.md` (find the next unchecked task)
- This file (`docs/PROMPT.md`) — for the product-level rules

### Read only when the task requires it
| Task area | Read this |
|---|---|
| Anything touching keys, crypto, storage encryption, biometric, FLAG_SECURE, integrity checks | `docs/PAPERKEEP_DESIGN.md` §6 (Security) — **mandatory, in full** |
| UI / visual design / theming / motion / haptics | `docs/PAPERKEEP_DESIGN.md` §7 (UI/UX) |
| Data model / Room entities / FTS | `docs/PAPERKEEP_DESIGN.md` §4 (Data Model) and §6.4 (searchable-OCR) |
| Module structure / Gradle / dependencies | `docs/PAPERKEEP_DESIGN.md` §2 (Tech Stack) and §3 (Architecture) |
| Phase-specific work (which deliverables belong to this phase) | `docs/PAPERKEEP_DESIGN.md` §5 → the specific phase subsection only |
| Migrating from the old v1 codebase | `docs/PAPERKEEP_DESIGN.md` §10 (Migration) |
| Privacy policy / ToS copy | `docs/PRIVACY_POLICY.md`, `docs/TERMS_OF_SERVICE.md` |

### Never read these (they no longer exist — v1 was deleted)
- ~~BACKEND_MVP.md~~, ~~INTELLIGENCE_LAYER.md~~, ~~DEVOPS_AUTOMATION.md~~, ~~TERRAFORM_GUIDE.md~~, ~~DEPLOY.md~~, ~~RUNBOOK.md~~, ~~POST_LAUNCH_MONITORING.md~~, ~~FRONTEND_MVP.md~~ (v1), ~~DESIGN_SYSTEM.md~~ (v1)

---

## 4. The test-gate rule (mandatory, no exceptions)

Every task in `PROGRESS.md` follows this sequence. You cannot skip steps.

```
1. BUILD   → write the implementation
2. TEST    → write tests for every behavior (see test matrix below)
3. RUN     → execute ALL tests (new + existing)
4. PASS    → ALL tests must pass. If any fail, fix and re-run.
5. UPDATE  → ONLY after step 4 passes, check the box in PROGRESS.md
```

**If tests fail → do not check the box. Fix the code first.**
**If you skip writing tests → the task is NOT done, even if the code works.**

### Test matrix (Android-only in v2)

| Layer | Framework | What to test |
|---|---|---|
| ViewModels / UseCases / Repositories | JUnit 5 + Turbine + MockK | every public method: happy path, error path, edge case, state transitions |
| Room DAOs | AndroidX Test + in-memory Room | every query, migrations, FTS search returns correct results |
| Crypto / Keystore wrappers | Android instrumented tests (keys live in Keystore) | round-trip encrypt/decrypt, key invalidation on biometric enrollment change, tamper-detection of ciphertext |
| OpenCV / TFLite wrappers | Instrumented tests with fixture images | edge detection returns 4 corners on clean input, degrades gracefully on low-light |
| Compose screens | Compose UI tests | renders without crash, all interactions, state survives rotation, empty + error states, a11y: contentDescription present, 48dp touch targets |
| Macrobenchmark | androidx.benchmark | cold start < 500ms (Phase 5), capture→save < 2s, library scroll 60fps with 1000 docs |

### Test requirements per task type

**New screen:**
- renders without crash
- every user interaction (tap, swipe, long-press, back)
- state survives rotation (use `rememberSaveable` + ViewModel)
- empty state, error state (e.g. permission denied)
- contentDescription on every interactive element, touch targets ≥ 48dp

**New storage/encryption code:**
- round-trip: write → read → same data
- raw file on disk is unreadable without the master key
- migration: old schema → new schema preserves data
- concurrent access: two coroutines writing does not corrupt
- edge cases: empty string, max size, unicode

**New crypto code:**
- encrypt with K, decrypt with K → identical plaintext
- encrypt with K1, decrypt with K2 → fails (wrong key)
- flip one ciphertext byte → decryption fails with auth error (GCM tag integrity)
- keys actually in Keystore, never written to DataStore or files

**New OCR/imaging code:**
- valid input → expected result (measured against fixtures)
- corrupted input → graceful error, no crash
- large input (e.g. 50 MB image) → handled or rejected cleanly
- timeout / cancellation honored

---

## 5. Code rules (locked)

### Kotlin / Android
- Jetpack Compose only. No XML layouts except the splash (API requirement).
- **Hilt via KSP**, never kapt. kapt has a Windows path bug with Hilt's ProGuard resource generation.
- Material 3 (including Expressive motion/shapes APIs where available).
- Navigation Compose with type-safe routes (Kotlin serialization-based).
- Coroutines + Flow. Never `Thread`, never `AsyncTask`, never `RxJava`.
- `from __future__ annotations` equivalent — always use explicit types on public APIs.
- Package structure: feature modules depend on `:core:*` only, never on other features.
- Every `@Composable` that takes a lambda must accept a `Modifier` parameter.

### What is banned
- No WebView anywhere (historical RCE vector)
- No Ktor / OkHttp / Retrofit in our code — we have no backend
- No libsodium / zxcvbn-kotlin — no passwords to strengthen
- No Firebase Analytics / Crashlytics / Sentry / any telemetry SDK
- No `Log.*` calls in release (Detekt blocks them)
- No `SupportSQLiteDatabase.execSQL` with user input — use Room typed queries
- No `file://` URIs shared across apps — always FileProvider
- No `android:exported="true"` without a written justification comment

### Commit convention
All commits: `<type>(<scope>): <subject>`
- `type` ∈ {feat, fix, perf, refactor, docs, test, build, ci, chore}
- `scope` ∈ {android, core, feature, docs, deps} — no more `backend`, no more `intelligence`, no more `ci`-for-cloud-stuff

---

## 6. Known Windows-host gotchas (save hours per session)

| Problem | Fix |
|---|---|
| `kapt` fails with "Invalid relative name: META-INF\proguard\..." | Use `alias(libs.plugins.ksp)` + `ksp(libs.hilt.compiler)` instead of kapt |
| Shell is bash — use Unix syntax (`/dev/null`, forward slashes) | — |
| PowerShell also available via the PowerShell tool when needed | — |
| Encrypted-file Android instrumented tests require an emulator or device | start `emulator -avd Pixel_6a_API_34` or plug in a phone with USB debugging |

---

## 7. The daily prompt (what the human pastes at session start)

```
Read CLAUDE.md, PROGRESS.md, and docs/PROMPT.md. Find the next unchecked task
in PROGRESS.md. Do it per the test-gate rule. Update PROGRESS.md when done.
The product is Paperkeep — Android-only, no backend, top-notch security.
```

That's it. Everything else is in the three files referenced above.

---

## 8. When you are stuck

- If the task is ambiguous → re-read `docs/PAPERKEEP_DESIGN.md` for the relevant phase subsection.
- If it involves security → re-read `docs/PAPERKEEP_DESIGN.md` §6 in full.
- If tests fail and you cannot diagnose → write a failing reproduction as a test, then fix; do NOT disable the test.
- If something "requires a backend" → you are wrong about the task. There is no backend. Re-read §1 above.

---

## 9. Definition of Done — per phase

A phase is not done until:

1. Every acceptance criterion in its `docs/PAPERKEEP_DESIGN.md` §5 subsection verified on real hardware (not just emulator)
2. Detekt clean, zero warnings
3. `assembleRelease` with R8 full mode succeeds
4. `:core:*` module unit coverage ≥ 70%
5. Previous phase's tests still pass (regression guard)
6. 30-minute manual test session on a mid-range device finds zero blockers
7. The signed APK has been installed on the developer's personal phone and used for at least one full day

---

## 10. TL;DR (if you remember nothing else)

- Product name is **Paperkeep**, package `app.paperkeep`.
- **Android-only. No backend. No cloud. No telemetry.**
- Read `CLAUDE.md` + `PROGRESS.md` + (this file) at session start.
- Read `docs/PAPERKEEP_DESIGN.md` only the section you need.
- Follow the test-gate rule. Every task. No exceptions.
- When in doubt about security → read `docs/PAPERKEEP_DESIGN.md` §6 in full.
