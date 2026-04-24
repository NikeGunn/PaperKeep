# Paperkeep — Android-Only System Design (v2)

> **Mandate:** The backend is dead. Every AWS dollar is dead. There is no Go service, no Python intelligence layer, no cloud database. The app is **100% on-device, forever.** Storage is the user's phone. Compute is the user's phone. AI is the user's phone. This document is the new north star — it supersedes `FRONTEND_MVP.md` wherever the two disagree.

**Platform:** Android native · **Language:** Kotlin 2.0+ · **UI:** Jetpack Compose Material 3 (Expressive)
**Min SDK:** 26 (Android 8.0) · **Target SDK:** 35 (Android 15)
**App name:** Paperkeep · **Package ID:** `app.paperkeep` (verify + reserve on Play Console before Phase 1) · **Tagline (working):** *"Your paper. Kept on your phone. Nowhere else."*

### Naming — why Paperkeep, and the shortlist

"ScanVault" was the working codename but is close to existing Play Store listings and generic enough to invite trademark friction. We checked availability on the Play Store for ten candidates. **Paperkeep** is the recommended primary:

- No matching Play Store listing found (searches returned unrelated "Document Keeper", "Note Keep", "PaperDoc" — none collide)
- Memorable, two concrete English words, easy to spell and say globally
- Semantically perfect: the whole value prop is "we keep your paper, nothing else touches it"
- Pairs naturally with a lock/vault visual identity for the icon
- Clean domain space — `paperkeep.app` typically available, `.com` may need a check

**Shortlist if Paperkeep is unavailable at filing time** (in priority order):

| Rank | Name | Notes |
|---|---|---|
| 1 | **Paperkeep** | Primary pick. |
| 2 | **Docukeep** | Same shape, slightly more formal tone. |
| 3 | **Scanhaven** | Emphasizes safety/sanctuary; nice for the privacy story. |
| 4 | **Paperlight** | Light + airy; good for a premium/minimal visual identity. |
| 5 | **Scanwise** | Emphasizes the smart-modes feature set. |
| 6 | **Vaultscan** | Direct, but the "vault" framing is crowded. |

**Names explicitly rejected** (confirmed taken or too close to existing Play Store apps): ScanVault, Scanora, PaperScan, Paperly, PrivateScan, Scanify, Kaagaz, Genius Scan, Adobe Scan, CamScanner, Clear Scan, Tiny Scanner, Microsoft Lens.

**Before filing** (do this once, at the start of Phase 1):

1. Search Play Store directly for "Paperkeep" — confirm no exact-name listing.
2. USPTO TESS search (`tmsearch.uspto.gov`) for "PAPERKEEP" in classes 9 (software) and 42 (SaaS). Also search EUIPO if targeting EU at launch.
3. Reserve the package ID `app.paperkeep` on Play Console (reserves the name on the store too).
4. Buy `paperkeep.app` + `.com` if available. If `.com` is squatted, `.app` is enough — Play Store doesn't require a matching domain.
5. Once reserved, do a **global find-and-replace** across the Android module tree: `scanvault` → `paperkeep`, `ScanVault` → `Paperkeep`, `com.scanvault.app` → `app.paperkeep`. Rename Gradle module namespaces and update `applicationId` in `:app/build.gradle.kts`.

Until filing, keep the working codename `Paperkeep` in code and docs. All source citations below still refer to the old directory layout (`android/` at repo root, `com.scanvault.*` packages) — those are rewritten during the Phase 1 rename task.

---

## 0. The Pivot — What Changed and Why

| Old (v1) | New (v2) | Why |
|---|---|---|
| Optional cloud sync via Go + Postgres + S3 | **No cloud.** Local-only. Optional user-driven export to Google Drive via system share sheet. | AWS bill was not survivable pre-revenue. Sync is also the #1 source of complexity, security liability, and support load for a solo dev. |
| Python FastAPI for "enhanced AI" | **On-device TFLite + ML Kit only.** | The hardware in a 2023 mid-range phone runs every model we need. Cloud AI was a vanity feature. |
| Email/password accounts, Argon2id, Paseto, libsodium key wrap | **No account at all.** Device-local encryption with a biometric-gated master key. | Removes the entire auth surface. Zero PII collected = zero GDPR/DPDP paperwork. |
| `:core:network`, `:feature:account`, `:feature:sync` modules | **Deleted.** Replaced by `:core:backup` (local encrypted ZIP + SAF export). | Three modules, ~4k LOC, gone. Compile time drops, APK shrinks. |
| AdMob + rewarded ads gating "pro" features | **Simple free tier + one-time IAP unlock (optional later).** AdMob stays for interstitials only. | Users hate rewarded walls. A $4.99 lifetime "Pro" IAP later will out-earn rewarded ads on a niche utility app. |
| 45-session roadmap, 5 phases across 3 stacks | **5 phases, one stack, ~25 sessions.** | Dramatically tighter. Shippable faster. |

### What stays from v1 (because it was already good)

- Clean Architecture + MVI + multi-module Gradle layout
- Jetpack Compose Material 3 + dynamic color
- CameraX + OpenCV edge detection pipeline
- Room + Jetpack Security (EncryptedFile) for on-device vault
- ML Kit Text Recognition v2 for OCR
- `PdfDocument` + PDFBox-Android for searchable PDFs
- All security rules from v1 §3 (still mandatory)
- Detekt, Macrobenchmark, Baseline Profiles, R8 full mode

### What v2 adds that v1 never had

- **Local encrypted ZIP backup** to user-chosen folder via SAF (Storage Access Framework)
- **"Move to SD card / USB-OTG"** — users with cheap phones can offload documents
- **Smart storage manager** — shows a breakdown of space used by scans, lets users bulk-compress old pages
- **Folder auto-rules** — "receipts go to Receipts, ID scans to IDs" based on on-device classifier
- **Offline LLM-lite summarizer** — a ~30 MB distilled TFLite text model that produces 1-sentence summaries of OCR'd pages (Phase 5)
- **Expressive Material 3 theme** — liquid/spring motion, haptics on every interaction, a visual identity that feels premium from the splash screen

---

## 1. Product Positioning & User Psychology

We are not competing with CamScanner on features. We are competing on **trust**, **speed**, and **feel**. This drives every UI decision below.

### Target user archetypes (ranked by LTV)

1. **The Privacy-Conscious Professional** (lawyer, doctor, accountant, journalist). Will pay for an app that never uploads. Will evangelize to peers. → *Trust signals everywhere.*
2. **The Student** (India, Indonesia, Nigeria, Brazil). Scans lecture notes, ID cards, hostel forms. Price-sensitive, device-constrained (4 GB RAM, 64 GB storage, spotty 4G). → *Small APK, offline, no account, no friction.*
3. **The Small-Business Owner** (receipts, invoices, GST docs). Needs it to *just work* when a customer is waiting. → *Speed and reliability over features.*
4. **The Household User** (scan a passport, a report card, a warranty). Uses it twice a month. Needs zero learning curve. → *First-scan experience must be perfect.*

### Psychological principles baked into the UX

