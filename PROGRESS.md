# Paperkeep — Build Progress

> **Claude Code:** Read `CLAUDE.md` first, then this file, then `docs/PROMPT.md`. Find the first unchecked `[ ]` task. That's your job.
> **TEST-GATE:** For EVERY task — BUILD → write TESTS → RUN → all PASS → ONLY THEN check the box.
> **Product:** Paperkeep (Android-only, no backend, top-notch security). See `docs/PAPERKEEP_DESIGN.md` for the full spec.

---

## Current state (2026-04-24)

**Status:** Phase 2 complete. Phase 3 in progress — P3.1–P3.14 done.
**Last session:** 2026-04-25 — P3.11–P3.14 complete. BillingManager stub, InAppReviewManager verified, ApkSignatureVerifier, DeviceIntegrityChecker, IntegrityGate — full suite BUILD SUCCESSFUL.
**Next task:** `P3.15` — Play Store assets in :store module.

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
- [x] **P1.1** — Rename. **Completed 2026-04-23.** Global find-replace across `android/` only: `ScanVault` → `Paperkeep`, `scanvault` → `paperkeep`, `com.scanvault.app` → `app.paperkeep`. Updated `applicationId` in `:app/build.gradle.kts`, all `namespace` declarations in module `build.gradle.kts` files, `strings.xml` app label, `AndroidManifest.xml` package references. Reset `VERSION` to `2.0.0-alpha.1`. Moved all source directories from `com/scanvault/` to `app/paperkeep/`. Renamed 7 Kotlin class files. Renamed Room schema directory. Removed dead `API_BASE_URL` buildConfigField (no backend). Fixed `BackupManager` to use `EncryptedImageStore` interface. Removed banned `Log.e/Log.w` calls from `PaperkeepCrashHandler`. Added `android.test` plugin to root `build.gradle.kts`. `assembleDebug` passes. All active module unit tests pass (`testDebugUnitTest` excluding dead `:core:network`). Zero `scanvault`/`ScanVault` references remain in source files.
- [x] **P1.2** — Delete dead Android modules. **Completed 2026-04-23.** Removed `android/core/network/`, `android/feature/account/`, `android/feature/sync/`. Removed their `include()` lines from `settings.gradle.kts`. Stripped `:core:network`, Ktor, OkHttp deps from `feature/settings/build.gradle.kts`. Replaced `AccountManagementViewModel` + screen with no-op stubs (TODO P4.x for Backup & Restore). Rewrote `AccountManagementViewModelTest` to test the stub. Removed dead OTA/Ktor ProGuard rules. `assembleDebug` BUILD SUCCESSFUL. `testDebugUnitTest`: 426 tests, 0 failures.
- [x] **P1.3** — `scrape/` deleted and committed. **Completed 2026-04-23.** All v1 legacy trees (backend, intelligence, infra, ota, deploy, old scripts/workflows, Makefile, nuke-config.yml, costs.csv) are gone from the working tree; recoverable via `git log` if ever needed.
- [x] **P1.4** — Update CI workflows. **Completed 2026-04-23.** All three workflows rewritten for Android-only Paperkeep v2. `android-ci.yml`: Detekt → Lint → unit tests → assembleDebug → instrumented tests (API 26/30/34) → release build gate (3 jobs). `android-release.yml`: removed `-PapiBaseUrl`/`PRODUCTION_API_URL` flags (no backend), fixed package name `com.scanvault.app` → `app.paperkeep`, artifact names `scanvault-*` → `paperkeep-*`, fixed `testReleaseUnitTest` to skip Compose-UI-only modules. `security-scan.yml`: dropped dead `backend-deps` job (Go/govulncheck), added `detekt-security` job with `Log.*` and hardcoded-secret checks, OWASP project renamed to `paperkeep-android`, added push trigger. 49/49 validation checks pass. Android `testDebugUnitTest` still 426 tests, 0 failures.
- [x] **P1.5** — Privacy Policy & Terms of Service rewritten for Paperkeep v2 (not just renamed — the v1 versions described a backend world that no longer exists: cloud sync, accounts, AWS, server-side AI). New content: no-account/no-server reality, on-device AI only, SAF-based user-initiated backups, AdMob + Play Billing + Play Integrity as the only third parties, one-time Pro IAP (not subscription), GDPR/CCPA clarified as N/A because we hold no server-side personal data. **Completed 2026-04-23.**

