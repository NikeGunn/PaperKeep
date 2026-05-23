<div align="center">

# 📄 Paperkeep

### Privacy-first Android document scanner with on-device OCR.

**On-device. Encrypted. No account. No telemetry. No backend.**

[![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?logo=android&logoColor=white&style=for-the-badge)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white&style=for-the-badge)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/license-Proprietary-lightgrey?style=for-the-badge)](#-license)

[**About**](#about) · [**Focus**](#focus) · [**Features**](#-features) · [**Architecture**](#-architecture) · [**Security**](#-security-model) · [**Build**](#-build--run) · [**Roadmap**](#-roadmap) · [**Contributing**](#-contributing) · [**Tags**](#tags)

</div>

---

## About

Paperkeep is a privacy-first Android document scanner built for offline capture, on-device OCR, and encrypted storage. It is a CamScanner alternative with no account, no cloud, and no telemetry. Every scan stays on your phone unless you export or back it up.

The capture flow uses Google's ML Kit Document Scanner for edge detection, auto-shutter, and multi-page batches. After capture, Paperkeep runs OpenCV cleanup and lets you choose from 10 filters before saving. Exports include searchable PDF with an OCR text layer.

Best for:
- Receipts, invoices, and contracts
- IDs, cards, and passports
- Whiteboards and books

---

## Focus

Paperkeep is deliberately narrow. It optimizes for:
- Offline capture and on-device OCR
- Data ownership (no accounts, no cloud)
- Encryption-by-default storage
- Fast capture to clean PDF workflow
- A calm UI with minimal choices

Non-goals:
- Cloud sync or accounts
- Telemetry or analytics of any kind
- Cross-platform parity at the cost of Android quality
- Background uploads or auto-sharing

## ✨ Features

### 📷 Capture
- **Google‑grade edge detection** via ML Kit Document Scanner (auto‑shutter, manual corner adjustment, multi‑page batch)
- **Automatic shadow removal** — illumination normalisation in LAB colour space removes hand shadows, vignettes, and uneven lighting
- **Auto‑orient to A4‑fit** — landscape captures rotate to portrait so exports print cleanly
- **10 image filters** with live thumbnail previews: Original · Auto · Magic Color · Document · Lighten · Vivid · Whiteboard · Grayscale · B&W · Sepia
- **Smart classification** picks the best default filter per document type (receipt, ID, letter, whiteboard)

### 📚 Library
- **Folder chips** (All, Favorites, Archive, and user folders) with **auto-rules** based on document type (receipt, ID, whiteboard, book)
- **Full-text search** across OCR content runs entirely on-device against an SQLite FTS index
- **Recent scans widget** + Quick Settings tile + share-import so any image or PDF can be imported as a scan

### 📖 Reader & Edit
- **Horizontal pager** with pinch-to-zoom and pan; single tap hides or shows controls
- **Edit toolbar**: Reorder pages · Add pages · Rename · Share · Export · Delete
- **OCR text overlay** with selectable text per page

### 🔐 Privacy & Security
- **AES‑256‑GCM encryption** for every page image, thumbnail, OCR blob, signature, and crash log on disk
- **Hardware‑backed Keystore** with StrongBox where available, automatic TEE fallback
- **Biometric lock** with configurable timeout (immediate · 1 min · 5 min · 30 min · never)
- **Optional screenshot protection** (FLAG_SECURE) to block screenshots and screen recording
- **Encrypted backups** via Argon2id (m=128MiB, t=4) + AES‑256‑GCM streamed over a custom ZIP container, with per‑page SHA‑256 integrity verification on restore
- **Privacy‑first task switcher** — recent‑apps thumbnail shows a static brand colour, not your documents

### 📤 Export
- **PDF export** with searchable OCR text layer
- **Share sheet** to any app (Drive, Dropbox, Gmail, SAF anywhere)
- **Storage Access Framework** backups go straight to user‑chosen locations — no upload, no middleman

---

## 🛠️ Tech Stack

<table>
<tr>
<td width="50%" valign="top">

**Language & Build**
- Kotlin 2.0
- Gradle 8.14 + KSP (no kapt)
- Hilt for DI
- Multi‑module Android project

**UI**
- Jetpack Compose + Material 3
- Material 3 Expressive (motion + shape tokens)
- Navigation Compose with type‑safe routes
- Edge‑to‑edge with proper inset handling

</td>
<td width="50%" valign="top">

**Capture & Vision**
- Google ML Kit Document Scanner
- CameraX for legacy capture path
- OpenCV 4.10 (shadow removal, perspective warp)
- TFLite for on‑device classification

**Data**
- Room 2.6 (10 migrations on file)
- SQLite FTS for search
- DataStore for preferences
- Argon2id KDF for backup passwords

</td>
</tr>
</table>

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Paperkeep Android App                    │
│  Kotlin · Jetpack Compose · Material 3 Expressive · Hilt    │
└─────────────────────────────────────────────────────────────┘
            │                                  ▲
            │ user‑initiated export            │ user‑initiated backup
            ▼                                  │
   ┌─────────────────┐               ┌──────────────────────┐
   │  Share / PDF    │               │  SAF → anywhere      │
   │  via system     │               │  (Drive, Dropbox,    │
   │  share sheet    │               │   SD, USB‑OTG, …)    │
   └─────────────────┘               └──────────────────────┘
```

### Module graph

```
app  ─────┐
          ├─► feature:scanner ─┐
          ├─► feature:library  │
          ├─► feature:reader   ├─► core:ui · core:imaging · core:ml
          ├─► feature:settings │       core:data · core:domain
          └─► feature:onboarding         core:common · core:crypto
                                         core:security · core:pdf
                                         core:ads · core:backup
```

Feature modules depend on `:core:*` only — never on each other. Domain models flow up, navigation flows down, nothing crosses sideways.

---

## 🔒 Security Model

| Asset                          | Location              | Encryption                     | Key custody                 |
| ------------------------------ | --------------------- | ------------------------------ | --------------------------- |
| Page images                    | `filesDir/scans/`     | AES‑256‑GCM                    | Android Keystore (StrongBox)|
| Thumbnails                     | `filesDir/scans/`     | AES‑256‑GCM                    | Android Keystore (StrongBox)|
| OCR text                       | Encrypted Room column | AES‑256‑GCM                    | Android Keystore (StrongBox)|
| Crash logs                     | `filesDir/crash/`     | AES‑256‑GCM                    | Android Keystore (StrongBox)|
| Backups (ZIP)                  | User‑chosen (SAF)     | AES‑256‑GCM + Argon2id KDF      | **User password only**      |
| Cache (temp share PDFs)        | `cacheDir/exports/`   | Plaintext (60s lifetime)        | OS file permissions         |

### Network policy

```kotlin
// The complete allowlist of outbound network operations:
val PERMITTED_NETWORK_TRAFFIC = listOf(
    "Google AdMob (ads — disabled with Pro)",
    "Google UMP (consent — required by AdMob)",
    "Google Play Billing (IAP only)",
    "Google Play Integrity (anti‑tamper)",
    "ML Kit module download (one‑time, Play Services)",
)
// Anything outside this list is a bug.
```

No Firebase Analytics. No Crashlytics. No Sentry. No AppsFlyer. No Adjust. No HTTP client library in the codebase — `OkHttp`, `Ktor`, `Retrofit` are all banned.

---

## 🚀 Build & Run

### Prerequisites
- Android Studio Hedgehog (2023.1) or newer
- Android SDK 36, NDK 25
- JDK 17

### Quick start

```bash
# Clone
git clone https://github.com/<your-org>/paperkeep.git
cd paperkeep/android

# Build debug APK
./gradlew :app:assembleDebug

# Install + run on connected device
./gradlew :app:installDebug
adb shell am start -n app.paperkeep.debug/app.paperkeep.MainActivity
```

### Project layout

```
android/
├── app/                     The launchable Android application
├── benchmark/               Macrobenchmark module (Phase 5)
├── core/
│   ├── ads        AdMob + UMP integration (opt-in)
│   ├── backup     Encrypted backup engine (Argon2id + AES-GCM)
│   ├── common     Cross-module utilities, dispatchers, logging
│   ├── crypto     Keystore wrapper, encryption helpers
│   ├── data       Room database, DAOs, repositories
│   ├── domain     Domain models (pure Kotlin)
│   ├── imaging    OpenCV bridge, filters, shadow removal
│   ├── ml         TFLite classifier, OCR orchestration
│   ├── pdf        PDF generation with OCR text layer
│   ├── security   Biometric lock, FLAG_SECURE management
│   └── ui         Theme, typography, shared composables
├── feature/
│   ├── library      Document library, search, folders
│   ├── onboarding   First-launch flow
│   ├── reader       Document viewer + Edit toolbar
│   ├── scanner      Capture pipeline + Filter review
│   └── settings     Settings screen
└── store/                   Play Store metadata
```

### Run the test suite

```bash
# Unit tests (~966 tests, runs in 1‑2 min)
./gradlew testDebugUnitTest

# Lint + Detekt
./gradlew lintDebug detekt

# Macrobenchmark (requires connected device)
./gradlew :benchmark:connectedCheck
```

---

## 🗺️ Roadmap

Paperkeep ships in **5 phases**:

- [x] **Phase 1** — Foundation, capture, encrypted storage
- [x] **Phase 2** — Library, OCR, PDF export, biometric lock
- [x] **Phase 3** — Smart modes (ID / receipt / whiteboard / book), AdMob, edge-detection v2 ← *capture pipeline rebuilt on ML Kit + 10 filters + shadow removal*
- [x] **Phase 4** — Local encrypted backup (SAF), polish, accessibility, i18n, widgets, storage manager
- [x] **Phase 5** — On-device summariser, Pro IAP, perf pass, launch prep
- [ ] **Play Store** — Closed testing (≥12 testers, 14-day window) → Production

See [`PROGRESS.md`](PROGRESS.md) for the full task log.

---

## 🤝 Contributing

This is currently a solo project under active development. Issues and feedback are welcome.

Start here:
- [CONTRIBUTING.md](CONTRIBUTING.md)
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- [SECURITY.md](SECURITY.md)

Ways to help:
- Device testing reports (screenshots, logs, reproduction steps)
- UI and UX polish suggestions
- Accessibility or localization fixes
- Performance profiling or benchmark runs

Before opening a PR:
1. Read [`CLAUDE.md`](CLAUDE.md) — it documents the architecture and non‑negotiable rules
2. Run `./gradlew testDebugUnitTest detekt lintDebug` — must be green
3. Follow the commit convention: `<type>(<scope>): <subject>` where `type ∈ {feat, fix, perf, refactor, docs, test, build, ci, chore}` and `scope ∈ {android, core, feature, docs, deps}`

---

## 📜 License

Proprietary — © Nikhil Bhagat. All rights reserved.

The source code in this repository is published for transparency. It is not licensed for redistribution, derivative works, or use outside contributing to this project.

---

## Tags

android document scanner, offline scanner, OCR, PDF scanner, privacy-first, encrypted storage, on-device OCR, ML Kit document scanner, OpenCV, Kotlin, Jetpack Compose, CamScanner alternative

## 🙏 Acknowledgements

- **Google ML Kit** — document scanner & on-device text recognition
- **OpenCV** — perspective transforms, illumination normalisation
- **Jetpack Compose** — the UI toolkit that made this codebase enjoyable to write
- **CamScanner & Adobe Scan** — for setting the bar on what "polished" looks like in this category

---

<div align="center">

**Paperkeep** — built in Kotlin, on Android, for people who actually own their documents.

<sub>If you like what you see here, the best way to support the project is to install the app when it lands on the Play Store.</sub>

</div>