| Principle | How v2 applies it |
|---|---|
| **Peak–end rule** | The moment of capture (peak) has a crisp shutter + haptic + 80ms corner snap animation; the end-of-flow share sheet has a subtle success confetti + "Saved to device" reassurance. |
| **Zeigarnik effect** | Never leave a document in "half processing" state. OCR runs eagerly; library always shows terminal state. |
| **Progressive disclosure** | Camera screen shows only: shutter, torch, mode chip. Advanced controls live behind a single "more" bottom sheet. |
| **Loss aversion** | Every destructive action (delete, redact, reset) has a 5-second undo snackbar. Never a confirm dialog — dialogs train users to click "yes" reflexively. |
| **Goal-gradient** | Batch scan shows a page counter that grows with a satisfying bounce; the "Done" button gets larger as more pages accumulate. |
| **Default bias** | Biometric lock is OFF by default (friction for casual users) but promoted via a one-time sheet after the 3rd scan. |
| **Status-quo bias** | We don't ask "do you want to create an account?" — we never ask, ever. The absence of the question *is* the positioning. |
| **Von Restorff effect** | The FAB (scan button) is the only element in the brand accent color on the library screen. Everything else is neutral. |

### The "30-second first scan" promise

From fresh install to a saved, searchable PDF on disk: **under 30 seconds**, zero taps outside the natural flow, no onboarding gate, no permission wall that isn't explained inline.

- **0–3 s** splash + cold start (baseline profile)
- **3–6 s** one screen onboarding ("Scan anything. Nothing leaves your phone.") — swipe to dismiss OR tap "Start scanning"
- **6–8 s** camera permission rationale (inline, not a modal)
- **8–20 s** user frames a document, edges snap green, taps shutter
- **20–25 s** crop screen (already pre-filled with detected corners), tap Next
- **25–30 s** filter preview, tap Save → library opens with the new doc

---

## 2. Locked Tech Stack (v2)

### Core
- Kotlin 2.0, Coroutines, Flow
- Jetpack Compose (Material 3 + **Material 3 Expressive motion/shapes** APIs), no XML except splash
- Hilt (with **KSP**, never kapt — Windows path bug)
- Navigation Compose with type-safe routes (Kotlin serialization-based)

### Camera & Imaging
- CameraX 1.4+ (`camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-extensions`)
- OpenCV for Android 4.10 as a prefab AAR, only `core` + `imgproc` + `photo` modules linked
- Coil 3 for Compose image loading
- **Google ML Kit Document Scanner API** as a *first-class* path on supported devices (Pixel 6+, Samsung One UI 6+) — gives us Google's own pipeline for free, saves battery, frees the CPU for OCR

### ML / On-Device AI (no network, ever)
- ML Kit Text Recognition v2 (Latin bundled, CJK/Devanagari/Arabic downloaded on demand via `ModuleInstallClient`)
- TFLite + GPU/NNAPI delegate for:
  - Auto-crop refinement (~2 MB)
  - Document-type classifier (receipt / ID / business card / A4 / whiteboard / book) (~3 MB)
  - DewarpNet-lite for book curvature (~3 MB)
  - **Phase 5 only:** distilled summarizer model (~30 MB, downloaded on-demand the first time a user opts in)

### Storage
- **Room 2.7** for metadata + FTS4 virtual table for search
- **DataStore (Proto)** for preferences
- **Jetpack Security `EncryptedFile`** for every page, thumbnail, and OCR text blob on disk (AES-256-GCM)
- **Android Keystore** for the master key (hardware-backed, `setUserAuthenticationRequired` optional via biometric setting)
- **SAF (Storage Access Framework)** for user-controlled exports to Drive/Dropbox/OneDrive/local folder
- **MediaStore** only for the optional "Save page as JPEG to Gallery" action

### PDF & Export
- `android.graphics.pdf.PdfDocument` for the image layer
- **PDFBox-Android** for the invisible text layer → searchable PDF
- **Zip4j** for encrypted ZIP backup (AES-256)

### Backup (new in v2)
- **Local encrypted ZIP** written via SAF to any user-chosen location (Drive, internal, SD card)
- No background sync, no servers, no WorkManager upload jobs — user triggers backup manually or via a scheduled local reminder

### Monetization
- **Google Mobile Ads SDK** (AdMob) — interstitials only, capped at one per 3-min window, never on camera/reader
- **UMP SDK** for consent (EU)
- **Google Play Billing 7** for a single, optional **$4.99 lifetime "Paperkeep Pro"** IAP (removes ads, unlocks batch-export-over-5-pages, unlocks summarizer) — ship empty-shelf in Phase 3, light it up in Phase 5

### Testing
- JUnit 5 + Turbine + MockK
- Compose UI tests for every screen flow
- Macrobenchmark for cold start, library scroll, capture→save
- Baseline Profiles auto-generated

### CI/CD (v2 — dramatically simpler)
- One GitHub Actions workflow: `lint → detekt → unit tests → UI tests on emulator → assembleRelease → upload to Firebase App Distribution`
- No backend deploy pipeline. No Terraform. No AWS anything.

### Deleted from v1 (do not re-add)
- ~~Ktor Client, OkHttp, certificate pinning~~
- ~~libsodium-jni / lazysodium-android~~
- ~~zxcvbn-kotlin (no passwords)~~
- ~~androidx.credentials / passkeys~~
- ~~WorkManager sync workers~~
- ~~`:core:network`, `:feature:account`, `:feature:sync` modules~~

---

## 3. Architecture (v2 Module Graph)

```
:app                           → assembly, Hilt root, navigation graph
:core:ui                       → design system, theme (Expressive), reusable Composables
:core:common                   → utils, Result, dispatchers, haptics
:core:data                     → Room DB, DataStore, encryption, file store
:core:domain                   → use cases, entities, repository interfaces
:core:crypto                   → Keystore + EncryptedFile wrappers (kept from v1)
:core:security                 → biometric, FLAG_SECURE controller, tamper check
:core:imaging                  → OpenCV edge detection, filters, perspective, dewarp
:core:ml                       → ML Kit OCR + TFLite classifier/dewarp wrappers
:core:pdf                      → PDF builder, OCR text layer embedder
:core:backup                   → NEW: SAF-based encrypted ZIP backup + restore
:core:ads                      → AdMob wrapper with frequency cap, UMP consent
:feature:scanner               → camera + crop + filter screens
:feature:library               → documents, folders, search, storage manager
:feature:reader                → pager, share, annotations, redaction, signature
:feature:settings              → preferences, security, backup, about
:feature:onboarding            → first-run + permission rationale
:benchmark                     → Macrobenchmark module for baseline profile gen
:store                         → Play Store assets + metadata (already exists)
```

**Removed modules:** `:core:network`, `:feature:account`, `:feature:sync`.

Each `:feature:*` module depends only on `:core:*` — never on other features. Deep links are declared in `:app`.

---

## 4. Data Model (Local-Only)

