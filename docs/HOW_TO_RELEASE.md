# How to Release a New Update — Paperkeep

> The simple, copy-paste steps to ship a new version. Follow top to bottom.
> Releasing is a **deliberate, separate action** — merging a PR does NOT publish.

---

## The big picture (read once)

```
1. Write code on a branch  →  open PR  →  checks go green  →  YOU merge to main
2. Bump the version numbers  →  commit
3. Push a tag  vX.Y.Z        →  this is what actually publishes:
                                  • GitHub Release (always)
                                  • Play Store push (once Play secrets exist;
                                    "coming soon" until then)
```

Step 1 lands your code. **Step 3 (pushing the tag) is the release.** Tags are not
branches, so branch protection on `main` never blocks them.

---

## Step-by-step

### 1. Make sure `main` is up to date and green
```powershell
git checkout main
git pull
```
Your latest work should already be merged via a PR. If not, merge it first.

### 2. Bump the version (3 places — keep them in sync)

Pick the new version, e.g. `2.0.0-alpha.2`. Edit:

| File | Change |
|---|---|
| `VERSION` | `2.0.0-alpha.2` |
| `android/app/build.gradle.kts` → `versionName` | `"2.0.0-alpha.2"` (match VERSION) |
| `android/app/build.gradle.kts` → `versionCode` | bump by 1 (e.g. `1` → `2`) |

> ⚠️ `versionCode` must **always go up** by at least 1, never repeat or go down —
> Play Store rejects a build whose versionCode it has already seen.

### 3. Write release notes (optional but recommended)
Create `android/store/release-notes-v2.0.0-alpha.2.txt` with a short "what's new".
This text becomes the GitHub Release description. If you skip it, a default line
is used.

### 4. Commit the version bump
```powershell
git add VERSION android/app/build.gradle.kts android/store/release-notes-v2.0.0-alpha.2.txt
git commit -m "chore(android): bump version to 2.0.0-alpha.2"
git push
```

### 5. Tag and push — **this triggers the release**
```powershell
git tag v2.0.0-alpha.2
git push origin v2.0.0-alpha.2
```
That's it. Go to **GitHub → Actions → "Android Release"** and watch it run.

---

## What the release pipeline does automatically

When the tag lands, `android-release.yml`:
1. Runs release unit tests.
2. Builds the signed AAB + APK (unsigned if no keystore secret yet).
3. **Creates a GitHub Release** named `Paperkeep 2.0.0-alpha.2` with the APK/AAB
   attached — testers can download immediately.
4. **Play Store:** uploads automatically **if** `PLAY_STORE_SERVICE_ACCOUNT_JSON`
   secret exists. Until you add it, the step prints **"coming soon"** and the job
   stays green.

You don't touch the build machine — it's all automatic from the tag.

---

## Version number cheat-sheet

`2.0.0-alpha.2` → `MAJOR.MINOR.PATCH-prerelease`

| You changed… | Bump |
|---|---|
| Small fix / tweak | `-alpha.2` → `-alpha.3` (or `2.0.0` → `2.0.1`) |
| New feature, backward-compatible | `2.0.0` → `2.1.0` |
| Big/breaking change | `2.0.0` → `3.0.0` |
| Moving from testing to stable | drop the `-alpha.N` → `2.0.0` |

Tag format must be `vX.Y.Z` or `vX.Y.Z-something` (the workflow listens for both).

---

## First-ever Play Store upload (one time only)

Google requires the **first** AAB of a brand-new app to be uploaded **by hand** in
the Play Console — the API can't create the app's first release. After that, every
tagged release uploads automatically.

1. Download the AAB from the release run's artifacts (or build locally).
2. Play Console → your app → Testing → Internal testing → Create release → upload
   the AAB → roll out.
3. From then on, just tag releases as above and the pipeline does the upload.

To enable automatic Play uploads, add the secrets in
`docs/RELEASE_SECRETS_SETUP.md` (one-time setup — then nothing in the code changes).

---

## If something goes wrong

| Problem | Fix |
|---|---|
| Release workflow didn't start | Tag must match `vX.Y.Z`. Check `git push origin <tag>` actually pushed it: `git ls-remote --tags origin`. |
| "versionCode already used" (Play) | You reused a `versionCode`. Bump it higher, re-tag with a new version. |
| Build is UNSIGNED | No `RELEASE_KEYSTORE_BASE64` secret yet — see `docs/RELEASE_SECRETS_SETUP.md`. Fine for GitHub pre-releases. |
| Need to re-release the same version | Delete the tag locally + remotely, fix, re-tag: `git tag -d vX.Y.Z; git push origin :refs/tags/vX.Y.Z`, then tag again. |
| Wrong release notes | Edit the GitHub Release directly in the web UI, or fix the notes file and re-tag. |

---

## TL;DR (the whole thing in 5 commands)

```powershell
# after your code is merged to main:
# 1. bump VERSION + versionName + versionCode (edit the files)
git add VERSION android/app/build.gradle.kts
git commit -m "chore(android): bump version to 2.0.0-alpha.2"
git push
git tag v2.0.0-alpha.2
git push origin v2.0.0-alpha.2     # ← release happens here
```

Related: `docs/DAILY_DEV_GUIDE.md` (daily dev loop), `docs/RELEASE_SECRETS_SETUP.md`
(enabling Play Store), `reference.md` (full project reference).
