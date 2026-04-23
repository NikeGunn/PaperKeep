# Paperkeep — Build Progress

> **Claude Code:** Read `CLAUDE.md` first, then this file, then `docs/PROMPT.md`. Find the first unchecked `[ ]` task. That's your job.
> **TEST-GATE:** For EVERY task — BUILD → write TESTS → RUN → all PASS → ONLY THEN check the box.
> **Product:** Paperkeep (Android-only, no backend, top-notch security). See `docs/PAPERKEEP_DESIGN.md` for the full spec.

---

## Current state (2026-04-23)

**Status:** v1 pivoted → v2 Paperkeep started · AWS fully torn down · `scrape/` deleted · docs on Paperkeep branding
**Last session:** 2026-04-23 — AWS teardown (P1.0), Privacy Policy & ToS rewrite (P1.5), `scrape/` deleted and committed (P1.3)
**Next task:** `P1.1` — Rename codebase (ScanVault → Paperkeep) across `android/`

### What exists from v1 that survives the pivot

Android modules (keep and rename in P1.1):
- `:core:{ui, common, crypto, data, domain, imaging, ml, pdf, security, ads}`
- `:feature:{scanner, library, reader, settings, onboarding}`
- `:benchmark`, `:store`, `:app`

Android modules scheduled for DELETE in P1.2: `:core:network`, `:feature:account`, `:feature:sync`.

Non-Android trees have been fully deleted (2026-04-23, P1.3): `backend/`, `intelligence/`, `infra/`, `ota/`, `deploy/`, 12 backend scripts, 7 backend workflows, `Makefile`, `nuke-config.yml`, `costs.csv`. All reachable via `git log` if ever needed.

Design docs to keep: `docs/PAPERKEEP_DESIGN.md`, `docs/PROMPT.md`, `docs/PRIVACY_POLICY.md`, `docs/TERMS_OF_SERVICE.md`.

---

## PHASE 1 — Cleanup + Foundation + Real-Time Capture

> **Spec:** `docs/PAPERKEEP_DESIGN.md` §5 (Phase 1) and §10 (migration)
> **Goal:** Rename to Paperkeep, delete dead code, ship a working camera screen with encrypted save, cold start < 800ms, zero network traffic.

### Cleanup (do these FIRST — they unblock everything)

- [x] **P1.0** — AWS teardown. **Completed 2026-04-23** — user manually removed every resource from the AWS account. No Aurora, Lambda, API Gateway, S3, ECR, Secrets Manager, CloudWatch, or VPC resources remain. $0 ongoing cost.
- [ ] **P1.1** — Rename. Global find-replace across `android/` only: `ScanVault` → `Paperkeep`, `scanvault` → `paperkeep`, `com.scanvault.app` → `app.paperkeep`. Update `applicationId` in `:app/build.gradle.kts`, all `namespace = ` declarations in module `build.gradle.kts` files, `strings.xml` app label, `AndroidManifest.xml` package references. Reset `VERSION` to `2.0.0-alpha.1`. Verify `assembleDebug` still builds.
- [ ] **P1.2** — Delete dead Android modules. Remove dirs `android/core/network/`, `android/feature/account/`, `android/feature/sync/`. Remove them from `settings.gradle.kts`. Delete imports of these modules in all `build.gradle.kts` files. Delete Hilt modules/interfaces that referenced them. Delete navigation graph entries for account/sync screens. Verify `assembleDebug` builds. If code in `:app` references account/sync features, stub with TODO comments and track in P1.8.
- [x] **P1.3** — `scrape/` deleted and committed. **Completed 2026-04-23.** All v1 legacy trees (backend, intelligence, infra, ota, deploy, old scripts/workflows, Makefile, nuke-config.yml, costs.csv) are gone from the working tree; recoverable via `git log` if ever needed.
- [ ] **P1.4** — Update the 3 kept workflows (`android-ci.yml`, `android-release.yml`, `security-scan.yml`) so they no longer reference any backend/infra paths. `android-ci.yml` should run: `lint → detekt → unit tests → instrumented tests on emulator → assembleRelease → upload to Firebase App Distribution`. Merge `security-scan.yml` into it if duplicative.
- [x] **P1.5** — Privacy Policy & Terms of Service rewritten for Paperkeep v2 (not just renamed — the v1 versions described a backend world that no longer exists: cloud sync, accounts, AWS, server-side AI). New content: no-account/no-server reality, on-device AI only, SAF-based user-initiated backups, AdMob + Play Billing + Play Integrity as the only third parties, one-time Pro IAP (not subscription), GDPR/CCPA clarified as N/A because we hold no server-side personal data. **Completed 2026-04-23.**

