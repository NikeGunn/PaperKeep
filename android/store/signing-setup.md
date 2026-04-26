# Paperkeep Release Signing Setup

One-time setup to produce a signed release AAB for upload to Play Console.

> **Why this file exists:** the Gradle build now reads signing credentials from
> a gitignored `keystore.properties` at the repo root. Without it, `:app:assembleRelease`
> still works (produces `app-release-unsigned.apk`), but the AAB Play Console accepts
> must be signed by *your* upload key.

## 1. Generate the upload keystore (do this ONCE, ever)

> **Critical:** the upload keystore must never be lost, leaked, or regenerated.
> If you lose it, Play Console requires a key reset which takes weeks. Back it up
> to at least two offline locations (encrypted USB, password manager attachment).

From the repo root:

```bash
keytool -genkey -v \
  -keystore paperkeep-upload.jks \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -alias paperkeep-upload
```

It will prompt for:
- Keystore password (pick a strong one — store in your password manager)
- Key password (use the same as keystore password — simpler)
- Common name / org / etc. — these are baked into the cert; "Nikhil Bhagat" / "Paperkeep" is fine

Output: `paperkeep-upload.jks` at the repo root. **This file is gitignored** (`.gitignore` line: `*.jks`).

## 2. Configure Gradle to use it

Copy the example template:

```bash
cp keystore.properties.example keystore.properties
```

Edit `keystore.properties`:

```properties
storeFile=paperkeep-upload.jks
storePassword=<your keystore password>
keyAlias=paperkeep-upload
keyPassword=<your key password>
```

`keystore.properties` is gitignored too.

## 3. Verify the wiring

```bash
cd android
./gradlew :app:bundleRelease
```

Should print:

```
BUILD SUCCESSFUL
```

Output AAB at:

```
android/app/build/outputs/bundle/release/app-release.aab
```

Verify it's actually signed:

```bash
keytool -printcert -jarfile android/app/build/outputs/bundle/release/app-release.aab
```

Should show your CN/O fields and a SHA-256 fingerprint. **Save that SHA-256 fingerprint** — you'll need it for:
- `:app:src/main/.../security/IntegrityGate.kt` (P3.13 baked APK signature pin — replace the all-zeroes sentinel)
- Play Console → Setup → App integrity (upload key fingerprint)

## 4. Bake the signing-cert SHA-256 into the IntegrityGate (P3.13)

Once you have the keystore, find the existing sentinel:

```bash
grep -rn "0000000000" android/core/security/src/main 2>/dev/null
```

Replace the all-zeroes constant in `ApkSignatureVerifier` with your hex
fingerprint (lower-case, no colons). After this, mismatched APKs silently
disable AdMob + Pro IAP + backup creation per `docs/PAPERKEEP_DESIGN.md` §6.6.

## 5. Backup checklist before you ship

- [ ] `paperkeep-upload.jks` copied to encrypted USB #1
- [ ] `paperkeep-upload.jks` copied to encrypted USB #2 (different physical location)
- [ ] Keystore + key passwords stored in password manager (1Password / Bitwarden)
- [ ] SHA-256 fingerprint recorded in your project notes
- [ ] Test verified: deleting `keystore.properties` and rebuilding produces
      `app-release-unsigned.apk` (not `.aab`) — confirms graceful fallback works