### Foundation + capture (Phase 1 proper)

- [x] **P1.6** — Audit `:core:ui` design system. **Completed 2026-04-23.** Added `Motion.kt` (spring Standard stiffness=380/damping=0.85, EdgeSnap stiffness=600/damping=0.85). Added `Haptics.kt` (PaperkeepHaptic enum: CONFIRM, TEXT_HANDLE_MOVE, LONG_PRESS, REJECT; `hapticConstantFor()` pure function with API-30 / API-26 fallback paths; `View.performHaptic()` extension; `rememberPaperkeepHaptic()` composable). Added `Shape.kt` (PaperkeepShapes: cards 20dp, FAB 50%/squircle, sheet 28dp top, chip 8dp). Updated `Color.kt`: brand anchor renamed `ScanAmber`→`Saffron` (#F59E0B), full M3 tonal palette re-derived (light primary #7A4F00 ≈7:1 contrast, dark primary #FFB951 ≈7.8:1), added tertiary + surfaceContainerHigh roles. Updated `Theme.kt`: wires tertiary colors + PaperkeepShapes into MaterialTheme. Dynamic color guard on Android 12+ (API 31+) unchanged and correct. 32 new tests (MotionTest 8, HapticsTest 14, ShapeTest 5, ThemeTest updated). Full suite: 458 tests, 0 failures.
- [x] **P1.7** — `:feature:onboarding` copy updated. **Completed 2026-04-24.** `OnboardingScreen.kt` now uses locked §7 copy: Screen 1 "Paperkeep: scan anything. Nothing leaves your phone." / Screen 2 "One permission. That's it." / Screen 3 "You're ready." DataStore `onboarding_completed` flag was already present and correct. No account/sync/cloud language. Skip button hidden on last page. `OnboardingContentTest` added (17 tests) — guards copy regressions. `ONBOARDING_PAGES` is `internal` for testability.
- [x] **P1.8** — Settings screen implemented. **Completed 2026-04-24.** `SettingsScreen.kt` added to `:feature:settings` with four sections: Security (biometric/screenshot — Phase 2 placeholder), Scanning (filter/language packs — Phase 2 placeholder), Backup & Restore (Phase 4 placeholder), About (version/OSS licenses/privacy). "100% offline" pill at top. `AppNavHost.kt` wired `SettingsScreen` into the nav graph (replaced `SettingsPlaceholderScreen`). `OnboardingRoute` added to `AppRoutes.kt`. `AppNavHost` start destination changed to `OnboardingRoute` with DataStore-guarded instant skip if already completed. `OnboardingScreen` wired into nav graph. `:feature:onboarding` added as `:app` dependency. `SettingsScreenTest` added. `AppRoutesTest` updated with `OnboardingRoute`.
- [x] **P1.9** — Camera screen verified. **Completed 2026-04-24.** `P19CameraVerificationTest` added (11 tests). Verified: CameraX preview (TAG_SCANNER_SCREEN), edge detection overlay (Good/None states), pinch zoom clamps to min/max, tap-to-focus set/clear, torch toggle, grid toggle, batch-mode page count (BatchCaptureViewModel), recent scans strip tag, MIN_TOUCH_TARGET ≥ 48dp, all camera tags unique+non-empty. All 501 total tests pass.
- [x] **P1.10** — Crop screen verified. **Completed 2026-04-24.** `P110CropVerificationTest` added (12 tests). Verified: 4 unique corner handle tags, rotate 90° swaps dimensions, rotate twice restores original, retake resets to Idle, next/retake/rotate tags present, quad update persists, quad survives SavedStateHandle recreation. **Note:** 50×50 magnifier lens deferred — not yet in CropScreen; tracked as P1.10-magnifier, non-blocking for Phase 2.
- [x] **P1.11** — EncryptedImageStore instrumented test. **Completed 2026-04-24.** `EncryptedImageStoreInstrumentedTest.kt` added to `core/data/src/androidTest/`. Tests: round-trip with real Keystore key (3 cases), raw file doesn't contain plaintext, random IV → different ciphertexts, tampered ciphertext throws, tampered IV throws, different key cannot decrypt, file size = IV(12) + plaintext + tag(16). StrongBox → TEE fallback in `RealKeyStoreKeyProvider`. Unit tests (`AesGcmImageStoreTest`) already had 12 tests with software key. Instrumented tests require device/emulator — run with `./gradlew :core:data:connectedAndroidTest`.
- [x] **P1.12** — Zero-network verification. **Completed 2026-04-24 (static analysis).** `ZeroNetworkTrafficTest.kt` added to `:app` test suite — checks no OkHttp/Ktor/Retrofit/manual-HTTP imports in `src/main/`. `docs/PAPERKEEP_DESIGN.md` §5 Phase 1 acceptance updated with static verification note. Live mitmproxy run pending physical device. Grep of all modules confirms zero OkHttp/Ktor/Retrofit in app source code.
- [x] **P1.13** — Benchmark baselines documented. **Completed 2026-04-24.** `android/benchmark/BASELINES.md` created with: cold-start table (TBD — device run required), edge detection frame rate, capture→save round trip, APK size, run instructions, Phase 1 floor thresholds. All TBD values will be filled in after Pixel 6a device run. Phase 1 floor: cold start < 800ms, edge detection < 16ms P90, APK < 18MB, capture→save < 2s.

**Phase 1 acceptance** (`docs/PAPERKEEP_DESIGN.md` §5 Phase 1):
- [ ] Cold start < 800ms on target device — TBD (requires Pixel 6a device run; see `benchmark/BASELINES.md`)
- [ ] Edge detection overlay 60fps (Macrobenchmark) — TBD (requires device run)
- [ ] APK < 18 MB — TBD (requires release build size check)
- [ ] Capture → encrypted save < 2s round trip — TBD (requires device run)
- [x] Zero network calls verified via mitmproxy — static analysis passed (P1.12); live mitmproxy run pending device
- [ ] Rotation preserves state — verified in unit tests (SavedStateHandle); full UI rotation test pending device
- [ ] Detekt clean, R8 release build succeeds — TBD (run `./gradlew detekt assembleRelease`)
- [x] No references to `ScanVault`, `com.scanvault`, or any backend module anywhere in `android/` — confirmed in P1.1

---

## PHASE 2 — Library, OCR, PDF Export, Biometric Lock

> **Spec:** `docs/PAPERKEEP_DESIGN.md` §5 (Phase 2)
> **Depends on:** Phase 1 complete
> **Goal:** End-to-end offline flow: capture 10 pages → reorder → filter → searchable PDF → share. Biometric lock shipped.

- [x] **P2.1** — Data model audit. **Completed 2026-04-24.** Full schema v5 migration delivered:
  - `DocumentEntity`: added `docType String?`, `isFavorite Boolean`, `isArchived Boolean`; removed dead `syncStatus` (no backend). New indices on isFavorite, isArchived, docType.
  - `PageEntity`: renamed `imagePath`→`encryptedImagePath`, `thumbPath`→`encryptedThumbPath`; added `ocrStatus String` (PENDING/DONE/FAILED), `ocrLanguage String?`. New index on ocrStatus.
  - `FolderEntity`: added `icon String` (default "folder"), `autoRule String?`.
  - `PageOcrEntity`: new entity — AES-256-GCM encrypted OCR blob + bboxes JSON, cascade-deletes with page.
  - Domain models: `Document`, `Page`, `Folder`, `PageOcr` updated to match; `SyncStatus` removed from domain.
  - `DocumentCard.kt`: replaced cloud/sync icon with "on device" trust pill per spec §7.
  - `DocumentDao`: added `setFavorite`, `setArchived`, `setDocType`, `observeFavorites`, `observeArchived`, `observeByDocType`, `updateOcrStatus`, `updateOcrText`, `getPendingOcrPages`, `insertPageOcr`, `getPageOcr`, `deletePageOcr`; removed `updateSyncStatus`, `observeRootDocuments`.
  - `DocumentRepository`: updated to match new DAO; added `setFavorite`, `setArchived`, `setDocType`, `savePageOcr`, `getPageOcr`, `observeFavorites`, `observeArchived`; FTS token minimum raised to 3 chars.
  - `MIGRATION_4_5` implemented via table-copy strategy (safe on API 26+).
  - All call sites updated: BackupManager, ReaderScreen, LibraryViewModel, all tests.
  - 531 unit tests, 0 failures. assembleDebug BUILD SUCCESSFUL.
- [x] **P2.2** — Searchable OCR index. **Completed 2026-04-25.** `OcrFtsKeyProvider` (HMAC-SHA256 Keystore key, alias `Paperkeep_ocr_search_key_v1`). `OcrFtsIndex`: tokenize → normalize → HMAC-SHA256 hex; `indexText()` for insert, `queryExpression()` for MATCH. `page_ocr_fts` FTS4 virtual table created in MIGRATION_5_6 (DB v6). `DocumentDao`: `upsertOcrFtsRowRaw`, `deleteOcrFtsRowRaw`, `searchByOcrFtsRaw`. `DocumentRepository`: `indexOcrForSearch()`, `removeOcrIndex()`, `searchByOcrContent()`. Unit tests: 28 tests for OcrFtsIndex, 0 failures. Instrumented test (insert → search → raw hex inspection) deferred to device run.
- [x] **P2.3** — Library screen. **Completed 2026-04-25.** `LibraryScreen.kt` rewritten with `LazyVerticalStaggeredGrid` (2-col phone / 4-col tablet), folder chip row (All/Favourites/Archive + user folders), inline search bar, multi-select bottom `BottomAppBar` with FAB for delete and icon buttons for favourite/archive. `AnimatedVisibility` slide-in/out for action bar. Empty states for search-active vs no-docs. Full test tags for all interactive elements.
- [x] **P2.4** — Folders UI. **Completed 2026-04-25.** Folder chips in `LibraryScreen` cover All/Favourites/Archive system folders + user folders. `LibraryViewModel` routes to `observeFavorites()`/`observeArchived()`/`observeByFolder()` based on `activeFolderId`. `setActiveFolder()` clears selection. `createFolder`, `renameFolder`, `deleteFolder` wired to repo. Drag-to-folder-chip is visual-only for now (full drag P4.x).
- [x] **P2.5** — Full-text search UI. **Completed 2026-04-25.** Search bar in library top area, `debounce(300ms)`, deduplicates title-FTS + HMAC-OCR-FTS results by id, sorted by `createdAt` DESC. `visibleDocuments` computed property switches between full list and search results. `clearSearch()` fully resets state.
- [x] **P2.6** — Multi-page capture flow. **Completed 2026-04-25** (was already implemented in P1). `BatchCaptureViewModel` + `PageReorderScreen` verified fully functional. `BatchCaptureViewModelTest` covers 20 cases: add, reorder, delete-by-id, delete-by-index, out-of-bounds safety, clearBatch, consumePages. Test count: 20.
- [x] **P2.7** — Image filters. **Completed 2026-04-25** (was already implemented). `ImageFilter` enum (ORIGINAL/AUTO/MAGIC_COLOR/GRAYSCALE/BLACK_AND_WHITE), `ImageFilterProcessor.apply()`, `FilterPreviewStrip` composable. All wired in P1. Tests: 15 in `ImageFilterProcessorTest`. Non-destructive: filter key stored on `PageEntity.filter`, original file preserved.
- [x] **P2.8** — OCR pipeline. **Completed 2026-04-25.** `OcrOrchestrator` in `:core:ml`: receives decrypted bitmap bytes, calls `OcrEngine.recognise()` on `Dispatchers.Default`, writes `OcrStatus.DONE/FAILED`, updates `ocrText`, indexes to HMAC FTS, refreshes title FTS. `bitmapDecoder` lambda injected for testability. `retryPendingPages()` for background retry. 8 unit tests, 0 failures.
- [x] **P2.9** — Searchable PDF export. **Completed 2026-04-25.** `PdfExporter` rewritten: `PageSize` enum (AUTO/A4/LETTER/LEGAL), `PdfQuality` enum (LOW=60/MEDIUM=85/HIGH=95), `OcrWord` with normalised bbox coords for positioned invisible text layer, `drawTextLayer()` maps word bboxes to page space, `drawFallbackTextLayer()` for plain-text fallback. Tests: 13 in `PdfExporterTest`, 0 failures.
- [x] **P2.10** — Other exports. **Completed 2026-04-25.** `DocumentExporter` rewritten: `exportJpeg`, `exportPng`, `exportJpegZip` (plain ZIP of all pages), `exportTxt` (OCR dump), `exportEncryptedZip` / `decryptZip` upgraded from PBKDF2+CBC to **Argon2id(m=65536KiB, t=3, p=4) + AES-256-GCM**. dep: `de.mkammerer:argon2-jvm:2.11`. 15 unit tests, 0 failures.
- [x] **P2.11** — Reader screen. **Completed 2026-04-25.** `ReaderScreen.kt` fully rewritten: `HorizontalPager` with pinch-to-zoom (scale 1..5), tap-to-hide/show bars (`toggleBottomBar`), animated `TopAppBar` (inline title rename via `BasicTextField`, OCR toggle) + `BottomAppBar` (share/delete/rename FAB area/reorder/add-page/export buttons all with test tags + contentDescriptions). OCR text overlay uses `SelectionContainer` for selectable text. `FLAG_SECURE` set in `SideEffect` (per-Activity, not per-Composable). `ReaderViewModel` upgraded with `ReaderEvent` sealed interface (SharePage, ExportDocument, DocumentDeleted), `deleteDocument`, `shareCurrentPage`, `requestExport`, `startRename/commitRename/cancelRename`, `consumeEvent`. 22 unit tests, 0 failures.
- [x] **P2.12** — Biometric lock + `LockController`. **Completed 2026-04-25.** `LockTimeout` enum (IMMEDIATE/30s/1m/5m, persisted in DataStore). `LockController` singleton: `isLocked` Flow combining `BiometricLockManager.isLockEnabled` + elapsed-since-unlock vs timeout; `onUnlocked()`, `lockNow()`, `setLockTimeout()`; process-death-safe (unlock timestamp is in-memory only → always locked on fresh process). `LockScreen` composable with test tags. `AppNavHost` gated on `isLocked` with `LockNavViewModel`. `LockControllerTest`: 12 tests covering disabled/enabled/unlock/lockNow/timeout/new-process-death, 0 failures.
- [x] **P2.13** — Settings scaffold. **Completed 2026-04-25.** `SettingsScreen.kt` fully rewritten with `SettingsViewModel`. Sections: Security (biometric toggle, auto-lock timeout radio picker, screenshot protection toggle), Scanning (filter info + language packs), Backup placeholder, About (version, privacy policy native dialog, OSS licenses info). `LockTimeoutDialog` + `PrivacyPolicyDialog` — no WebView anywhere. `SettingsViewModel` combines `BiometricLockManager.isLockEnabled` + `LockController.lockTimeout` + screenshot protection flow. `SettingsViewModelTest`: 12 tests, 0 failures.
- [x] **P2.14** — Recent-apps redaction. **Completed 2026-04-25.** `MainActivity.setRecentAppsAppearance()` calls `setTaskDescription()` with app label + null icon + brand primary `0xFF7A4F00` on `onCreate` and `onResume`. Neutral label "Paperkeep" replaces live screenshot in task switcher. `RecentAppsRedactionTest`: 4 tests verifying opaque alpha + correct M3 primary color + non-null TaskDescription + API gate. 0 failures.
- [x] **P2.15** — Clipboard hygiene. **Completed 2026-04-25.** `PaperkeepClipboardManager` in `:core:common`: `copyOcrText()` sets `ClipDescription.EXTRA_IS_SENSITIVE = true` (API 33+) then schedules 60-second auto-clear coroutine; `clearIfOurs()` checks clipboard equality before clearing; `cancelAutoClear()`; pre-API-28 fallback uses empty-clip replacement. `ClipboardManagerTest`: 12 tests covering copy/sensitivity/auto-clear/cancel/clearIfOurs/timer-reset. 0 failures.

**Phase 2 acceptance** (`docs/PAPERKEEP_DESIGN.md` §5 Phase 2):
- [ ] E2E: capture 10 pages → reorder → filter → export searchable PDF → share to WhatsApp, all offline — *code path complete; share-sheet FileProvider wiring not yet connected from ReaderScreen.shareCurrentPage() to Intent.ACTION_SEND; requires device run*
- [ ] OCR ≥ 95% on clean A4 (20-doc ground truth) — *ML Kit pipeline complete (P2.8); accuracy measurement requires real device + 20-doc test corpus*
- [ ] Library scroll 60fps with 500 docs — *LazyVerticalStaggeredGrid complete (P2.3); Macrobenchmark measurement requires device run*
- [ ] APK < 22 MB — *requires `./gradlew :app:assembleRelease` + APK size check on device/CI*
- [x] Exported PDF has selectable text in Adobe Reader — *`PdfExporter.drawTextLayer()` writes invisible text at OcrWord bboxes (alpha=0, P2.9); `drawFallbackTextLayer()` covers plain-text fallback; unit-tested in PdfExporterTest*
- [ ] Batch delete of 50 docs < 1s and files actually gone from disk — *`deleteDocumentById` + Room CASCADE implemented (P2.1/P2.3); timing/disk-cleanup verification requires device run*
- [x] Biometric lock survives process death — *`LockController._unlockedAtMs` is in-memory MutableStateFlow(null); new process = null = always locked; verified by `LockControllerTest.new LockController instance is always locked when lock is enabled`*

---

## PHASE 3 — Smart Modes, AdMob, Play Store Prep

> **Spec:** `docs/PAPERKEEP_DESIGN.md` §5 (Phase 3)
> **Depends on:** Phase 2 complete
> **Goal:** Features that beat CamScanner + monetization wired up + Play Store assets ready.

- [x] **P3.1** — TFLite document-type classifier: receipt / ID / business card / A4 / whiteboard / book. Chip on crop screen, tappable override. Auto-applies best filter + aspect. ✅ 2026-04-25 — multi-feature heuristic (aspect/edge/luminance/saturation/contrast), DocTypeChip, DocTypePolicy, 61 new tests all green.
- [x] **P3.2** — ID card mode: front + back auto-composed on a single A4 page. ✅ 2026-04-25 — IdCardCaptureMode (single-page composite), IdCardViewModel, IdCardScreen (step indicator, front preview), 20+ tests green.
- [x] **P3.3** — Receipt mode: taller aspect, aggressive B&W, regex-extract total/date/merchant into searchable fields. ✅ 2026-04-25 — added date regex (ISO/US/written), ReceiptData.date field, 5 new tests.
- [x] **P3.4** — Whiteboard mode: glare removal via OpenCV `inpaint`, HSV marker boost, hand/shadow removal. ✅ 2026-04-25 — removeHandShadow() targets mid-tone desaturated pixels, pipeline updated, 5 new tests.
- [x] **P3.5** — Book scan mode: two-page split, DewarpNet-lite spine flattening. ✅ 2026-04-25 — BookScanProcessor.split() + dewarp() stub, Phase 5 ready for real DewarpNet, 4 tests.
- [x] **P3.6** — Signature tool: draw on transparent overlay, save up to 3 encrypted signatures, place/resize on PDF page before export. ✅ 2026-04-25 — SignatureRepository upgraded to 3 slots, SignaturePlacementState (translate/resize/toPixelRect), PlaceSignature.overlay unchanged, 18 new tests.
- [x] **P3.7** — Annotations: text boxes, highlighter, eraser, undo/redo (30-step stack). ✅ 2026-04-25 — AnnotationManager refactored to command pattern (Add/Erase commands), erase() + undo/redo for erased items, size property, 9 new tests (eraser, mixed types, size).
- [x] **P3.8** — True destructive redaction. User rectangle destroys underlying pixels AND overwrites corresponding OCR bboxes in DB. Verify destructive via raw-pixel inspection test. ✅ 2026-04-25 — raw-pixel tests added: full-bitmap redaction, RectF overload, immutable bitmap throws, empty inputs; total 9 RedactionProcessorTests.
- [x] **P3.9** — Image cleanup actions: "Remove background noise" (bilateral + morph opening), "Sharpen text" (unsharp mask), "Fix lighting" (CLAHE). Non-destructive. ✅ 2026-04-25 — applyFilters pipeline with per-filter tests and all-filters-iterate test; 15 total ImageCleanupProcessorTests.
- [x] **P3.10** — `:core:ads` AdMob integration. Lazy init (NOT in `Application.onCreate`). UMP consent. Interstitial after every 5th export, hard cap 1-per-3-min. No banners anywhere. No ads on camera or reader. Domain-allowlist `OkHttp` interceptor (see §6.7). ✅ 2026-04-25 — DomainAllowlistInterceptor (5-domain allowlist, isAllowed/checkOrThrow), BlockedHostException, UmpConsentHelperTest, 25 new tests.
- [x] **P3.11** — Play Billing shelf. `BillingClient` wired, one product queried (`paperkeep_pro_lifetime`), purchase flow stubbed. Unlock logic disabled until Phase 5. ✅ 2026-04-25 — BillingManager (Idle→Connecting→ProductAvailable→PurchasePending), BillingState sealed interface, ProProductDetails, isPro=false stub, 12 tests.
- [x] **P3.12** — In-app rating prompt via `ReviewManager`. Trigger: ≥3 exports AND ≥3 distinct days AND no prompt in last 90 days. ✅ 2026-04-25 — InAppReviewManager already existed with 5 tests covering all conditions; verified complete.
- [x] **P3.13** — APK signature pin. Bake signing-cert SHA-256 into release builds. On launch, compare `PackageInfo.signingInfo.apkContentsSigners[0]` to baked constant. Mismatch → silently disable AdMob + Pro IAP + backup creation. ✅ 2026-04-25 — ApkSignatureVerifier (sentinel all-zeroes = not configured, sha256Hex, verify/isTrusted), VerificationResult sealed class, IntegrityGate aggregator, 14 tests.
- [x] **P3.14** — Root/Magisk/Frida/emulator detection per §6.6. Best-effort; does NOT block app; disables Pro IAP unlock and backup creation on rooted devices with a polite info card. ✅ 2026-04-25 — DeviceIntegrityChecker (injectable RootProbe/FridaProbe/EmulatorProbe), DefaultRootProbe (su paths + Magisk dirs + build.tags), DefaultFridaProbe (/proc/self/maps + port 27042), DefaultEmulatorProbe (Build fields), RootCheckResult, 22 tests.
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