### Foundation + capture (Phase 1 proper)

- [ ] **P1.6** — Audit `:core:ui` design system against `docs/PAPERKEEP_DESIGN.md` §7. Add Expressive motion tokens (spring stiffness 380 / damping 0.85, and the tighter 600/0.85 for edge-corner snap). Add haptic tokens (CONFIRM, TEXT_HANDLE_MOVE, LONG_PRESS, REJECT). Accent color set to saffron `#F59E0B`. Verify dynamic color works on Android 12+.
- [ ] **P1.7** — `:feature:onboarding` — verify 3-screen onboarding copy matches §7 "Onboarding copy (v2, locked)". Replace any "account" / "sync" language. Skippable, shown once via DataStore flag.
- [ ] **P1.8** — Remove any settings / library / app entry points that referenced the deleted account or sync features. Replace the "Sync" settings section with a placeholder "Backup & restore" section that's wired in Phase 4 (P4.x).
- [ ] **P1.9** — Verify camera screen still meets §5 Phase 1 criteria after rename: CameraX preview, edge detection overlay ≤ 16ms/frame on downsampled 640px frame, pinch zoom, tap-to-focus, torch toggle, grid overlay, batch-mode toggle, bottom session-buffer strip. Fix any regressions caused by the module deletions.
- [ ] **P1.10** — Verify crop screen still works: 4 draggable handles, 50×50 magnifier lens (2× zoom), rotate 90°, retake/next. Debounced re-warp at 30fps.
- [ ] **P1.11** — Verify `:core:crypto` + `:core:data` `EncryptedImageStore` — per-page AES-256-GCM encryption, master key in Keystore (StrongBox when available), thumbnail encrypted. Add a passing instrumented test that proves the raw `.enc` file is unreadable without the master key.
- [ ] **P1.12** — Proxy-verify zero network traffic. Run the app through `mitmproxy` for 5 minutes across onboarding + camera + capture + crop + save. Zero requests observed. Add a doc note to `docs/PAPERKEEP_DESIGN.md` §5 Phase 1 acceptance with the date of verification.
- [ ] **P1.13** — Macrobenchmark cold-start baseline. Document current cold-start time on Pixel 6a (or the closest available device/emulator) in a new `benchmark/BASELINES.md`. Target for Phase 5 is < 500ms; Phase 1 floor is < 800ms.

**Phase 1 acceptance** (`docs/PAPERKEEP_DESIGN.md` §5 Phase 1):
- [ ] Cold start < 800ms on target device
- [ ] Edge detection overlay 60fps (Macrobenchmark)
- [ ] APK < 18 MB
- [ ] Capture → encrypted save < 2s round trip
- [ ] Zero network calls verified via mitmproxy
- [ ] Rotation preserves state
- [ ] Detekt clean, R8 release build succeeds
- [ ] No references to `ScanVault`, `com.scanvault`, or any backend module anywhere in `android/`

---

## PHASE 2 — Library, OCR, PDF Export, Biometric Lock

> **Spec:** `docs/PAPERKEEP_DESIGN.md` §5 (Phase 2)
> **Depends on:** Phase 1 complete
> **Goal:** End-to-end offline flow: capture 10 pages → reorder → filter → searchable PDF → share. Biometric lock shipped.

