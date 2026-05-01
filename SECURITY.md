# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 2.x     | Yes       |
| 1.x     | No (deprecated, no backend) |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Please report security issues by emailing:

**knewboy.nykhil@gmail.com**

Use the subject line: `[Paperkeep Security] <brief description>`

### What to include

- Description of the vulnerability and potential impact
- Steps to reproduce (device model, Android version, app version)
- Any proof-of-concept code or screenshots (mark as confidential)

### Response timeline

| Stage | SLA |
|-------|-----|
| Acknowledgement | within 72 hours |
| Initial triage | within 7 days |
| Fix release | within 90 days of confirmed vuln |
| Public disclosure | after fix ships + 14-day grace period |

We follow a **90-day responsible disclosure window**. If we cannot ship a fix in 90 days we will communicate status proactively and agree on an extension with the reporter.

## Security Architecture

Paperkeep is an **Android-only, backend-free** document scanner. The security model is:

### What we protect
- Document images and OCR text (encrypted at rest with AES-256-GCM + Android Keystore)
- All on-device data is stored in `filesDir/` with AES-256-GCM encryption
- No plaintext document data is ever written to shared storage

### What we do NOT do
- No backend server — there is nothing to breach server-side
- No telemetry, analytics, or crash reporting that leaves the device
- No user accounts, no passwords stored, no PII collected
- Data Safety form: "no data collected" (Play Store verified)

### Key management
- Encryption keys live in Android Keystore only (hardware-backed StrongBox when available)
- Keys are never written to DataStore, files, or logs
- Backup files use separate Argon2id-derived keys (not bound to Keystore)

### Known limitations (by design)
- The app does not use certificate pinning for AdMob/Play Billing (Google manages those certs)
- Root/Frida/emulator detection is best-effort — it disables Pro IAP and backup on compromised devices but does not block the app
- APK signature verification uses a baked-in SHA-256 — if the sentinel `000...0` is present, verification is skipped (development mode)

## Bug Bounty

We do not currently operate a paid bug bounty program. Responsible disclosures that result in a shipped fix will be credited in the app's release notes (with the reporter's consent).
