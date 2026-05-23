# Paperkeep Reference (single source for Claude)

Use this file as the centralized context for future work. It summarizes the project rules, build steps, and Play Store release flow. Keep it updated.

## Project summary
- Product: Paperkeep (Android-only document scanner)
- Package: app.paperkeep
- No backend, no cloud, no telemetry
- On-device OCR + encrypted storage (AES-256-GCM)
- Monetization: AdMob interstitials + one-time Pro IAP

## Non-negotiable rules
- Android-only. No server code, no sync, no accounts
- No telemetry/analytics SDKs (no Firebase Analytics, Crashlytics, Sentry, AppsFlyer)
- No network traffic except Google SDKs (AdMob, UMP, Play Billing, Play Integrity, ML Kit module download)
- All on-disk data encrypted except short-lived cache exports
- Keys only in Android Keystore
- Compose UI only (no XML except splash)

## Where to read first
- CLAUDE.md (project brain)
- PROGRESS.md (task log)
- docs/PROMPT.md (rules and test gate)

## Versioning
- VERSION file is the single version source (currently 2.0.0-alpha.1)
- app/build.gradle.kts uses versionName and versionCode

## Build (Windows)
Use Android Studio JBR as JAVA_HOME.

PowerShell:
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

Build release AAB + APK:
cd android
.\gradlew.bat :app:bundleRelease :app:assembleRelease

Outputs:
- AAB: android/app/build/outputs/bundle/release/app-release.aab
- APK: android/app/build/outputs/apk/release/app-release.apk

## Release signing (do NOT commit secrets)
- Keystore: android/paperkeep-upload.jks (gitignored)
- Config: android/keystore.properties (gitignored)
- Password reference (local only): secrets/release-keystore.txt (gitignored)
- If a password is required, use the value stored in the password reference file above.
- This keystore must be preserved forever to update the Play Store app.

## Play Console release steps (when account is ready)
1) Create Play Console account
2) Create a new app (app.paperkeep)
3) Upload AAB: android/app/build/outputs/bundle/release/app-release.aab
4) Complete store listing (use android/store/ assets)
5) Use the same upload keystore above; never lose it
6) Set up internal/closed testing first, then production
7) Verify AdMob is configured with real ad unit IDs before production

## GitHub release (for sharing APK today)
- Create GitHub release and attach APK/AAB
- Latest release tag: v2.0.0-alpha.1
- Public release notes: secrets/github-release-notes.md

## Current app state notes
- Capture flow uses ML Kit Document Scanner
- 10 filters + OpenCV shadow removal
- Encrypted backups via SAF + Argon2id + AES-256-GCM
- Widgets and Quick Settings tile exist

## Safety reminders
- Do not commit keystore files or passwords
- Do not add new outbound network calls
- Keep APK signature verification in place

## Useful docs
- docs/PAPERKEEP_DESIGN.md (read only needed sections)
- docs/PRIVACY_POLICY.md
- docs/TERMS_OF_SERVICE.md
- android/store/ (Play Store metadata and checklists)