- [ ] **P2.1** — Data model audit. Confirm `DocumentEntity`, `PageEntity`, `FolderEntity`, `PageOcrEntity` match §4. Add `isFavorite`, `isArchived`, `docType`, `colorTag` fields if missing.
- [ ] **P2.2** — Searchable OCR index. Implement the HMAC'd FTS4 scheme from §6.4: `page_ocr_fts` stores HMAC-SHA256(K_search, normalized_token) hex. Drop 1- and 2-character tokens. K_search lives in Keystore. Add instrumented test: insert OCR text, search returns correct page, raw DB inspection shows only opaque hex.
- [ ] **P2.3** — Library screen. Compose `LazyVerticalStaggeredGrid` (2 cols phone, 4 cols tablet). Card = thumbnail + title + page count + relative timestamp + "on device" pill. Long-press multi-select with bottom action bar. Sort menu. Empty state illustration + single accent CTA.
- [ ] **P2.4** — Folders (one level). System folders: All / Favorites / Archive. User folders with icon + optional auto-rule field (rule wiring is P4.x). Drag-to-move onto folder chip.
- [ ] **P2.5** — Full-text search UI. Search bar collapses on scroll. Results highlight matched snippet with 50-char context each side. < 200ms over 100 docs (benchmark-verify).
- [ ] **P2.6** — Multi-page capture flow. Batch mode accumulator, reorder screen with `Reorderable`, delete/retake/add-more.
- [ ] **P2.7** — Image filters in `:core:imaging`: Original / Auto (TFLite picks) / Magic Color (CLAHE + saturation) / Grayscale / B&W (adaptiveThreshold Gaussian). Non-destructive — flag on `PageEntity`, original preserved. Filter preview strip on crop screen.
- [ ] **P2.8** — OCR pipeline in `:core:ml`. ML Kit Text Recognition v2 runs on `Dispatchers.Default` immediately after capture. Encrypted OCR envelope + bboxes written. Library card shows "Processing…" pill until done. Language downloader screen in settings (Devanagari/Chinese/Japanese/Korean/Arabic via `ModuleInstallClient`).
- [ ] **P2.9** — Searchable PDF export in `:core:pdf`. `PdfDocument` image layer + PDFBox-Android invisible text layer at bboxes. Page size auto/A4/Letter/Legal, JPEG quality 60/85/95. Output to `cacheDir/exports/<docId>.pdf`. 60s auto-cleanup worker. Share via FileProvider.
- [ ] **P2.10** — Other exports: JPEG (single or ZIP), PNG, plain text (OCR dump), encrypted ZIP (Zip4j + user password with Argon2id KDF per §6.1).
- [ ] **P2.11** — Reader in `:feature:reader`. `HorizontalPager`, pinch-zoom, bottom bar (share/delete/rename/reorder/add page/export), OCR overlay toggle with selectable transparent text at bboxes. `FLAG_SECURE` default ON (per-Activity, not per-Composable — see §6.5).
- [ ] **P2.12** — Biometric lock in `:core:security`. `BiometricPrompt` with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`. Settings toggle + timeout (immediate/30s/1m/5m). Process-global `LockController` intercepts nav entries. Cannot be bypassed by kill-and-relaunch (lock state derived from Keystore key availability, not a boolean).
- [ ] **P2.13** — Settings scaffold. Sections: Security (biometric, screenshot protection), Scanning defaults, Language packs, About (version, OSS licenses, privacy policy link → native Compose screen, not WebView).
- [ ] **P2.14** — Recent-apps redaction. Set task description with neutral "Paperkeep + lock icon" so the task-switcher thumbnail never shows document content, even beyond FLAG_SECURE's guarantees.
- [ ] **P2.15** — Clipboard hygiene. When copying OCR text, set `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+). Auto-clear our clipboard entries after 60s.

