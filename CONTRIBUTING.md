# Contributing to Paperkeep

Thanks for your interest in Paperkeep. This is a privacy-first Android document scanner with no backend and no telemetry. The focus is narrow by design.

Before you start
- Read [CLAUDE.md](CLAUDE.md) for architecture and non-negotiable rules
- Read [SECURITY.md](SECURITY.md) for vulnerability reporting
- Confirm you can run Android Studio and JDK 17

What we accept
- Android-only improvements in Kotlin and Jetpack Compose
- Performance and stability fixes
- UI and UX polish that preserves the offline-first focus
- Tests for any new or changed behavior

What we do not accept
- Backend or cloud features of any kind
- New analytics, telemetry, or tracking SDKs
- Plaintext on-disk storage for documents or OCR
- XML UI work outside the splash screen

Development quick start
1. `cd android`
2. `./gradlew :app:assembleDebug`
3. `./gradlew testDebugUnitTest detekt lintDebug`

Commit convention
Use `<type>(<scope>): <subject>`
- `type`: feat, fix, perf, refactor, docs, test, build, ci, chore
- `scope`: android, core, feature, docs, deps

Pull request checklist
- Tests added for new behavior
- All tests and lint pass
- No new network traffic added
- No plaintext document data written to disk
- Screenshots updated if UI changes are user-visible

Reporting bugs
Use the issue templates and include:
- Device model and Android version
- App version and build type
- Clear reproduction steps and expected behavior

License note
By submitting a pull request, you grant permission for your contribution to be used in this project under the repository license.