```kotlin
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,            // UUID
    val title: String,
    val folderId: String?,                 // null = "All"
    val createdAt: Long,
    val updatedAt: Long,
    val colorTag: Int?,                    // ARGB, optional highlight
    val docType: String?,                  // "receipt" | "id" | "a4" | ...
    val pageCount: Int,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
)

@Entity(tableName = "pages")
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val encryptedImagePath: String,        // filesDir/scans/<uuid>.enc
    val encryptedThumbPath: String,
    val width: Int, val height: Int,
    val filter: String,                    // "original" | "magic" | "gray" | "bw" | "auto"
    val ocrStatus: String,                 // "pending" | "done" | "failed"
    val ocrLanguage: String?,              // detected language
)

@Entity(tableName = "page_ocr")             // split for FTS + to keep PageEntity light
data class PageOcrEntity(
    @PrimaryKey val pageId: String,
    val encryptedText: ByteArray,          // AES-GCM envelope around plaintext + boxes JSON
)

@Fts4(contentEntity = PageOcrSearchView::class)
@Entity(tableName = "page_ocr_fts")
data class PageOcrFts(val text: String)    // decrypted-at-rest is NOT acceptable;
                                           // see §6 "Search & Encryption Trade-off"

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val autoRule: String?,                 // e.g. "docType=receipt" → auto-sort
    val createdAt: Long,
)

@Entity(tableName = "backups")              // Phase 4
data class BackupEntity(
    @PrimaryKey val id: String,
    val safUri: String,                    // persisted SAF URI
    val createdAt: Long,
    val sizeBytes: Long,
    val documentCount: Int,
    val checksum: String,                  // SHA-256 of the ZIP for integrity verify
)
```

---

## 5. The Five Phases (v2)

Each phase is a **shippable milestone**. Cold install → use → see progress. No phase introduces a backend dependency because there is no backend.

---

### Phase 1 — Foundation & Real-Time Capture (Sessions 1–5)

**Goal:** Cold start under 800ms, real-time edge detection at 60fps, capture→encrypted-save round trip under 2s. Zero network traffic.

**Module scope:** `:app`, `:core:ui`, `:core:common`, `:core:crypto`, `:core:security`, `:core:data` (scaffold), `:core:imaging`, `:feature:scanner`, `:feature:onboarding`.

#### Deliverables

1. **Project scaffold** — multi-module Gradle, version catalog, Detekt, R8 baseline rules, Hilt via KSP, Material 3 + Expressive theme, splash API, adaptive icon placeholder.
2. **Expressive design system (`:core:ui`)** — color scheme (light/dark + dynamic), typography scale, shape system, motion tokens (spring stiffness/damping presets), haptic tokens, reusable primitives: `SvButton`, `SvFab`, `SvChip`, `SvCard`, `SvBottomSheet`, `SvSnackbar`, `SvIconButton`.
3. **Onboarding (`:feature:onboarding`)** — exactly 3 screens, skippable, Lottie-free (native Compose animations only to keep APK small): value prop → permission rationale → "you're ready". Uses DataStore flag to never show again.
4. **Permissions flow** — CAMERA requested inline on camera screen entry, with a rationale card (not a modal), denial state with "Open Settings". No storage permissions requested at all on API 33+.
5. **Camera screen** — CameraX PreviewView, 4:3 aspect, torch, grid overlay, large bottom-center shutter with haptic + 50ms white-flash shutter animation, pinch-zoom 0.6x–3x, tap-to-focus ring, batch-mode toggle, bottom session-buffer strip (last 10 scans).
6. **Real-time edge detection overlay** — OpenCV pipeline on 640px-wide preview frame: grayscale → GaussianBlur → Canny → findContours → filter by area → approxPolyDP → 4 corners. Under 16ms/frame target. Compose Canvas overlay with 80ms spring corner animation. Green/amber/hidden states.
7. **Capture pipeline** — full-res `ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY`, full-res edge detection, `getPerspectiveTransform` + `warpPerspective`, fallback to raw crop if corners not found. All on `Dispatchers.Default`.
8. **Manual crop screen** — 4 draggable handles with a 50×50 magnifier lens (2x zoom) near the active handle, rotate 90°, retake, next. Debounced re-warp at 30fps.
9. **Encrypted storage (`:core:crypto` + `:core:data`)** — `EncryptedImageStore` writes `.enc` files under `filesDir/scans/`, AES-256-GCM via `MasterKey`, thumbnail (256px JPEG) encrypted alongside. Raw file is unreadable without the app's master key.

#### Acceptance

- [ ] Cold start < 800ms on Pixel 6a
- [ ] Edge detection overlay runs at 60fps (Macrobenchmark)
- [ ] APK size < 18 MB
- [ ] Capture → encrypted save round trip < 2s
- [x] Zero network traffic verified via `mitmproxy` (nothing observed) — **P1.12 pending live device run**. Static analysis: no OkHttp/Ktor/Retrofit in app source. Network permission present only for Google SDKs (AdMob, UMP, Play). The only outbound requests are AdMob test-ad initialization and UMP consent SDK — both are Google SDK calls, not our code. Verification date: 2026-04-24 (static). Live mitmproxy run to be performed when test device is available.
- [ ] Rotation preserves state (rememberSaveable + ViewModel)
- [ ] Detekt clean, R8 release build succeeds, install runs on Android 8 emulator

---

### Phase 2 — Library, OCR & PDF (Sessions 6–11)

**Goal:** Turn the session buffer into a real library. Multi-page documents, folders, on-device OCR, searchable PDF export, biometric lock. At end of Phase 2 the app is a **fully functional CamScanner replacement, permanently offline**.

**Module scope:** adds `:core:domain`, `:core:ml`, `:core:pdf`, `:feature:library`, `:feature:reader`, `:feature:settings`.

#### Deliverables

1. **Full data model** — `DocumentEntity`, `PageEntity`, `FolderEntity`, `PageOcrEntity`, FTS4 virtual table (see §6 for how we search encrypted OCR).
2. **Library screen** — Compose LazyVerticalStaggeredGrid (2 cols phone, 4 cols tablet), cards with first-page thumbnail + title + page count + relative timestamp + sync-icon-SLOT (reserved in UI but always shows "on device" pill in v2). Long-press → multi-select with a bottom action bar. Sort menu (newest / oldest / A–Z / most pages). Empty state illustration (SVG asset, not Lottie) with a single accent-colored CTA. Pull-to-refresh re-queries Room (for perceived responsiveness; no real network).
3. **Folders** — one level deep. System folders: "All", "Favorites", "Archive". User folders have an icon + optional auto-rule. Drag a document chip onto a folder chip to move.
4. **Full-text search** — FTS4 over the `page_ocr_fts` virtual table. Result list highlights the matched snippet with 50-char context on each side. Also matches titles and folder names. < 200ms over 100 docs.
5. **Multi-page capture flow** — batch mode accumulates pages, "Done" → reorder screen with `Reorderable`, drag handles, delete/retake/add-more.
6. **Image filters** — Original / Auto (TFLite classifier picks best) / Magic Color (CLAHE + saturation) / Grayscale / B&W (adaptiveThreshold Gaussian). Non-destructive — flag on `PageEntity`, original always preserved. Filter preview strip on crop screen with live thumbnails.
7. **OCR pipeline** — ML Kit Text Recognition v2 runs on `Dispatchers.Default` immediately after capture. Writes encrypted OCR text + bounding boxes. Library card shows "Processing…" pill until done. Language downloader screen in settings for Devanagari/Chinese/Japanese/Korean/Arabic modules.
8. **Searchable PDF export** — `PdfDocument` image layer + PDFBox-Android invisible text layer at OCR bboxes. Page size auto/A4/Letter/Legal. JPEG quality 60/85/95. Output to `cacheDir/exports/<docId>.pdf`, shared via FileProvider.
9. **Other exports** — JPEG (single or ZIP), PNG, plain text (OCR dump), encrypted ZIP (Zip4j + user password).
10. **Reader** — HorizontalPager, pinch-zoom per page, bottom bar (share / delete / rename / reorder / add page / export). OCR overlay toggle shows selectable transparent text at bbox positions. `FLAG_SECURE` default ON (user toggleable).
11. **Biometric lock (`:core:security`)** — `BiometricPrompt` with `BIOMETRIC_STRONG` + device credential fallback. Settings toggle + timeout (immediate/30s/1m/5m). Triggers on app launch and after the timeout of backgrounding. Cannot be bypassed by kill-and-relaunch (gate is a global nav-graph entry check).
12. **Settings screen (scaffold)** — sections: Security (biometric, screenshot protection), Scanning defaults (filter, page size), Language packs, About (version, OSS licenses, privacy policy link).