**Phase 2 acceptance** (`docs/PAPERKEEP_DESIGN.md` §5 Phase 2):
- [ ] E2E: capture 10 pages → reorder → filter → export searchable PDF → share to WhatsApp, all offline
- [ ] OCR ≥ 95% on clean A4 (20-doc ground truth)
- [ ] Library scroll 60fps with 500 docs
- [ ] APK < 22 MB
- [ ] Exported PDF has selectable text in Adobe Reader
- [ ] Batch delete of 50 docs < 1s and files actually gone from disk
- [ ] Biometric lock survives process death

---

## PHASE 3 — Smart Modes, AdMob, Play Store Prep

> **Spec:** `docs/PAPERKEEP_DESIGN.md` §5 (Phase 3)
> **Depends on:** Phase 2 complete
> **Goal:** Features that beat CamScanner + monetization wired up + Play Store assets ready.

- [ ] **P3.1** — TFLite document-type classifier: receipt / ID / business card / A4 / whiteboard / book. Chip on crop screen, tappable override. Auto-applies best filter + aspect.
- [ ] **P3.2** — ID card mode: front + back auto-composed on a single A4 page.
- [ ] **P3.3** — Receipt mode: taller aspect, aggressive B&W, regex-extract total/date/merchant into searchable fields.
- [ ] **P3.4** — Whiteboard mode: glare removal via OpenCV `inpaint`, HSV marker boost, hand/shadow removal.
- [ ] **P3.5** — Book scan mode: two-page split, DewarpNet-lite spine flattening.
- [ ] **P3.6** — Signature tool: draw on transparent overlay, save up to 3 encrypted signatures, place/resize on PDF page before export.
- [ ] **P3.7** — Annotations: text boxes, highlighter, eraser, undo/redo (30-step stack).
- [ ] **P3.8** — True destructive redaction. User rectangle destroys underlying pixels AND overwrites corresponding OCR bboxes in DB. Verify destructive via raw-pixel inspection test.
- [ ] **P3.9** — Image cleanup actions: "Remove background noise" (bilateral + morph opening), "Sharpen text" (unsharp mask), "Fix lighting" (CLAHE). Non-destructive.
- [ ] **P3.10** — `:core:ads` AdMob integration. Lazy init (NOT in `Application.onCreate`). UMP consent. Interstitial after every 5th export, hard cap 1-per-3-min. No banners anywhere. No ads on camera or reader. Domain-allowlist `OkHttp` interceptor (see §6.7).
- [ ] **P3.11** — Play Billing shelf. `BillingClient` wired, one product queried (`paperkeep_pro_lifetime`), purchase flow stubbed. Unlock logic disabled until Phase 5.
- [ ] **P3.12** — In-app rating prompt via `ReviewManager`. Trigger: ≥3 exports AND ≥3 distinct days AND no prompt in last 90 days.
- [ ] **P3.13** — APK signature pin. Bake signing-cert SHA-256 into release builds. On launch, compare `PackageInfo.signingInfo.apkContentsSigners[0]` to baked constant. Mismatch → silently disable AdMob + Pro IAP + backup creation.
- [ ] **P3.14** — Root/Magisk/Frida/emulator detection per §6.6. Best-effort; does NOT block app; disables Pro IAP unlock and backup creation on rooted devices with a polite info card.
- [ ] **P3.15** — Play Store assets in `:store`. EN listing copy (Paperkeep title + keyword-loaded short description), 8 feature-narrative screenshots, 1024×500 feature graphic, hosted privacy policy link (copy served from `docs/PRIVACY_POLICY.md`), Data Safety form draft answers ("no data collected").
- [ ] **P3.16** — Closed testing track on Play Console with ≥ 12 opted-in testers. Starts the 14-day clock — do this the moment the Phase 3 build is stable, not at the end.

