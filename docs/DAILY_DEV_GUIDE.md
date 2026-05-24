# Paperkeep — Daily Dev & Release Guide

> The everyday loop: **fix a bug → make CI green → push → cut a release.**
> Keep this updated whenever the pipeline changes.

---

## 0. TL;DR cheat-sheet

```powershell
# from android/  (set JAVA_HOME to Android Studio JBR first — see reference.md §Build)
.\gradlew.bat detekt lintDebug testDebugUnitTest   # the 3 CI gates, locally
```
If all three pass locally, CI will pass. Then:
```powershell
git checkout -b fix/<short-name>
git commit -am "fix(<scope>): <subject>"
git push -u origin fix/<short-name>      # open a PR → Android CI runs
```
To ship a version: bump `VERSION` + `versionCode`, tag `vX.Y.Z`, push the tag.

---

## 1. The CI gates (what must be green)

Three workflows run in `.github/workflows/`:

| Workflow | Trigger | What it checks |
|---|---|---|
| **Android CI** (`android-ci.yml`) | push/PR to `main` touching `android/**` | detekt → lint → unit tests → debug APK → instrumented tests (API 26/30/34) → release R8 assemble |
| **Security Scan** (`security-scan.yml`) | push to `main`, weekly cron, manual | OWASP dependency CVEs, TruffleHog secrets, detekt security gate, banned `android.util.Log.*` check, hardcoded-secret patterns |
| **Android Release** (`android-release.yml`) | tag `vX.Y.Z` or manual | builds signed AAB+APK, **creates a GitHub Release**, and uploads to Play Store **only if Play secrets exist** (else prints "coming soon" and stays green) |

### Reproduce every gate locally before pushing
```powershell
cd android
.\gradlew.bat detekt              # static analysis (all modules)
.\gradlew.bat lintDebug           # Android lint (catches NewApi etc.)
.\gradlew.bat testDebugUnitTest   # JVM unit tests
.\gradlew.bat assembleRelease     # proves R8/ProGuard rules are valid
```
Instrumented tests (`connectedDebugAndroidTest`) need an emulator/device; CI runs
them for you on API 26/30/34.

---

## 2. The bug-fix loop (do this every time)

1. **Reproduce / locate.** For a CI failure, read the failing step's log:
   ```powershell
   gh run list --limit 10
   gh run view <run-id> --log-failed
   ```
2. **Fix the real cause**, not the symptom. Prefer a correct code change over
   suppressing a warning.
3. **Re-run the relevant gate locally** (see §1).
4. **Update `PROGRESS.md`** (check the box / add a dated note) per the test-gate rule.
5. **Branch, commit, push, PR.** Never commit straight to `main`.
   Commit format: `<type>(<scope>): <subject>` — types `feat|fix|perf|refactor|docs|test|build|ci|chore`.
6. **Watch CI go green:** `gh run watch` or the Actions tab.

### detekt: the baseline rule
detekt is enforced at `maxIssues: 0`. Pre-existing issues are snapshotted in
per-module `detekt-baseline.xml` files so they don't block CI, **but any NEW
issue fails the build.** If you legitimately add complexity:
```powershell
cd android
.\gradlew.bat detektBaseline      # regenerates baselines for all modules
```
Only regenerate a baseline when the new issue is genuinely acceptable — the
default is to fix the code. Compose conventions (PascalCase `@Composable`,
long declarative UI builders) are already allowed via `detekt.yml`.

### Logging rule
Never call `android.util.Log.*` in `src/main`. Use `DebugLog` (`core:common`) —
it's `FLAG_DEBUGGABLE`-gated and stripped from release by ProGuard. The Security
Scan workflow fails the build on any bare `Log.<level>(` outside `DebugLog.kt`.

---

## 3. Cutting a release

### 3a. Bump the version (single source of truth)
- `VERSION` — human version, e.g. `2.0.0-alpha.2`
- `android/app/build.gradle.kts` — `versionName` (match VERSION) and bump
  `versionCode` by 1 (Play requires a strictly increasing integer)
- Add `android/store/release-notes-<VERSION>.txt` (used as the GitHub Release body)

### 3b. Trigger the pipeline
**Option A — tag (recommended):**
```powershell
git tag v2.0.0-alpha.2
git push origin v2.0.0-alpha.2
```
**Option B — manual:** Actions → "Android Release" → *Run workflow* → pick track.

### 3c. What happens
- ✅ **GitHub Release** is always created with the APK (and AAB) attached.
- 🟡 **Play Store** is skipped with a "coming soon" notice **until** the Play
  secrets are configured. The job is **green** either way.
- If the keystore secret is missing, the build is **unsigned** (fine for a GitHub
  pre-release; not for Play).

---

## 4. Enabling Play Store + signed releases (when the Console account is ready)

Add these repo secrets (Settings → Secrets and variables → Actions). Once
`PLAY_STORE_SERVICE_ACCOUNT_JSON` exists, the release workflow automatically
flips from "coming soon" to real uploads — **no code change needed.**

| Secret | How to get it |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 android/paperkeep-upload.jks` (keep the .jks forever) |
| `KEYSTORE_PASSWORD` | from `secrets/release-keystore.txt` (local, gitignored) |
| `KEY_ALIAS` | the upload key alias |
| `KEY_PASSWORD` | the key password |
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` | Play Console → Setup → API access → service account JSON |

Then follow the Play Console steps in `reference.md` §"Play Console release steps"
and `android/store/` checklists. First upload must be done **manually** in the
Console (Google requires the first AAB by hand); afterwards Fastlane `supply`
handles subsequent uploads automatically.

> ⚠️ Never commit `*.jks`, `keystore.properties`, or the service-account JSON.
> They're gitignored — keep it that way.

---

## 5. Changelog of pipeline fixes

| Date | Change |
|---|---|
| 2026-05-24 | Fixed detekt CI failure (17 app-module issues hand-fixed; ~155 pre-existing issues across 13 modules baselined). Configured `detekt.yml` for Compose conventions. |
| 2026-05-24 | Fixed Security Scan `Log.*` gate that false-matched the sanctioned `DebugLog` wrapper. |
| 2026-05-24 | Fixed release-only bug: bottom navigation bar hidden in R8 builds (route classes obfuscated → `qualifiedName` desync). Now uses type-safe `hasRoute<T>()` + ProGuard keep rules. |
| 2026-05-24 | Fixed `BackupCoordinator` `getLongVersionCode()` crash on API 26/27 (lint NewApi). |
| 2026-05-24 | Rebuilt `android-release.yml` into a dual-target pipeline: always GitHub Release, Play Store gated on secrets ("coming soon" until configured). |