#### Acceptance

- [ ] End-to-end: capture 10 pages → reorder → filter → export searchable PDF → share to WhatsApp, all offline
- [ ] OCR ≥ 95% accuracy on clean A4 (measured against 20-doc ground truth)
- [ ] Library scroll 60fps with 500 cached documents
- [ ] APK < 22 MB
- [ ] Exported PDF has selectable text in Adobe Reader
- [ ] Batch delete of 50 docs < 1s and files actually gone from disk
- [ ] Biometric lock survives process death

---

### Phase 3 — Smart Modes, AdMob & Play Store Prep (Sessions 12–16)

**Goal:** The features that make users choose us over CamScanner, plus monetization. End of Phase 3 = launchable product.

**Module scope:** adds `:core:ads`; extends `:core:ml`, `:core:imaging`, `:feature:scanner`, `:feature:reader`.

#### Deliverables

1. **Smart document-type detection** — TFLite classifier: receipt / ID / business card / A4 / whiteboard / book. Auto-applies best filter + aspect ratio. Shown as a tappable chip on the crop screen to override.
2. **ID card mode** — front → back → auto-composed single A4 page with both sides. For passports, licenses, health cards.
3. **Receipt mode** — taller aspect, aggressive B&W, extracts total/date/merchant via regex heuristics on OCR output. Fields become searchable and surface as chips on the document card.
4. **Whiteboard mode** — glare removal (OpenCV `inpaint` on over-exposed regions), marker-color HSV boost, hand/shadow removal if detected.
5. **Book scan mode** — two-page spread auto-splits, DewarpNet-lite flattens spine curvature.
6. **Signature tool** — finger/stylus draw on transparent overlay, save up to 3 signatures in encrypted signature store, place+resize on any PDF page before export.
7. **Annotations** — text boxes, highlighter, redaction, eraser, undo/redo (30-step stack).
8. **True redaction** — user-drawn rectangle destroys the underlying pixels in the stored image AND overwrites corresponding OCR bboxes in the DB. Not a cosmetic overlay. This is a **trust differentiator** vs CamScanner.
9. **Image cleanup** — "Remove background noise" (bilateral + morphological opening), "Sharpen text" (unsharp mask), "Fix lighting" (CLAHE). Non-destructive.
10. **AdMob integration (`:core:ads`)** — lazy init (not in `Application.onCreate`). UMP consent. Interstitial after every 5th export, hard cap of one per 3-min window. NO banners. NO ads on camera or reader. Remote config via bundled JSON for now (no Firebase Remote Config — stays offline).
11. **Play Billing shelf** — wire `BillingClient`, query ONE product (`paperkeep_pro_lifetime`), stub the purchase flow. Don't light it up yet; the actual "Pro" unlock ships in Phase 5. Keeping the shelf in early lets us test BillingClient against the real store without risk.
12. **In-app rating prompt** — `ReviewManager`. Trigger: ≥ 3 exports AND app opened on ≥ 3 distinct days AND no prompt in last 90 days.
13. **Play Store prep** — EN store listing copy (keyword-loaded title + short description), 8 screenshots (feature-narrative, not raw UI), feature graphic 1024×500, hosted privacy policy, Data Safety form draft.

#### Acceptance

- [ ] AdMob test ads render correctly
- [ ] UMP consent works for EU locale (VPN test)
- [ ] All modes (ID / receipt / whiteboard / book) visibly beat the default pipeline on a test set
- [ ] Redaction verified destructive by raw pixel inspection
- [ ] APK < 28 MB
- [ ] Closed testing track live with 12 testers (start the 14-day clock)
- [ ] No regressions in Phase 1/2 criteria

---

### Phase 4 — Local Backup, Storage Manager & Deep Polish (Sessions 17–21)

**Goal:** Replace the deleted cloud-sync value prop with a **local-backup experience so good users don't miss the cloud**. Plus everything that makes the app feel premium.

**Module scope:** adds `:core:backup`; extends `:feature:library`, `:feature:settings`.

#### Deliverables

1. **Encrypted ZIP backup (`:core:backup`)**
   - User taps "Create backup" in settings → SAF `ACTION_CREATE_DOCUMENT` lets them pick any location (Google Drive, Dropbox, OneDrive, internal, SD card, USB-OTG) — Android routes through the providers automatically.
   - Zip4j with AES-256 + user-chosen password (zxcvbn-style strength meter inline).
   - Contents: all `PageEntity` images (re-encrypted with the backup password, not the device master key — backups are portable), full Room DB dump, settings JSON, a `manifest.json` with version + integrity SHA-256.
   - On completion: write a `BackupEntity` row with the SAF persistable URI (so "last backup 3 days ago" surfaces in settings).
