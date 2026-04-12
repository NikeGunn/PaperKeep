# ScanVault — Android Frontend MVP

**Project codename:** ScanVault (placeholder — rename before launch)
**Platform:** Android (native)
**Language:** Kotlin 2.0+
**UI:** Jetpack Compose (Material 3)
**Min SDK:** 26 (Android 8.0) — covers 97%+ of active devices
**Target SDK:** 35 (Android 15)
**Build system:** Gradle with Kotlin DSL + version catalogs

---

## 0. Why native Android (not Flutter / React Native)

We are competing with CamScanner. CamScanner's weaknesses are: aggressive ads, forced accounts, watermarks on free tier, past malware incidents (2019 SDK scandal), bloated APK (~180 MB), slow edge detection. Our advantages must be:

- **APK under 25 MB** (small = more installs in emerging markets = more ad impressions)
- **Edge detection under 16ms per frame** (buttery real-time preview)
- **Zero forced login** (core scanning works fully offline, forever, no account)
- **No watermark ever on free tier** (this is our #1 differentiator)
- **No data leaves the device unless the user explicitly enables sync**
- **Cold start under 500ms**

Flutter adds ~8 MB of framework overhead and camera plugins have historically been flaky with CameraX. React Native is worse. Native Kotlin wins on every metric that matters for a camera-heavy utility app.

iOS port comes later as a SwiftUI rewrite sharing only the ML models and the backend API contract.

---

## 1. Tech Stack (locked decisions)

### Core
- **Kotlin 2.0** + **Coroutines** + **Flow**
- **Jetpack Compose** (Material 3, no XML layouts except splash)
- **Hilt** for dependency injection
- **Navigation Compose** with type-safe routes

### Camera & Image
- **CameraX 1.4+** (`camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-extensions`)
- **OpenCV for Android 4.10+** (edge detection, perspective transform, adaptive thresholding)
  - Load as a prefab AAR, strip unused modules to keep APK small
- **Coil 3** for image loading in Compose

### ML / OCR (all on-device — this is non-negotiable)
- **Google ML Kit Text Recognition v2** (on-device, free, 100+ languages)
  - Latin script model bundled (~4 MB)
  - Chinese / Devanagari / Japanese / Korean downloaded on demand
- **Google ML Kit Document Scanner API** as a fallback path on devices that support it (Pixel, recent Samsung) — gives us Google's own edge detection for free
- **TensorFlow Lite** with GPU delegate for our custom models:
  - Auto-crop refinement (MobileNet-based corner detector, ~2 MB)
  - Image enhancement classifier (decides: magic color / grayscale / black&white)

### Storage
- **Room 2.7** (SQLite) for metadata
- **DataStore (Proto)** for user preferences
- **Encrypted file storage** using `AndroidX Security Crypto` (EncryptedFile) — all scanned images on disk are AES-256-GCM encrypted at rest
- **Android Keystore** for the master encryption key (hardware-backed on devices that support it)

### PDF & Export
- **iText 7 Community (AGPL)** — NO. AGPL is incompatible with closed-source distribution.
- **Use `android.graphics.pdf.PdfDocument`** (built-in, free, sufficient for image-based PDFs)
- **Apache PDFBox-Android** port for text-layer embedding (so OCR text is searchable inside the PDF)
- **Zip4j** for encrypted ZIP export

### Networking (Phase 4+)
- **Ktor Client** (native Kotlin, smaller than Retrofit+OkHttp combo, matches Go backend stack)
- **kotlinx.serialization** for JSON
- **Certificate pinning** via OkHttp engine
- **libsodium-jni** for client-side E2E encryption (before upload)

### Ads
- **Google Mobile Ads SDK (AdMob)**
- **UMP SDK** for GDPR/consent (mandatory for EU traffic — without it you earn $0 from EU)
- Ad formats: rewarded video + interstitial. NO banners on camera screen (kills conversion).

### Testing
- **JUnit 5** + **Turbine** (Flow testing) + **MockK**
- **Compose UI tests** for critical flows
- **Macrobenchmark** for cold start and frame timing
- **Baseline Profiles** generated from benchmark runs (gives 20-30% startup improvement)

### CI/CD
- **GitHub Actions**: lint → unit tests → assemble debug → upload to Firebase App Distribution
- **Detekt** for static analysis
- **Gradle version catalog** (`libs.versions.toml`)

---

## 2. Architecture

**Clean Architecture + MVI** (Model-View-Intent). Three layers:

```
app/
├── data/           # Room, DataStore, OpenCV wrappers, ML Kit wrappers, Ktor API
├── domain/         # Pure Kotlin use cases, entities, repository interfaces
└── ui/             # Compose screens, ViewModels (MVI), navigation
```

Modularization (from day one — refactoring later is expensive):

```
:app                          → assembly only, Hilt root
:core:ui                      → design system, theme, reusable Composables
:core:common                  → utils, Result wrapper, dispatchers
:core:data                    → Room DB, DataStore, encryption
:core:domain                  → use cases, entities
:core:ml                      → ML Kit + TFLite wrappers
:core:imaging                 → OpenCV edge detection, filters, perspective
:core:pdf                     → PDF builder, OCR text layer embedder
:core:network                 → Ktor client, E2E crypto (Phase 4+)
:feature:scanner              → camera screen, crop screen, filter screen
:feature:library              → document list, folders, search
:feature:reader                → PDF viewer, share, export
:feature:settings             → preferences, security, sync toggle
```

Each feature module depends only on `:core:*` — never on other features. This keeps build times fast and forces clean boundaries.

---

## 3. Security Requirements (non-negotiable, apply to every phase)

Every phase must satisfy these before it is considered "done":

1. **Encrypted at rest.** Every scanned page file and every DB entry containing OCR text is encrypted with AES-256-GCM. Master key lives in Android Keystore, hardware-backed when available.
2. **No analytics SDKs that phone home by default.** No Firebase Analytics, no Crashlytics without explicit opt-in, no Facebook SDK, no AppsFlyer, no Adjust. AdMob is the only third-party SDK that touches the network in Phase 1–3.
3. **Network security config** (`res/xml/network_security_config.xml`):
   - `cleartextTrafficPermitted="false"` globally
   - Certificate pinning on our own API (Phase 4+)
   - No user-added CAs trusted in production builds
4. **ProGuard / R8** full mode enabled on release. Obfuscate, strip, shrink.
5. **No exported components** unless explicitly needed. Every Activity/Service/Receiver in the manifest must justify `android:exported="true"`.
6. **No `READ_EXTERNAL_STORAGE` on API 33+.** Use Photo Picker (`ActivityResultContracts.PickVisualMedia`). We never scan the user's gallery without their explicit per-image selection.
7. **FileProvider** for every shared file. No `file://` URIs, ever.
8. **Biometric lock** (Phase 2) using `androidx.biometric`. Lock on app background with configurable timeout.
9. **Screenshot prevention** (`FLAG_SECURE`) on any screen showing sensitive document content — user toggle, default ON for the PDF reader.
10. **Clipboard safety.** When user copies OCR text, we use `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+) so it doesn't appear in clipboard history.
11. **Deep link validation.** Any `intent-filter` with a scheme must validate the host and reject unknown sources.
12. **Intent redirection guard.** Never trust `getParcelableExtra` for a `PendingIntent` or `Intent` from another app.
13. **SQL injection impossible** because we use Room with typed queries only. Never use `SupportSQLiteDatabase.execSQL` with user input.
14. **Zero log statements containing user content** in release builds. Detekt rule blocks `Log.*` calls in the `release` source set.
15. **Tamper detection.** Check `PackageManager.GET_SIGNATURES` on startup against a known hash. If the APK is re-signed, refuse to run cloud sync (Phase 4+).
16. **Dependency auditing.** OWASP dependency-check runs in CI on every PR. Any CRITICAL or HIGH vulnerability blocks the build.

---

## 4. The Five Phases (interconnected)

Each phase is a **shippable milestone**. At the end of every phase you can install the APK on a real device and see real progress. Each phase also has a **backend handshake point** — a specific moment where the frontend integrates with the matching backend phase from `BACKEND_MVP.md`.

Phases are sequential. Do not start phase N+1 until phase N passes its acceptance criteria.

---

### Phase 1 — Capture & Real-Time Edge Detection (Weeks 1–2)

**Goal:** A working camera screen that detects document edges in real-time, captures a high-res photo, runs perspective correction, and saves the result to encrypted local storage. No library, no PDF, no OCR yet. Just prove the hardest technical part works.

**Backend handshake:** None. This phase is 100% offline.

#### Deliverables

1. **Project skeleton**
   - Multi-module Gradle setup as defined in section 2
   - Version catalog, Detekt, R8 config, baseline ProGuard rules
   - Hilt wired up
   - Material 3 theme with dynamic color on Android 12+
   - Splash screen API (`core-splashscreen`)
   - App icon placeholder (adaptive)

2. **Permissions flow**
   - Runtime request for `CAMERA` with a clear rationale screen
   - Denial state with "Open Settings" button
   - No storage permissions requested — we use scoped storage only

3. **Camera screen (`:feature:scanner`)**
   - CameraX `PreviewView` filling the screen with safe-area insets respected
   - Aspect ratio: 4:3 (documents are almost never 16:9)
   - Torch toggle
   - Grid overlay toggle
   - Capture button (large, bottom center, haptic feedback on press)
   - Multi-capture mode toggle (batch → single flow)
   - Shutter animation (50ms white flash)
   - Zoom via pinch gesture (0.6x – 3x on supported devices)
   - Tap-to-focus with AF indicator ring

4. **Real-time edge detection overlay**
   - OpenCV pipeline running on a downsampled (640px wide) preview frame
   - Pipeline: grayscale → Gaussian blur → Canny → `findContours` → filter by area → approxPolyDP → 4-corner detection
   - Target: **under 16ms per frame** on a mid-range device (Snapdragon 6xx class)
   - Overlay drawn on a Compose `Canvas` above the preview, animated corners with 80ms spring interpolation
   - Color: green when 4 valid corners found, amber when partial, invisible when no document
   - Fallback to ML Kit Document Scanner API on supported devices (better quality, saves us battery)

5. **Capture pipeline**
   - On capture: take full-resolution image (use `ImageCapture` with `CAPTURE_MODE_MAXIMIZE_QUALITY`)
   - Run full-resolution edge detection (not downsampled) on the captured image
   - If 4 corners found → auto perspective transform using `getPerspectiveTransform` + `warpPerspective`
   - If not found → fall back to returning the raw cropped image
   - All image processing runs on `Dispatchers.Default`, never the main thread

6. **Manual crop screen**
   - Shown after capture, always
   - 4 draggable corner handles over the captured image
   - Magnifier lens near the active handle (50x50 px zoomed 2x) for pixel-accurate placement
   - Rotate buttons (90° left/right)
   - "Retake" and "Next" buttons
   - Debounced re-warp on corner drag (update preview at 30fps max)

7. **Local encrypted storage**
   - `:core:data` implements `EncryptedImageStore`
   - Key: AES-256-GCM via Jetpack Security Crypto `MasterKey` (hardware-backed when available)
   - One file per page, stored under `context.filesDir/scans/<uuid>.enc`
   - Thumbnail (256px JPEG, also encrypted) stored alongside as `<uuid>.thumb.enc`
   - Metadata written to a Room `ScanEntity` (id, createdAt, width, height, originalPath, thumbnailPath)

8. **Temporary "recent scans" strip**
   - Simple horizontal list at the bottom of the camera screen
   - Shows thumbnails of the last 10 scans from the current session
   - Tap → preview, swipe up → delete
   - This is NOT the library yet, it's a session buffer that gets cleared on app close

#### Acceptance criteria (you cannot move to Phase 2 until all pass)

- [ ] Cold start under 800ms on a Pixel 6a
- [ ] Edge detection preview runs at 60fps (measured with Macrobenchmark)
- [ ] APK size under 18 MB at this phase (leaves headroom for later)
- [ ] Capture → crop → save round trip under 2 seconds
- [ ] All captured images verifiably encrypted on disk (test by opening the raw `.enc` file — must be unreadable)
- [ ] Zero network calls (verified with a proxy — it must show nothing)
- [ ] Runs on Android 8 emulator and a physical mid-range device
- [ ] Screen rotation works without losing state (use `rememberSaveable` + ViewModel)
- [ ] Tested under low light (edge detection degrades gracefully, not crashes)
- [ ] Detekt passes with zero warnings, R8 build succeeds

---

### Phase 2 — Document Library, OCR & PDF Export (Weeks 3–4)

**Goal:** Turn the session buffer into a real library. Multi-page documents. On-device OCR. Share as searchable PDF. This is the phase that makes the app actually useful — end of Phase 2 = functional CamScanner replacement, offline only.

**Backend handshake:** None yet. Still 100% offline.

#### Deliverables

1. **Document model**
   - `DocumentEntity` (id, title, createdAt, updatedAt, folderId, pageCount, color tag)
   - `PageEntity` (id, documentId, pageIndex, imagePath, thumbPath, ocrText, width, height, filter)
   - Room relations: `@Relation` to load Document + pages in one query
   - DAO with `Flow<List<DocumentWithPages>>` for reactive library UI

2. **Library screen (`:feature:library`)**
   - Grid of document cards (2 columns on phones, 4 on tablets)
   - Each card: thumbnail of first page, title, page count, timestamp
   - Long-press → multi-select mode with batch actions (delete, move, merge, export)
   - Sort: newest, oldest, A–Z, most pages
   - Empty state illustration with a clear CTA
   - Pull-to-refresh (even though there's no network — just resyncs from Room, future-proof for Phase 4)

3. **Folders**
   - Simple folder structure (one level deep — no nested folders in MVP)
   - "All documents" pseudo-folder
   - Create / rename / delete folders
   - Drag a document onto a folder chip to move it

4. **Search**
   - Full-text search over OCR text using Room FTS4 virtual table
   - Search result highlights the matching snippet
   - Search also matches document titles and folder names

5. **Multi-page capture flow**
   - In camera screen, "batch mode" accumulates pages into a pending document
   - "Done" button → goes to a page-reorder screen
   - Drag-to-reorder (Compose `Reorderable` library)
   - Delete page, add more pages (back to camera), retake page

6. **Image filters (`:core:imaging`)**
   - Five filters, applied on the full-res image:
     - **Original** (no change)
     - **Auto** (ML Kit classifier picks the best filter)
     - **Magic Color** (adaptive white balance + saturation boost — OpenCV `cvtColor` + `CLAHE`)
     - **Grayscale** (luminance)
     - **Black & White** (adaptive threshold — OpenCV `adaptiveThreshold` with Gaussian C)
   - Filters are non-destructive: the original is kept, filter is a flag on `PageEntity`
   - Filter preview strip on the crop screen

7. **OCR pipeline (`:core:ml`)**
   - ML Kit Text Recognition v2, Latin script bundled
   - Runs on each page after capture, on `Dispatchers.Default`
   - OCR result stored in `PageEntity.ocrText`
   - OCR text also encrypted at rest (via Room `@TypeConverter` that encrypts/decrypts on read/write)
   - Progress indicator on library card: "Processing..." until OCR completes
   - Language downloader screen in settings (Devanagari, Chinese, Japanese, Korean, Arabic, etc.)

8. **PDF export (`:core:pdf`)**
   - Uses `PdfDocument` from the Android SDK for the image layer
   - Uses PDFBox-Android to add an invisible text layer positioned at the OCR bounding boxes → searchable PDF
   - Page size: auto (match image aspect) or A4 / Letter / Legal
   - Image compression: JPEG quality 85 by default, user-adjustable (60 / 85 / 95)
   - Output to `context.cacheDir/exports/<docId>.pdf`, shared via FileProvider
   - Share sheet triggered with `ACTION_SEND` + `application/pdf`

9. **Other export formats**
   - JPEG (single or ZIP of all pages)
   - PNG (lossless)
   - Plain text (just the OCR, `.txt`)
   - Encrypted ZIP (Zip4j with user-provided password)

10. **Document reader (`:feature:reader`)**
    - Swipeable pager of pages
    - Pinch-to-zoom on each page
    - Bottom bar: share, delete, rename, reorder, add page, export
    - OCR text viewer (toggle) — shows searchable, selectable text overlaid
    - `FLAG_SECURE` enabled by default (toggle in settings)

11. **Biometric app lock**
    - Settings toggle: "Require biometric to open app"
    - Uses `androidx.biometric` — BiometricPrompt with `BIOMETRIC_STRONG` fallback to device credential
    - Lock on background with configurable timeout (immediate / 30s / 1m / 5m)
    - Lock on app launch

#### Acceptance criteria

- [ ] A user can: capture 10 pages → reorder → apply filters → export as searchable PDF → share via WhatsApp, all without a network connection
- [ ] OCR accuracy ≥ 95% on a clean printed A4 document (measure against a ground-truth set of 20 docs you scan yourself)
- [ ] Search across 100 documents returns results in under 200ms
- [ ] Library list scrolls at 60fps with 500 cached documents
- [ ] APK size still under 22 MB
- [ ] Biometric lock cannot be bypassed by killing and relaunching the app
- [ ] Exported PDF opens correctly in Adobe Reader and has selectable text
- [ ] Batch delete of 50 documents completes in under 1 second and all files are actually removed from disk

---

### Phase 3 — AI Enhancements & Monetization (Weeks 5–6)

**Goal:** The features that make users choose us over CamScanner, plus AdMob integration. End of Phase 3 = this is a real, launchable product that can make money.

**Backend handshake:** None yet. Still 100% offline.

#### Deliverables

1. **Smart document type detection**
   - TFLite classifier on the captured image: receipt / ID card / business card / A4 document / whiteboard / book page
   - Auto-applies the best filter and aspect ratio for the detected type
   - Shown as a subtle chip on the crop screen, tappable to override

2. **ID card mode**
   - Special flow: capture front → capture back → auto-composes into a single A4 page with both sides
   - Perfect for passports, driver's licenses, health cards

3. **Receipt mode**
   - Taller aspect ratio, optimized B&W filter, auto-straightens crumpled receipts (more aggressive perspective correction)
   - Extracts total amount, date, and merchant name from OCR using simple regex heuristics (no LLM)
   - These fields become searchable in the library

4. **Whiteboard mode**
   - Removes glare using OpenCV `inpaint` on over-exposed regions
   - Boosts marker colors (HSV saturation boost)
   - Removes your own hand/shadow if detected

5. **Book scan mode**
   - Two-page spread → auto-splits into two pages
   - Curve correction using the TFLite `DewarpNet` lite model (~3 MB) — flattens the book-spine curve

6. **Signature tool**
   - Draw a signature on a transparent overlay with finger or stylus
   - Save up to 3 signatures in an encrypted signature store
   - Place and resize on any PDF page before export

7. **Annotations**
   - Text boxes, highlighter, redaction (black rectangle — actually destroys the underlying pixels, not just overlay)
   - Eraser
   - Undo/redo stack (max 30 steps)

8. **Redaction that actually works**
   - User draws a box over sensitive content
   - We replace those pixels with solid black in the stored image AND overwrite the corresponding OCR bounding boxes in the DB
   - Critical difference from CamScanner: the redacted data is gone forever, not just hidden with a layer

9. **Image cleanup**
   - "Remove background noise" — bilateral filter + morphological opening
   - "Sharpen text" — unsharp mask
   - "Fix lighting" — CLAHE (contrast-limited adaptive histogram equalization)
   - All applied non-destructively, stored as filter flags

10. **AdMob integration**
    - Google Mobile Ads SDK initialized lazily (not in `Application.onCreate` — that adds 200ms to cold start)
    - **UMP SDK** for consent (required for EU, and required to maximize eCPM everywhere)
    - **Ad placements:**
      - Interstitial after every 5th document export (not every export — kills retention)
      - Rewarded ad to unlock: batch export of more than 5 pages at once, unlimited OCR languages beyond the first 2, "pro" filters (there are no pro filters actually — all filters are free — but we gate the batch enhancement tool behind a rewarded ad)
      - NO banner on camera screen
      - NO interstitial inside the reader (hostile UX)
    - Ad frequency capping: maximum one interstitial per 3-minute window
    - Remote config via a simple JSON file bundled in-app for now (Firebase Remote Config comes in Phase 4)

11. **Rating prompt**
    - In-app review API (`ReviewManager`)
    - Trigger: user has exported at least 3 documents AND app has been opened on 3 different days AND no rating prompt shown in the last 90 days

12. **Onboarding**
    - 3 screens maximum: value prop, permission request, "you're ready"
    - Skippable
    - Never shown again after completion
    - No sign-up. No email. No account. This is a core selling point vs CamScanner.

13. **Play Store prep**
    - Store listing copy (EN) — keyword-loaded title and short description
    - 8 screenshots (feature graphics, not just UI shots)
    - Feature graphic (1024x500)
    - Privacy policy page (hosted somewhere — needed before upload)
    - Data safety form answers prepared

#### Acceptance criteria

- [ ] Test ad units show real AdMob test ads correctly
- [ ] UMP consent flow works for EU locale (test with a VPN)
- [ ] All special modes (ID, receipt, whiteboard, book) produce visibly better results than the default flow
- [ ] Redaction is actually destructive — verified by inspecting the raw pixel data
- [ ] APK size under 28 MB
- [ ] No regressions in Phase 1 and Phase 2 acceptance criteria
- [ ] Closed testing track published on Play Console with 12 testers opted in (start this early — it's a 14-day gate)

---

### Phase 4 — Optional Cloud Sync (Weeks 7–9)

**Goal:** Users who want it can create an account and sync their library across devices. Everything is E2E encrypted — the server never sees plaintext. This phase introduces the first network calls in the entire app.

**Backend handshake:** This is where frontend meets `BACKEND_MVP.md` Phase 1–3. Before starting, the backend must have: account creation, login with Argon2id, encrypted blob upload/download, and sync manifest endpoints live on a staging environment.

> **GATE (mandatory before 4B.1):** The Go backend MUST be deployed to staging with Backend Phase 1–3 complete. Check `PROGRESS.md` — tasks 1A.x, 1B.x, 1C.x, 2A.x, 2B.x, 3A.x must all be checked before writing any Ktor code.

#### API Contract — Endpoints Android Calls

Android communicates **only with the Go backend** over HTTPS. It never calls Python directly. All URLs below assume:
- **Production:** `https://api.scanvault.app/v1`
- **Staging:** `https://api-staging.scanvault.app/v1`

Configure the base URL via a `BuildConfig` field injected by Gradle (`buildConfigField("String", "API_BASE_URL", ...)`).

| Phase task | Method | Path | Go handler package | Notes |
|---|---|---|---|---|
| 4B.1 — AccountScreen | `POST` | `/accounts` | `internal/accounts` | Create account; body: `{email, password_hash, wrapped_key, argon2_params}` |
| 4B.1 — Login | `POST` | `/sessions` | `internal/accounts` | Returns Paseto token (15 min) + refresh token (30 days) |
| 4B.1 — Refresh | `PUT` | `/sessions` | `internal/accounts` | Rotate tokens |
| 4B.2 — Upload blob | `POST` | `/vault/blobs` | `internal/vault` | Multipart; body is AES-256-GCM ciphertext — server stores opaque bytes |
| 4B.2 — Download blob | `GET` | `/vault/blobs/{id}` | `internal/vault` | Returns raw ciphertext; client decrypts |
| 4B.3 — Sync manifest | `GET` | `/vault/manifest` | `internal/vault` | Returns `[{doc_id, version, updated_at, size_encrypted}]` |
| 4B.3 — Delete blob | `DELETE` | `/vault/blobs/{id}` | `internal/vault` | Marks tombstone; propagated to other devices via manifest |
| 4B.6 — Delete account | `DELETE` | `/accounts/me` | `internal/accounts` | Purges all blobs; client wipes local data |
| 4C.4 — Submit AI task | `POST` | `/intelligence/tasks` | `internal/intelligence` | Body: `{type, source_blob_id}`. Returns `{task_id, status:"queued"}` |
| 4C.5 — Poll AI result | `GET` | `/intelligence/tasks/{id}` | `internal/intelligence` | Returns `{status, result_blob_id?, error?}` |

**Auth header for all requests (except `/accounts` POST and `/sessions` POST):**
```
Authorization: Bearer <paseto_token>
```

**Error contract** (matches Go backend conventions):
- `400` — invalid input; body: `{"error": "human readable message"}`
- `401` — missing/expired token
- `404` — resource not found (also returned for another user's resource — never `403`)
- `429` — rate limited; `Retry-After` header present
- `503` — backend or intelligence layer temporarily unavailable

#### `:core:network` Module — Implementation Notes

The module at `android/core/network/` is **intentionally stubbed until 4B.2**. When implementing:

```
core/network/
├── src/main/java/com/scanvault/core/network/
│   ├── NetworkModule.kt         ← Hilt: provides KtorHttpClient (OkHttp engine, cert pin)
│   ├── ScanVaultApiClient.kt    ← All HTTP calls as suspend fns, one fn per endpoint above
│   ├── model/                   ← Serializable request/response DTOs
│   │   ├── AccountModels.kt
│   │   ├── VaultModels.kt
│   │   └── IntelligenceModels.kt
│   └── auth/
│       ├── TokenStore.kt        ← Stores Paseto token in EncryptedSharedPreferences
│       └── AuthInterceptor.kt   ← Ktor feature: injects + auto-refreshes tokens
```

**Certificate pinning config** (add to `NetworkModule.kt`):
```kotlin
install(HttpTimeout) { requestTimeoutMillis = 30_000 }
engine {
    config {
        certificatePinner(CertificatePinner.Builder()
            .add("api.scanvault.app", "sha256/<PRIMARY_PIN>")
            .add("api.scanvault.app", "sha256/<BACKUP_PIN>")
            .build())
    }
}
```
Replace `<PRIMARY_PIN>` and `<BACKUP_PIN>` with real SHA-256 pins from the staging cert before 4B.1 tests run.

#### Intelligence Feature — What Android Does in Phase 4C

The intelligence flow is **user-opt-in per document**. Android never auto-uploads to AI.

1. User taps "Cloud AI" on a document → Android shows consent dialog
2. On consent: Android decrypts the document pages locally (using `K_master`)
3. Android uploads plaintext images to Go: `POST /vault/blobs` tagged with `processing: true` (1-hour TTL)
4. Android calls `POST /intelligence/tasks` with `{type: "ocr.enhance", source_blob_id: "..."}`
5. Go enqueues to Redis (`scanvault:intelligence:tasks`) → Python processes → writes result blob
6. Android polls `GET /intelligence/tasks/{id}` every 5 seconds until status = `completed` or `failed`
7. On `completed`: Android downloads result blob, re-encrypts with `K_master`, stores locally; ephemeral processing blob is auto-deleted by Go after 1 hour

#### Deliverables

1. **Account screen**
   - Email + password signup
   - Password strength meter (zxcvbn-kotlin)
   - Minimum 10 characters enforced
   - Forgot password flow (sends an email with a time-limited recovery code — recovery does NOT decrypt data, it only restores account access; user's data is lost if they forget the password, and we tell them that clearly)
   - Optional passkey support (`androidx.credentials` with `PublicKeyCredential`)

2. **Client-side E2E crypto (`:core:network`)**
   - **libsodium-jni** (or lazysodium-android)
   - On signup:
     - Generate a random 256-bit master key `K_master`
     - Derive `K_auth` and `K_encrypt` from the user's password using Argon2id (client-side — server also Argon2ids the auth hash for double protection)
     - Wrap `K_master` with `K_encrypt` using XChaCha20-Poly1305
     - Upload only: email, Argon2id(password) auth hash, wrapped `K_master`, Argon2id params
   - On login:
     - Fetch wrapped `K_master` + params
     - Derive `K_encrypt` locally, unwrap `K_master`
     - Store `K_master` in Android Keystore, never write it to DataStore
   - Every document is encrypted with a per-document key derived from `K_master` + document UUID via HKDF
   - Server sees: blob ID, blob size, timestamps, user ID. Server does NOT see: titles, OCR text, images, filenames, page counts.

3. **Sync engine**
   - A `SyncService` (not `android.app.Service` — a Hilt-scoped class) driven by `WorkManager`
   - Operations: `upload`, `download`, `delete`, `rename` — each as a `OneTimeWorkRequest` with exponential backoff
   - Conflict resolution: last-write-wins based on server timestamp, with conflict backup ("original" and "from other device" both kept locally)
   - Sync manifest: server has a per-user list of `{docId, version, updatedAt, sizeEncrypted}` — client compares against local and figures out what to upload/download

4. **Sync states in the UI**
   - Each library card shows a small icon: cloud-done, cloud-uploading, cloud-pending, local-only
   - Global sync status in the top bar
   - "Sync now" button in settings
   - "Pause sync on mobile data" setting (default ON)

5. **Certificate pinning**
   - Pin the backend API's certificate SHA-256 in Ktor's OkHttp engine config
   - Backup pin for rotation
   - If pin fails, show an error and refuse to sync (do NOT fall back to unpinned — that defeats the point)

6. **Tamper check**
   - On first launch of a signed release build, compute `PackageInfo.signatures[0]` SHA-256 and compare to a hardcoded value
   - If mismatch, disable sync entirely (core scanning still works — we don't want to brick the app)

7. **Account management**
   - Change password (re-wraps `K_master` with the new password-derived key)
   - Delete account (sends delete request to backend, then wipes everything locally)
   - Log out (wipes `K_master` from Keystore, but keeps local scans — user can log back in)
   - Data export: user can request a full download of all their encrypted blobs for portability

8. **Network layer**
   - Ktor client with OkHttp engine, HTTP/2, connection pooling
   - Request retry with exponential backoff (3 attempts)
   - Offline queue: any sync action that fails because of no network is queued and retried when connectivity returns
   - `NetworkType.UNMETERED` constraint on WorkManager requests by default (respects the "mobile data" preference)

#### Acceptance criteria

- [ ] Create account → scan 5 documents → log in on a second device → see all 5 documents (only images and metadata, everything still E2E encrypted)
- [ ] Verified with a proxy: server never receives any plaintext image, OCR text, or title
- [ ] Cert pinning verified by swapping the cert and confirming the app refuses to connect
- [ ] Sync works through airplane mode → restore (offline queue fires correctly)
- [ ] Change password works and old password no longer decrypts the vault
- [ ] Delete account wipes both local and server data

---

### Phase 5 — Polish, Performance, Launch (Weeks 10–11)

**Goal:** Everything up to this point has been "make it work." This phase is "make it great" and publish to production.

**Backend handshake:** Backend must be on Phase 5 (production-ready, monitored, backed up).

#### Deliverables

1. **Baseline Profiles**
   - Run Macrobenchmark → generate baseline profile for cold start, library scroll, camera launch, PDF export
   - Ship with release APK — gives 20–30% startup improvement on first launch

2. **R8 full mode + resource shrinking**
   - Verify obfuscation doesn't break reflection-heavy libraries (Hilt, kotlinx.serialization)
   - Tree-shake unused OpenCV modules (imgcodecs, videoio, etc.) → saves ~15 MB

3. **Dark mode**
   - Full Material 3 dynamic color on Android 12+
   - Manual dark/light/system toggle
   - Every screen tested in both

4. **Accessibility pass**
   - Every interactive element has `contentDescription`
   - TalkBack tested on the main flow
   - Minimum touch target 48dp
   - Text contrast ratio ≥ 4.5:1 everywhere
   - Font scaling up to 200% doesn't break layouts

5. **Localization**
   - English (default)
   - Hindi, Nepali, Spanish, Portuguese, Arabic, French, German, Indonesian
   - Use Play Store auto-translation as a starting point, but hand-review the top 3 languages
   - RTL support tested for Arabic

6. **Widgets**
   - "Scan now" home-screen widget (Glance-based)
   - "Recent scans" widget (1x4 showing latest 4 thumbnails)

7. **Quick Settings tile**
   - One-tap scan from the pull-down shade

8. **Share extension**
   - Receive images from other apps via `ACTION_SEND` → import as a new document
   - Useful for Gmail attachments, Drive files, etc.

9. **Performance pass**
   - Cold start < 500ms on Pixel 6a (measured)
   - Warm start < 200ms
   - Library scroll @ 60fps with 1000 documents
   - Memory footprint < 150 MB during normal use
   - No memory leaks (LeakCanary in debug builds, zero reported leaks)
   - Battery test: 1 hour of scanning drains < 8% on a 4000mAh battery

10. **Error reporting**
    - Custom crash handler (no Crashlytics) that writes crash logs to encrypted local storage
    - "Send crash report" button in settings — user manually attaches the log to an email, nothing auto-uploads

11. **Backup / restore**
    - Local backup: encrypted ZIP of the entire library + settings
    - Restore from backup file
    - This is separate from cloud sync — for users who don't want an account

12. **Play Store submission**
    - Closed testing → open testing → production
    - Targeted countries on launch: US, UK, Canada, Australia, Germany, France (Tier 1 for eCPM)
    - Staged rollout: 10% → 50% → 100% over 2 weeks
    - Data safety form: declare only "app activity" for ads, and "device IDs" for AdMob
    - Content rating: complete IARC questionnaire

13. **Post-launch monitoring**
    - Daily manual check of Play Console crash rate (target: < 0.5%)
    - Weekly review of 1-star reviews
    - AdMob dashboard: daily eCPM and fill rate check

#### Acceptance criteria

- [ ] All Macrobenchmark targets met
- [ ] APK size under 30 MB (under 25 MB is ideal)
- [ ] Passes Google Play pre-launch report with zero critical issues
- [ ] 12 testers complete the 14-day closed testing period
- [ ] Production release live, visible on the Play Store
- [ ] At least 50 organic installs in the first week (without paid acquisition)
- [ ] Crash-free users > 99.5%
- [ ] Average rating ≥ 4.3 after first 20 reviews

---

## 5. Phase Interconnection Map

```
Phase 1 (offline capture)
   │
   │  Uses: :core:data (EncryptedImageStore), :core:imaging (OpenCV)
   │
   ▼
Phase 2 (library + OCR + PDF)
   │
   │  Reuses Phase 1 storage → extends schema, never rewrites
   │  Uses: :core:ml, :core:pdf
   │
   ▼
Phase 3 (AI modes + monetization)
   │
   │  Reuses Phase 2 document pipeline → adds filters, not replaces
   │  First third-party SDK: AdMob
   │
   ▼
Phase 4 (optional sync)        ←── meets BACKEND_MVP Phase 1-3
   │
   │  First network code ever → :core:network
   │  Wraps existing EncryptedImageStore → adds an UploadQueue layer
   │  No existing code is rewritten; sync is purely additive
   │
   ▼
Phase 5 (polish + launch)     ←── meets BACKEND_MVP Phase 4-5
```

The key discipline: **never rewrite earlier phase code**. Every new phase adds modules and extends interfaces. If you find yourself needing to rewrite Phase 1 code during Phase 3, stop and fix the abstraction in Phase 1 retroactively — don't force it through.

---

## 6. What We Deliberately Do NOT Build in MVP

Cutting scope is how a solo dev actually ships. These are documented as "not MVP" so you don't feel tempted:

- Subscription tiers (everything is free with ads in MVP; subscriptions come in v2)
- Team sharing / collaboration
- Real-time collaborative editing
- Cloud OCR (we do on-device only — faster, private, free)
- AI "ask your document" LLM features (v2 candidate)
- Handwriting recognition beyond what ML Kit gives for free
- Form filling / fillable PDF
- Document comparison / diff
- Web version
- Desktop version
- Integrations with Google Drive, Dropbox, OneDrive (v2 — use system share sheet for now)
- Custom fonts in annotations
- Voice notes attached to pages
- Multi-user on the same device

---

## 7. Definition of Done (every phase)

A phase is not done until:

1. All acceptance criteria in this doc pass, verified on real hardware
2. Detekt is clean, zero warnings
3. Release build (`assembleRelease`) succeeds with R8 full mode
4. Unit test coverage for `:core:*` modules is ≥ 70%
5. The matching backend phase is also done and deployed to staging
6. A 30-minute manual test session on a mid-range device finds no blockers
7. The previous phase's tests still pass (regression guard)
8. You have actually installed the signed APK on your own daily-driver phone and used it for a day
