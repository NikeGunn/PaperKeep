# Release Secrets — Setup Sheet

> **Goal:** when your Play Console account is ready, you flip on full publishing
> by **pasting real values into GitHub Secrets** — no code or workflow edits.
> The release pipeline auto-detects each secret and switches behavior on its own.

---

## How the auto-detection works

`android-release.yml` checks, at run time, whether each secret is non-empty:

| If this secret is… | …the pipeline does |
|---|---|
| `RELEASE_KEYSTORE_BASE64` **missing** | builds an **unsigned** release (GitHub pre-release only) |
| `RELEASE_KEYSTORE_BASE64` **set** | builds a **signed** release |
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` **missing** | Play upload skipped → prints **"coming soon"**, job stays green |
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` **set** | uploads the AAB to Play Store automatically |

So today (no secrets) → green pipeline + GitHub Release.
Later (secrets added) → green pipeline + signed GitHub Release + Play upload.
**You change nothing but the secret values.**

---

## The 5 secrets (add when ready)

Repo → **Settings → Secrets and variables → Actions → New repository secret**.

| Secret name | Value | Where it comes from |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | base64 of your upload keystore | `base64 -w0 paperkeep-upload.jks` (one line) |
| `KEYSTORE_PASSWORD` | keystore store password | `secrets/release-keystore.txt` (local, gitignored) |
| `KEY_ALIAS` | upload key alias | chosen when you created the keystore |
| `KEY_PASSWORD` | key password | `secrets/release-keystore.txt` |
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` | full JSON contents | Play Console → Setup → API access → create service account → download JSON |

> ⚠️ Paste the **contents**, not the file path. For the keystore, paste the
> single-line base64 string. Never commit any of these.

---

## Fast path — set them all with the GitHub CLI

From the repo root (`gh auth login` first). Replace the placeholder paths/values:

```powershell
# 1. Keystore (base64, one line) — generates the value and stores it directly
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("android\paperkeep-upload.jks"))
$b64 | gh secret set RELEASE_KEYSTORE_BASE64

# 2. Keystore passwords / alias
"YOUR_STORE_PASSWORD" | gh secret set KEYSTORE_PASSWORD
"YOUR_KEY_ALIAS"      | gh secret set KEY_ALIAS
"YOUR_KEY_PASSWORD"   | gh secret set KEY_PASSWORD

# 3. Play service-account JSON (point at the downloaded file)
gh secret set PLAY_STORE_SERVICE_ACCOUNT_JSON < "C:\path\to\play-service-account.json"
```

Bash equivalent:
```bash
base64 -w0 android/paperkeep-upload.jks | gh secret set RELEASE_KEYSTORE_BASE64
echo -n 'YOUR_STORE_PASSWORD' | gh secret set KEYSTORE_PASSWORD
echo -n 'YOUR_KEY_ALIAS'      | gh secret set KEY_ALIAS
echo -n 'YOUR_KEY_PASSWORD'   | gh secret set KEY_PASSWORD
gh secret set PLAY_STORE_SERVICE_ACCOUNT_JSON < /path/to/play-service-account.json
```

Verify they're registered (values are never shown):
```powershell
gh secret list
```

---

## Updating a value later

Secrets are overwrite-in-place — re-run the same `gh secret set …` (or edit in
the web UI). The next release run picks up the new value automatically. Common
cases: rotating the service-account key, or fixing a mistyped password.

---

## First-ever Play upload (one-time manual step)

Google requires the **first** AAB of a new app to be uploaded by hand in the Play
Console (the API can't create the app's first release). After that, every tagged
release uploads automatically via Fastlane `supply`.

1. Build locally or download the AAB artifact from a release run.
2. Play Console → your app → Testing → Internal testing → Create release →
   upload the AAB → roll out.
3. From then on: tag `vX.Y.Z` and push → pipeline uploads for you.

Full Console checklist: `reference.md` §"Play Console checklist (detailed)" and
the files under `android/store/`.

---

## Quick reference: what to do on day 1 vs. day N

- **Today (no Console account):** just tag a release. You get a GitHub Release with
  the APK. Play step says "coming soon". ✅ green.
- **When account is ready:** add the 5 secrets above (one time), do the one-time
  manual first upload, then tag releases as normal — both GitHub and Play update
  on every push. ✅ green.