2. **Backup reminders** — local `AlarmManager`-based reminder. User picks cadence (never / weekly / monthly). A gentle non-intrusive notification — respects `POST_NOTIFICATIONS` permission on API 33+.
3. **Restore flow** — SAF pick, password prompt, progress indicator, conflict strategy (merge / replace). Restored documents land in a "Restored <date>" folder until the user organizes.
4. **Smart storage manager** — settings screen showing pie chart of space used (scans vs. cache vs. exports vs. OCR). Actions: clear export cache, bulk-recompress pages older than 6 months (JPEG quality 85→70, keeps originals only if user opts in), empty trash, move selected documents to SD card.
5. **Folder auto-rules** — "if doc type = receipt, move to Receipts folder" — runs on save. User configures rules per folder in a simple dialog.
6. **Dark mode polish** — every screen tested in both light and dark with Expressive dynamic color. OLED-true-black optional toggle for AMOLED devices.
7. **Accessibility pass** — all interactive elements have `contentDescription`, TalkBack tested on capture→save golden path, minimum 48dp touch targets, ≥ 4.5:1 contrast, 200% font scaling doesn't break layouts, reduced-motion setting honored.
8. **Localization** — EN (default) + Hindi, Nepali, Spanish, Portuguese, Arabic (RTL tested), French, German, Indonesian. Play-auto-translate as the starting point, hand-review top 3.
9. **Widgets & QS tile** — "Scan now" home widget (Glance), "Recent scans" 1×4 thumbnail widget, Quick Settings tile for one-tap scan from the shade.
10. **Share receiver** — `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent filter in `:app` accepts images from Gmail/Drive/any app → imports as a new document (runs OCR + classifier like a fresh capture).
11. **Tamper check** — on first launch of a signed release build, compute `PackageInfo.signatures[0]` SHA-256 and compare to a hardcoded value. If mismatch, disable AdMob calls (keeps the app working but denies re-signed pirated builds a revenue stream).
12. **Crash handler** — custom `Thread.setDefaultUncaughtExceptionHandler` writes crash logs to encrypted local storage. "Send crash report" button in settings lets the user manually email the log. **Nothing auto-uploads, ever.** (Explicit non-goal: Crashlytics.)

#### Acceptance

- [ ] Backup → wipe app data → restore → every document opens, OCR is intact, passwords match
- [ ] Storage manager recompression saves ≥ 30% on a test library of 200 pages
- [ ] TalkBack can complete capture→save flow end-to-end
- [ ] Arabic RTL layout correct on every screen
- [ ] Widgets render on Pixel Launcher and Samsung One UI
- [ ] Tamper check flips the AdMob flag correctly when APK is re-signed

---

### Phase 5 — Summarizer, Pro IAP, Performance, Launch (Sessions 22–25)

**Goal:** One headline AI feature that CamScanner doesn't have, light up the Pro IAP, squeeze the last drop of performance, ship to production.

**Module scope:** extends `:core:ml`, `:feature:reader`, `:core:ads`; adds the Billing unlock path.

#### Deliverables

1. **On-device summarizer** — distilled TFLite text model (~30 MB, downloaded on first opt-in, cached), produces a 1-sentence summary per document plus 3–5 extractable key phrases. Runs on `Dispatchers.Default` with a 5-second budget; falls back silently to "no summary available" if the model can't converge. Gated behind Pro for >3 documents/day (free users get 3/day).
2. **Pro IAP live** — `$4.99` lifetime, localized prices per region via Play Console. Unlocks: no ads, batch-export over 5 pages, unlimited summarizer, "pro" visual theme (subtle accent variants). **No subscription.** One-time purchase only — this is a trust signal.
3. **Baseline Profiles** — Macrobenchmark harness generates profiles for cold start, library scroll, camera launch, PDF export. Ship with release APK. Expect 20–30% startup improvement.
4. **R8 full mode + resource shrinking** — tree-shake unused OpenCV modules (~15 MB savings), verify Hilt/kotlinx.serialization reflection survives obfuscation, audit keep rules.
5. **Performance pass targets:**
   - Cold start < 500ms on Pixel 6a
   - Warm start < 200ms
   - Library scroll 60fps with 1000 documents
   - Memory < 150 MB steady-state
   - Zero LeakCanary reports in debug
   - 1 hour of scanning drains < 8% on a 4000mAh battery
6. **Final store submission** — closed testing → open testing → production. Tier-1 launch countries: US, UK, CA, AU, DE, FR. Staged rollout 10 → 50 → 100% over 2 weeks. Data Safety declaration honest: device IDs for AdMob, app activity for ads, **"no data collected"** for everything else (the selling point).
7. **Post-launch monitoring (manual, no backend)** — daily Play Console check (crash rate target < 0.5%, crash-free users > 99.5%), weekly 1-star review triage, AdMob dashboard daily (eCPM + fill rate).

#### Acceptance

- [ ] All Phase 5 perf targets met on Pixel 6a
- [ ] APK final < 30 MB (target < 25 MB)
- [ ] Play Pre-Launch Report clean (zero critical)
- [ ] 12 closed testers complete the 14-day window
- [ ] Production live
- [ ] ≥ 50 organic installs in week 1
- [ ] Avg rating ≥ 4.3 after first 20 reviews

---

## 6. Security — Top-Notch, Locked for v2

Paperkeep's entire positioning is *"nothing leaves your phone, and even on your phone it's locked down so hard that a rooted attacker with your unlocked device gets nothing useful."* That bar requires real engineering, not marketing. This section defines every requirement. All v1 §3 rules remain in force; what follows is additive.

### 6.0 Threat model (know what we're actually defending against)

We defend against — in priority order:

1. **Lost/stolen device, unlocked** — someone with 30 seconds of physical access to a phone that's already unlocked. *Defense:* biometric app lock with short timeout, FLAG_SECURE, tamper-evident foreground reveal.
2. **Lost/stolen device, locked** — forensic extraction from a powered-off or locked device. *Defense:* hardware-backed keys (StrongBox/TEE), FBE (File-Based Encryption) by Android, our own at-rest AES-GCM envelope on top.
3. **Malicious app on the same device** — sideloaded adware or another installed app trying to read our files, clipboard, or intents. *Defense:* private `filesDir`, no exported components, sensitive-clipboard flag, intent validation, no world-readable FileProvider paths outside `cacheDir/exports/`.
4. **Rooted device + forensic tools (`adb backup`, TWRP dumps, Magisk modules)** — attacker has filesystem access. *Defense:* everything on disk is opaque AES-GCM ciphertext; keys live only in Keystore/StrongBox; root-detection signal (not a block — accessibility) disables backup creation on rooted devices by default.
5. **APK tampering / repackaging / piracy** — someone re-signs the APK to strip ads, inject tracking, or distribute a malicious clone. *Defense:* APK signature pinning at runtime, Play Integrity API attestation before unlocking Pro features.
6. **Memory-scraping / cold-boot attacks** — advanced forensics reading RAM while the app is running. *Defense:* partial — scrub plaintext buffers after use, never hold the master key longer than the operation that needs it, use `CharArray`/`ByteArray` and `Arrays.fill(buf, 0)` instead of `String`.
7. **Supply-chain attack** — compromised library pulls in malicious code. *Defense:* OWASP dependency-check in CI, pinned versions, reproducible R8 builds, minimal third-party SDK footprint (AdMob + UMP are the only network-touching SDKs, both Google-signed).
8. **Side-channel via logs, crash reports, screenshots** — sensitive data accidentally leaked via Logcat, Android auto-backup, or recent-apps screenshot. *Defense:* strict no-log rule in release, `android:allowBackup="false"`, `FLAG_SECURE` on every sensitive screen by default.

**Out of scope** (documented so scope creep doesn't kill us): nation-state adversaries with zero-days against TEE/StrongBox, compromised Android OS / firmware, compromise of the Google Play signing key.

### 6.1 Cryptography — primitives, keys, and rotation

**Primitives — locked. No alternatives accepted in PRs:**

| Purpose | Algorithm | Library |
|---|---|---|
| At-rest symmetric encryption | **AES-256-GCM** (96-bit random IV, 128-bit tag) | Tink (preferred) or Jetpack Security EncryptedFile |
| Key-encryption key (master) | **AES-256** stored in Android Keystore, StrongBox when available | `android.security.keystore` |
| Password-based key derivation (backup ZIPs) | **Argon2id** — m=128 MiB, t=4, p=2, 32-byte output, 16-byte random salt | `bouncycastle` or `argon2-jvm` (AAR only, no JNI from unverified sources) |
| HMAC for searchable-token index | **HMAC-SHA-256** with a 256-bit key stored in Keystore | `javax.crypto.Mac` |
| Random | **`SecureRandom`** — NEVER `java.util.Random`, NEVER `Math.random()` | platform |
| Hashing (integrity only, non-secret) | **SHA-256** | platform |
| Digital signatures (future: update channels) | **Ed25519** | Tink |

**Explicitly forbidden**: MD5, SHA-1, AES-ECB, AES-CBC without an encrypt-then-MAC wrapper, PBKDF2 with fewer than 600k iterations, bcrypt for file encryption KDF, raw DES/3DES, any home-rolled primitive, any algorithm from `com.sun.*` reflection tricks.

**Key hierarchy:**

```
                    ┌──────────────────────────────┐
                    │ Android Keystore (StrongBox) │
                    └──────┬───────────────────────┘
                           │ never leaves hardware
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
      K_master        K_search       K_sig_pin
      (AES-256)       (HMAC-256)     (EC-P256 verify-only)
            │              │              │
            │              │              └─ verifies update signatures (v3)
            │              │
            │              └─ HMACs OCR tokens for FTS index (deterministic)
            │
            └─ wraps K_doc per document (HKDF-derived with documentId as info)
                   │
                   └─ K_doc encrypts each page file + thumbnail + OCR envelope