**Phase 3 acceptance** (`docs/PAPERKEEP_DESIGN.md` §5 Phase 3):
- [ ] AdMob test ads render correctly
- [ ] UMP consent works in EU locale (VPN)
- [ ] All 4 smart modes visibly beat the default pipeline on test fixtures
- [ ] Redaction is destructive (raw-pixel test)
- [ ] APK < 28 MB
- [ ] Closed testing live, 14-day window started
- [ ] No Phase 1 or 2 regressions

---

## PHASE 4 — Local Backup, Storage Manager, Deep Polish

> **Spec:** `docs/PAPERKEEP_DESIGN.md` §5 (Phase 4)
> **Depends on:** Phase 3 complete
> **Goal:** Replace the dead cloud-sync value prop with a backup experience so good users don't miss the cloud. A11y + i18n + widgets.

- [ ] **P4.1** — `:core:backup` module. SAF-based `ACTION_CREATE_DOCUMENT` → user picks any location (Drive/Dropbox/internal/SD/USB-OTG). Zip4j AES-256 with Argon2id(m=128MiB, t=4) KDF from user password.
- [ ] **P4.2** — Backup contents: re-encrypted page files (backup password, NOT K_master — backups are portable), Room DB dump, settings JSON, `manifest.json` with version + SHA-256 integrity. Write `BackupEntity` row with persistable SAF URI.
- [ ] **P4.3** — Backup password strength meter inline (zxcvbn-style but implemented locally, not as a network-touching lib). Minimum 10 chars.
- [ ] **P4.4** — Backup reminders. AlarmManager-based local notification, user picks cadence (never / weekly / monthly). Respects `POST_NOTIFICATIONS` permission on API 33+.
- [ ] **P4.5** — Restore flow. SAF pick → password prompt → progress indicator → conflict strategy (merge / replace). Restored docs land in a "Restored <date>" folder.
- [ ] **P4.6** — Smart storage manager in settings. Pie chart of space (scans / cache / exports / OCR). Actions: clear export cache, bulk recompress pages older than 6 months (JPEG 85→70, optional original retention), empty trash, move selected docs to SD card.
- [ ] **P4.7** — Folder auto-rules. "docType=receipt → Receipts folder" runs on save. Configure per folder in a simple dialog.
- [ ] **P4.8** — Dark mode polish. Every screen tested light + dark, Expressive dynamic color. Optional OLED-true-black toggle for AMOLED.
- [ ] **P4.9** — Accessibility pass. Every interactive element `contentDescription`, TalkBack-tested golden path (capture → save), ≥ 48dp touch targets, ≥ 4.5:1 contrast, 200% font scaling doesn't break layouts, reduced-motion honored.
- [ ] **P4.10** — Localization. EN + Hindi, Nepali, Spanish, Portuguese, Arabic (RTL tested end-to-end), French, German, Indonesian. Top-3 hand-reviewed.
- [ ] **P4.11** — Widgets. "Scan now" home widget (Glance), "Recent scans" 1×4 thumbnail widget.
- [ ] **P4.12** — Quick Settings tile. One-tap scan from the system shade.
- [ ] **P4.13** — Share receiver. `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent filter in `:app` with strict MIME allowlist (`image/jpeg|png|heic|webp`, `application/pdf`) and 50 MB size cap. Imports as new document, runs OCR + classifier.
- [ ] **P4.14** — Crash handler. `Thread.setDefaultUncaughtExceptionHandler` writes encrypted crash log to `filesDir/crash/`. "Send crash report" button in settings — user manually attaches to email. Nothing auto-uploads.

**Phase 4 acceptance** (`docs/PAPERKEEP_DESIGN.md` §5 Phase 4):
- [ ] Backup → wipe app data → restore → every document opens, OCR intact, passwords match
- [ ] Storage-manager recompression saves ≥ 30% on 200-page test library
- [ ] TalkBack can complete capture→save end-to-end
- [ ] Arabic RTL correct on every screen
- [ ] Widgets render on Pixel Launcher and Samsung One UI
- [ ] APK signature tamper-check flips the AdMob/Pro/backup flag correctly when re-signed

---

## PHASE 5 — Summarizer, Pro IAP, Performance, Launch

> **Spec:** `docs/PAPERKEEP_DESIGN.md` §5 (Phase 5)
> **Depends on:** Phase 4 complete
> **Goal:** One AI feature CamScanner doesn't have, light up the Pro IAP, squeeze performance, ship to production.

- [ ] **P5.1** — On-device summarizer. Distilled TFLite text model (~30 MB, downloaded on first opt-in, cached on `filesDir/models/`). 1-sentence summary + 3–5 key phrases per document. 5-second timeout → silent fallback. Free-tier cap: 3 summaries/day.
- [ ] **P5.2** — Pro IAP live. `paperkeep_pro_lifetime`, $4.99, Play-localized pricing. Unlocks: no ads, batch export >5 pages, unlimited summarizer, Pro visual theme. One-time purchase, no subscription.
- [ ] **P5.3** — Play Integrity API gate for Pro unlock. Verify on-device with Google's public key; require `meetsDeviceIntegrity` minimum. Rooted devices get a clear "Pro unavailable on this device" message (we don't silently take their money).
- [ ] **P5.4** — Baseline Profiles. Macrobenchmark harness generates profiles for cold start, library scroll, camera launch, PDF export. Ship with release APK.
- [ ] **P5.5** — R8 full-mode audit. Verify Hilt + kotlinx.serialization reflection survives. Tree-shake unused OpenCV modules (target ≥ 15 MB savings).
- [ ] **P5.6** — Performance-pass targets: cold start < 500ms on Pixel 6a, warm start < 200ms, library scroll 60fps with 1000 docs, memory < 150 MB, LeakCanary clean, 1hr scanning drains < 8% of a 4000mAh battery.
- [ ] **P5.7** — Security release gates (automated CI checks per `docs/PAPERKEEP_DESIGN.md` §6.12): Detekt, OWASP dep-check (zero CRITICAL/HIGH), Gradle dependency verification, `apksigner verify` reports v2+v3, manifest audit script, `grep -r "Log\." app/src/main` returns zero, MobSF scan zero High findings, Play Pre-Launch Report clean.
- [ ] **P5.8** — `SECURITY.md` at repo root with PGP key + responsible-disclosure window (90 days). Set up `security@paperkeep.app` forwarding to personal inbox (or use the real contact email if the domain isn't yet owned).
- [ ] **P5.9** — Final Play Store submission. Closed testing → open testing → production. Tier-1 launch: US, UK, CA, AU, DE, FR. Staged rollout 10 → 50 → 100% over 2 weeks. Data Safety honest: "no data collected" + device IDs for AdMob only.
- [ ] **P5.10** — Post-launch manual monitoring. Daily Play Console check (crash rate < 0.5%, crash-free users > 99.5%), weekly 1-star review triage, daily AdMob dashboard (eCPM + fill rate).

**Phase 5 acceptance** (`docs/PAPERKEEP_DESIGN.md` §5 Phase 5):
- [ ] All perf targets met on Pixel 6a
- [ ] APK < 30 MB (target < 25 MB)
- [ ] Play Pre-Launch Report clean
- [ ] 12 closed testers complete 14-day window
- [ ] Production live
- [ ] ≥ 50 organic installs week 1
- [ ] Avg rating ≥ 4.3 after first 20 reviews

---

## Notes for future sessions

- **Trademark check before P5.9:** USPTO TESS + EUIPO search for "PAPERKEEP" in classes 9 and 42. Reserve `app.paperkeep` package on Play Console. Buy `paperkeep.app` (and `.com` if available). See `docs/PAPERKEEP_DESIGN.md` §0.
- **Do NOT re-introduce a backend.** Every time you are tempted, re-read `docs/PROMPT.md` §1.
- **Security section is the contract.** Every PR touching keys, storage, auth, or integrity re-reads `docs/PAPERKEEP_DESIGN.md` §6 in full.