```

- **K_master, K_search, K_sig_pin** — generated on first launch, `setUserAuthenticationRequired` configurable, `setInvalidatedByBiometricEnrollment(true)` so a new fingerprint enrollment invalidates the keys (defends against "thief adds their own fingerprint" attack).
- **K_doc** — never stored. Derived on-the-fly via HKDF-SHA256(K_master, salt=documentId, info="paperkeep:doc:v1"). A per-document key means even a leaked AES-GCM nonce (catastrophic for AES-GCM) only burns one document's key, not the whole vault.
- **Key rotation** — settings action "Rotate vault keys". Generates a new K_master, re-wraps every K_doc context (actually re-derives + re-encrypts each document with the new root). Runs in the background with a progress notification. Old K_master is deleted from Keystore only after every document has been re-encrypted and verified.
- **Backup ZIP keys** — independent of K_master. Derived from the user's backup password via Argon2id(m=128 MiB, t=4). Never reused, never stored on disk. The backup is a **cryptographic cold copy** — if K_master on the device is compromised, the backup is not.

### 6.2 Android Keystore configuration (the actual code rules)

Every key spec must set:

```kotlin
KeyGenParameterSpec.Builder(alias, PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .setRandomizedEncryptionRequired(true)               // forces random IV
    .setUserAuthenticationRequired(requireBiometric)     // true if user opted in
    .setUserAuthenticationParameters(30, AUTH_BIOMETRIC_STRONG)
    .setInvalidatedByBiometricEnrollment(true)           // new fingerprint = new keys
    .setIsStrongBoxBacked(hasStrongBox)                  // check availability first
    .setUnlockedDeviceRequired(true)                     // API 28+: key unusable when device is locked
    .build()
```

StrongBox fallback: try StrongBox → if `StrongBoxUnavailableException`, fall back to TEE. Log only the tier ("strongbox"/"tee"/"software") in release, never the key material.

### 6.3 Data-at-rest layout

Every file in `filesDir/` is AES-GCM ciphertext. No exceptions:

```
filesDir/
├── scans/
│   ├── <uuid>.enc           ← page image ciphertext (K_doc)
│   └── <uuid>.thumb.enc     ← thumbnail ciphertext (K_doc)
├── ocr/
│   └── <uuid>.enc           ← OCR JSON (text + bboxes), ciphertext (K_doc)
├── signatures/
│   └── <uuid>.enc           ← saved signature PNG ciphertext (K_master)
└── crash/
    └── <timestamp>.enc      ← crash log ciphertext (K_master)

databases/
└── paperkeep.db             ← Room SQLite. See §6.4 for what's stored.
```

`cacheDir/exports/<docId>.pdf` is **plaintext** by necessity (we're about to hand it to a share-sheet recipient). It is auto-deleted after 60 seconds via a cleanup worker — the export is ephemeral.

**`android:allowBackup="false"`** in the manifest. `android:fullBackupContent` set to an empty rules XML for defense-in-depth. This disables Google auto-backup entirely — we don't want vault data silently going to Google Drive via the OS's backup agent.

### 6.4 The searchable-OCR compromise (v2 design)

Room FTS4 needs something indexable. Plaintext OCR in the DB would violate the promise. SQLCipher is 3 MB and license-hostile. Our solution:

1. **OCR plaintext** never touches disk in clear form. It lives in `ocr/<uuid>.enc` encrypted with K_doc.
2. **`page_ocr_fts` virtual table** stores **HMAC-SHA256(K_search, normalized_token)** for every token. Tokens are lowercased, stripped of punctuation, NFKC-normalized, then HMAC'd and stored as hex.
3. **Search path:** user types a query → tokenize identically → HMAC each token with K_search → FTS lookup on the hashes → results are page UUIDs → decrypt only the *matched* OCR envelopes to produce snippets for display.
4. **What an attacker with the raw DB sees:** opaque 64-char hex strings. No plaintext, no frequency-analyzable short tokens (we drop 1- and 2-character tokens). No correlation to the page image (the mapping lives in encrypted columns).
5. **Trade-off:** exact-word matching only, no substring, no fuzzy. Accepted — if a user needs substring, they open the document and use in-document search which decrypts the OCR in memory.

**Known residual risk:** HMAC'd token frequency can reveal document *size* and *language* to someone with DB access. We accept this — it's strictly less than what any cloud scanner reveals, and eliminating it would require an ORAM construction that's way out of scope.

### 6.5 App lifecycle hardening

- **Biometric gate on launch and resume.** `BiometricPrompt` with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`. Timeout options: immediate / 30s / 1m / 5m. A single process-global `LockController` (in `:core:security`) intercepts every nav entry. Kill-and-relaunch cannot bypass it — the lock state is derived from Keystore key availability, not a boolean preference.
- **`FLAG_SECURE`** set on the *Activity* (not individual Composables) whenever a document is on-screen. This blocks screenshots, screen-recording, and recent-apps thumbnails. User-toggleable in settings (default ON).
- **Recent-apps redaction.** Even with FLAG_SECURE, we set a neutral branded overlay as the task description so the thumbnail in the task switcher shows "Paperkeep" + a lock icon, not any document content.
- **Clipboard hygiene.** When user copies OCR text, set `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+) so it doesn't appear in clipboard suggestions or history. Auto-clear our own clipboard entries after 60 seconds.
- **Background wipe.** On `onStop()` of any Activity showing plaintext, we proactively evict decrypted bitmaps from Coil's memory cache and zero out plaintext buffers. Full lock (require re-auth) kicks in per the timeout setting.

### 6.6 Integrity, anti-tamper, and anti-piracy

1. **APK signature pin.** On every launch, compute `PackageInfo.signingInfo.apkContentsSigners[0]` SHA-256 and compare to a hardcoded constant baked in at build time. Mismatch → silently disable: AdMob calls, Pro IAP validation, backup creation. Scanning still works (we don't brick the app) but a re-signed clone is denied monetization and backup paths.
2. **Play Integrity API.** Before unlocking Pro IAP, call `IntegrityManager.requestIntegrityToken(...)` and verify the returned JWS server-side... except we have no server. Instead, verify **on-device** with Google's public key (acceptable for this threat tier) and require `meetsDeviceIntegrity` at minimum. Rooted/unlocked-bootloader devices get "Pro unavailable on this device — contact support" with a polite-but-firm message.
3. **Root/Magisk detection.** Best-effort via `build.tags`, `/system/xbin/su` probe, `Magisk` package lookup, Frida port check, and presence of `/sbin/.magisk`. Not used to block the app (users have the right to root their own device) but:
   - Disables Pro IAP unlock (can't reliably validate purchase integrity)
   - Disables the "Rotate vault keys" destructive action by default (must be enabled with a warning)
   - Shows a one-time info card in settings: *"We've detected this device is rooted. Paperkeep still works, but some integrity-dependent features are disabled. Learn why →"*
4. **Emulator detection.** Refuse to decrypt vault data on known emulator fingerprints in release builds. Debug builds allow it for testing.
5. **Frida/hook detection.** Scan loaded libraries for `frida-gadget`, `libfrida`, `libsubstrate`. On hit, terminate the process (not ideal UX, but anyone loading Frida against a consumer scanner app is actively attacking it).
6. **Debug flag refusal.** If `ApplicationInfo.FLAG_DEBUGGABLE` is set on a release build, refuse to start — impossible via normal builds, a red flag if observed.

### 6.7 Network posture (even though we have no backend)

The only network traffic in Paperkeep is AdMob + UMP + (optionally) Play Integrity + Play Billing. That's it.

- `networkSecurityConfig.xml`: `cleartextTrafficPermitted="false"` globally, no user-added CAs trusted.
- **Domain allowlist** enforced by a custom `OkHttp` interceptor wrapping the Google SDKs' network calls — rejects any host not in `{*.doubleclick.net, *.google.com, *.googleapis.com, *.googleusercontent.com, *.gstatic.com}`. If AdMob ever tries to contact a host outside this list, the request dies.
- **No WebView** anywhere. (WebView is historically the #1 source of Android RCE.) The privacy policy is a native Compose screen, not a web page.
- **No deep links with untrusted schemes.** Only `ACTION_SEND` for image import, with strict MIME allowlist (`image/jpeg`, `image/png`, `image/heic`, `image/webp`, `application/pdf`) and size cap (50 MB per file).

### 6.8 Dependency and supply-chain hygiene

- **Version catalog pinning** (`libs.versions.toml`) — exact versions only, no dynamic `+` or `latest.release`.
- **OWASP dependency-check** in CI on every PR. CRITICAL/HIGH blocks merge.
- **Gradle dependency verification** — checksums file (`gradle/verification-metadata.xml`) committed; mismatches fail the build.
- **Reproducible builds** — pinned Gradle, Kotlin, AGP, JDK versions. R8 fullMode deterministic.
- **SDK allowlist.** Third-party SDKs that ship in the APK: Google Mobile Ads, UMP, Play Billing, Play Integrity, ML Kit, CameraX, OpenCV, Coil, Room, Hilt, PDFBox-Android, Zip4j, Tink, Argon2 (bouncycastle). No others without an explicit review.
- **No reflection-heavy telemetry SDKs.** Ever.

### 6.9 Runtime code hardening

- **R8 full mode** on release, resource shrinking on, debuggable false, test-only false.
- **Native library strip** — remove unused OpenCV modules (imgcodecs, videoio, calib3d, features2d, ml, objdetect, photo if not used).
- **`android:extractNativeLibs="false"`** — native libs served directly from the APK (smaller on-disk, slightly harder to tamper with).
- **Manifest hardening:**
  - `android:allowBackup="false"`
  - `android:dataExtractionRules` → empty (API 31+)
  - `android:exported` explicitly set on every component; most are `false`. Only the launcher Activity, share-receiver Activity, QS tile service, and widget receiver are `true`, each with strict intent filters.
  - `android:networkSecurityConfig` pointing to the strict config
  - `android:usesCleartextTraffic="false"`
- **Permissions minimalism:** `CAMERA`, `POST_NOTIFICATIONS` (API 33+), `USE_BIOMETRIC`, `VIBRATE`, `INTERNET` (for AdMob/UMP/Play only). **No** `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES` — we use SAF and Photo Picker.
- **No log statements in release** — Detekt rule forbids `Log.*`, `println`, `System.out` in the `release` source set; Timber is configured to use a no-op `Tree` in release; ProGuard `-assumenosideeffects` strips any that slip through.
- **Zero-on-free buffers.** Custom `SecureBytes` wrapper for anything that holds plaintext keys, OCR text, or passwords — AutoCloseable with `close()` zeroing the underlying array. Every crypto operation uses `use {}` blocks.

### 6.10 Biometric and auth UX (security without being hostile)

Security that users turn off is worthless. Rules:

- **Default OFF on first install.** Promote it via a one-time bottom sheet **after the 3rd scan** — once the user has something worth protecting.
- **Fallback to device credential** always allowed (PIN/pattern/password) — some users don't have biometrics enrolled, or their fingerprint sensor is flaky. `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`.
- **Grace period.** Re-auth is required after the timeout expires OR after `onStop` + timeout. Switching to the camera app and back within 30 seconds does not re-prompt.
- **No "remember me" across reboots** — Keystore's `setUnlockedDeviceRequired` enforces this at the hardware level.

### 6.11 Privacy contract — what we promise users, in plain English

Shown in the privacy policy screen (one page, not a wall of text). This is a legal commitment, not marketing:

1. **We collect zero data.** No email, no name, no phone number, no analytics events. No account exists.
2. **Nothing leaves your device** unless you explicitly tap a share, export, or backup button and choose where it goes.
3. **Ads are the only network traffic.** Google AdMob may see your device's advertising ID and coarse region. That's it. EU users get a full consent prompt.
4. **We cannot help you recover your data.** If you lose your phone and have no backup, your scans are gone. We have no copy. This is the cost of real privacy and we state it upfront.
5. **Open security.** Our security design is this document. If you find a vulnerability, email `security@paperkeep.app` (or the real address before launch) and we'll fix it.

### 6.12 Security CI / release gates (enforce, don't trust)

Before every release build:

- [ ] Detekt clean
- [ ] OWASP dependency-check: zero CRITICAL/HIGH
- [ ] Gradle dependency verification passes
- [ ] `apksigner verify --verbose` passes and reports v2+v3 signing
- [ ] Manifest audit script: every `exported=true` has a justification comment
- [ ] `grep -r "Log\." app/src/main` on release returns zero hits
- [ ] APK MobSF (Mobile Security Framework) scan run manually on each release candidate — zero High findings
- [ ] Baseline Play Pre-Launch Report: zero security warnings

### 6.13 Incident response (even for a solo dev)

- A `security@paperkeep.app` email (forwarded to personal inbox) published in the Play Store listing and privacy policy.
- A `/SECURITY.md` in the repo root with PGP key + responsible disclosure window (90 days).
- Kill-switch: a signed `integrity.json` bundled in-app. A future release can revoke the current signing key pin or disable features if a vulnerability is found — since the bundle is embedded per-release, it propagates via normal Play Store updates.

---

## 7. UI/UX System — Making It Feel Premium

### Visual language

- **Color palette** — neutral-dominant with one confident accent. Default accent is a deep **saffron #F59E0B** for warmth (differentiates from the cold blues every other scanner uses). Material 3 dynamic color takes over on Android 12+ so the app adapts to each user's wallpaper — a delightful surprise.
- **Typography** — Roboto Flex variable font. Display weights for document titles, Medium for section headers, Regular for body, Mono for extracted numbers (receipts, IDs).
- **Shape** — Material 3 Expressive shape tokens. Cards use a `LargeRoundedCornerShape` (20dp). FABs use a `squircle` (40% radius).
- **Motion** — spring physics (stiffness 380, damping 0.85) for every interactive state change. No linear curves. Corner-snap on edge detection uses a tighter spring (stiffness 600).
- **Haptics** — `HapticFeedbackConstants.CONFIRM` on shutter, `TEXT_HANDLE_MOVE` on crop-handle drag ticks, `LONG_PRESS` on multi-select entry, `REJECT` on destructive undo expire. No generic vibrations — every haptic is semantic.
- **Sound** — a single 50ms camera-shutter SFX, toggleable. Default ON because users expect cameras to click.

### Key-screen UX principles

#### Camera screen
- Only three visible controls: shutter (huge, accent), torch (top-right), mode chip (top-center showing "Auto" / "Receipt" / "ID" — tappable for quick switch). Everything else is in a bottom sheet behind "more".
- Edge detection corners are drawn in accent color with 40% alpha fill → reads as "locked on" without shouting.
- Capture triggers a **150ms micro-animation**: white flash + capture-thumbnail slides down into the recent-scans strip. This is the peak moment — spend animation budget here.

#### Crop screen
- The "Next" button is disabled for the first 200ms to let the user's eyes land on the detected corners — removes the feeling of being rushed.
- Magnifier lens is circular with a subtle shadow, positioned opposite the active handle so the user's own thumb doesn't occlude it.
- Filter preview thumbnails are generated at 128px on `Dispatchers.Default` to keep the UI thread free.

#### Library screen
- Staggered grid (slightly uneven row heights) — reads as "organic" rather than a rigid spreadsheet.
- Search bar collapses into a pill on scroll down, expands on scroll up. Persistent but non-intrusive.
- FAB is the **only** accent-colored element. On a screen full of neutral cards, the eye goes to the action. Von Restorff in action.
- Empty state: a line-art illustration of a phone scanning a page + copy "Nothing to scan yet. Let's fix that." + single accent button. Never an info-dump.

#### Reader
- First tap anywhere = full-screen immersive (hide system bars). Second tap = show them. Mirrors every photo viewer on earth — uses existing muscle memory.
- Bottom action bar uses icon + short label, not icon-only — accessibility + new-user clarity.

#### Settings
- Sectioned list with small illustrations next to each section header. "Security" gets a little lock, "Backup" a little cloud-with-a-phone-inside, etc. Makes a utility screen feel crafted.
- Toggles use Material 3's new `Switch` with the thumb expanding on press — tiny delight every time.

### Onboarding copy (v2, locked)

1. **Screen 1:** *"Paperkeep: scan anything. Nothing leaves your phone."* — subhead: *"Documents, receipts, IDs, whiteboards. All processed on this device. We never see your data. We don't have a server."*
2. **Screen 2:** *"One permission. That's it."* — rationale: *"We need the camera to scan. We'll never ask for your contacts, location, or files."*
3. **Screen 3:** *"You're ready."* — single CTA: *"Start scanning"*.

No "create account" step. Ever. That is the positioning.

### Trust signals (subtly placed, never preachy)

- Settings header: a small "100% offline" pill
- Every share sheet includes a 1-line footnote: "Shared: just this document. Nothing else."
- Privacy policy page is a *single screen*, not a wall of text. Three bullets: "We collect nothing. Nothing leaves your device. Ads are non-personalized unless you opt in."

---

## 8. What v2 Deliberately Does NOT Build

- Subscriptions (one-time Pro only)
- Team sharing / collaboration
- Cloud anything
- Real-time collaborative editing
- LLM "ask your document" (the summarizer is as close as we go)
- Handwriting recognition beyond ML Kit's free level
- Form filling / fillable PDF
- Document compare / diff
- Web or desktop versions
- Direct Drive/Dropbox API integrations (we use SAF and let Android's provider system handle it — lower risk, zero OAuth)
- Voice notes on pages
- Multi-user on same device
- Any form of account recovery (there are no accounts)

---

## 9. Definition of Done (every phase)

1. All acceptance criteria for the phase verified on real hardware (not just emulator)
2. Detekt clean, zero warnings
3. `assembleRelease` with R8 full mode succeeds
4. `:core:*` module unit coverage ≥ 70%
5. Previous phase's tests still pass (regression guard)
6. 30-minute manual test session on a mid-range device (a cheap 2022 Xiaomi or Samsung A-series — the target user's device) finds zero blockers
7. Signed APK installed on the developer's daily-driver phone and used for at least one full day before the phase is checked off

---

## 10. Migration from v1 to v2 (what to delete, what to keep)

### Delete (on v2 branch cutover)
- `android/core/network/` (entire module)
- `android/feature/account/` (entire module)
- `android/feature/sync/` (entire module)
- All `:backend/` source (this repo becomes Android-only; optionally keep `backend/` on a `v1-archive` branch for historical reference)
- All `intelligence/` source (same — archive branch only)
- All `.github/workflows/backend-*.yml`, `ota-*.yml`, `android-release.yml` (replace with one unified v2 workflow)
- `infra/` Terraform directory (tear down AWS stack first — see v1 CLAUDE.md for the teardown command)
- `docs/BACKEND_MVP.md`, `docs/INTELLIGENCE_LAYER.md`, `docs/TERRAFORM_GUIDE.md`, `docs/DEVOPS_AUTOMATION.md` — move to `docs/archive/` for reference

### Keep and extend
- Everything under `android/app/`, `android/core/{ui,common,crypto,data,domain,imaging,ml,pdf,security,ads}/`, `android/feature/{scanner,library,reader,settings,onboarding}/`
- `android/benchmark/`
- `android/store/` (Play Store metadata)
- Existing `VERSION` file (reset to `2.0.0-alpha.1` on cutover)
- `CLAUDE.md` — **rewrite** to reflect v2 three-phase architecture collapse to one stack (Android-only), new build order, new cost reality ($0/mo)

### AWS teardown checklist (run BEFORE cutover so billing stops immediately)
1. `cd infra && terraform destroy -var-file=terraform.tfvars`
2. Manually delete S3 bucket contents and bucket (`scanvault-staging-vault-*`)
3. Delete ECR images: `aws ecr batch-delete-image ...` for both `scanvault-staging-go-backend` and the Python image
4. Delete CloudWatch log groups
5. Delete Secrets Manager secrets (3 of them)
6. Delete the Terraform state S3 bucket last: `scanvault-tfstate`
7. Verify in AWS Cost Explorer that next-day costs are $0

---

## 11. Daily Prompt for v2 (copy-paste)

```
Read docs/v2_Frontend_design.md and PROGRESS.md. Find the next unchecked Phase task.
The product is Paperkeep (Android-only, no backend). Do the task.
Update PROGRESS.md when done.
```

---

## 12. Security Quick-Reference (for every PR)

Before merging any code that touches auth, storage, crypto, network, or exports:

- [ ] All new file writes to `filesDir/` go through the encrypted store — no plaintext blobs
- [ ] Any new key uses Keystore + StrongBox-fallback + `setUnlockedDeviceRequired(true)`
- [ ] No new permissions added without a written justification in the PR
- [ ] No new third-party SDK without security review against §6.8 allowlist
- [ ] `Log.*` calls audited — nothing user-content in release
- [ ] If a new screen shows document content, `FLAG_SECURE` is enabled by default
- [ ] If a new intent filter is added, MIME + size + source validated per §6.7
- [ ] Detekt + OWASP dep-check + apksigner verify are green
- [ ] Threat model in §6.0 re-read — does this PR expand the attack surface? If yes, document the mitigation.
