# Paperkeep Privacy Policy

**Last updated:** April 23, 2026
**Effective date:** April 23, 2026

---

## 1. The short version

Paperkeep is an Android document scanner. Everything you scan stays on your phone. We do not run a server. We do not have an account system. We do not collect any personal data about you.

The only data that leaves your device is what you explicitly send through a share sheet, export, or backup that you initiate yourself — and what Google AdMob sees when it serves ads (if you haven't purchased the ad-free "Pro" upgrade).

That's the whole policy. The rest of this document is the detailed version.

---

## 2. Introduction

Paperkeep ("we", "our", "us", or "the App") is a mobile application developed by Nikhil Bhagat ("Developer"). This Privacy Policy explains what information Paperkeep does and does not collect, and how your information is handled.

By installing or using Paperkeep, you agree to the practices described below.

---

## 3. Information we collect

### 3.1 Information you provide

**None.** Paperkeep has no account system, no registration, no login. We do not ask for your name, email, phone number, or any other personal identifier. There is no "sign up" button anywhere in the App.

### 3.2 Information stored on your device (never sent to us)

When you use Paperkeep, the following are created and stored **only on your device**:

- Scanned document images and thumbnails (encrypted with AES-256-GCM, keys in the Android Keystore)
- OCR text extracted from your documents (encrypted)
- Document titles, folders, and organization metadata
- App preferences and settings
- Optional local encrypted backup files you create yourself

We have no access to any of this. We have no server to store it on.

### 3.3 Information collected automatically by Paperkeep

**None.** Paperkeep contains no analytics SDK, no crash-reporting SDK, no behavior-tracking code. We do not know how often you open the App, what features you use, or whether you use it at all.

If the App crashes, a local encrypted crash log is written to your device's private storage. This log stays on your device. It is not uploaded. If you choose to attach it to an email to support us, that is your explicit action.

### 3.4 Information collected by third parties inside the App

Two Google SDKs run inside Paperkeep and may collect information independently of us:

- **Google AdMob** (for interstitial advertising in the free tier). See §6.
- **Google Play Billing** (if you purchase the Pro upgrade). See §7.

We do not receive any of the information these SDKs collect about you.

### 3.5 Camera, storage, and notification access

- **Camera:** Paperkeep asks for camera permission solely to capture documents. Captured images are processed on your device and stored encrypted in the App's private storage. We never access your photo gallery unless you use Android's system photo picker to explicitly import an image.
- **Storage:** Paperkeep uses only its own private app storage (`filesDir`) and the Android Storage Access Framework (SAF) when you pick a location for a backup or export. We do not request `READ_EXTERNAL_STORAGE` or `READ_MEDIA_IMAGES` permissions.
- **Notifications (Android 13+):** Used only for local backup reminders if you enable them. No push notifications are received from any server.

---

## 4. What we do not collect

To be explicit — Paperkeep does **not** collect, transmit, or store on any server:

- Your name, email address, phone number, or any identity information
- Your scanned documents, images, or OCR text
- Your search queries
- Your device contacts, calendar, location, or browsing history
- Any analytics or behavioral data
- Any biometric data (biometric app lock is handled entirely by Android; we never see the biometric material itself)

We do not sell your data because we have no data to sell.

---

## 5. On-device AI

All document intelligence features — edge detection, document classification, OCR, image enhancement, and the optional summarizer — run entirely on your device using Google ML Kit and TensorFlow Lite models. No image, OCR text, or document metadata is sent to any server for AI processing. There is no "cloud AI" option in Paperkeep.

---

## 6. Advertising (Google AdMob)

In the free tier, Paperkeep shows occasional interstitial ads via Google AdMob. When AdMob is loaded, Google may collect:

- Your device's advertising ID (resettable in Android Settings)
- General device information (model, Android version, language, coarse region)
- Ad interaction data (impressions, clicks)
- IP address (as part of any network request)

We do not receive this data. Google processes it according to its own privacy policy: https://policies.google.com/privacy

**Consent (EU / UK / EEA users):** We display a Google UMP consent dialog the first time the App loads ads. You can change your choice at any time via **Settings → Privacy → Ad preferences**.

**Opt out of personalized ads:** Android **Settings → Google → Ads → Opt out of Ads Personalization** (or the equivalent on your device manufacturer's version of Android).

**Pro upgrade:** If you purchase the one-time "Paperkeep Pro" upgrade, AdMob is not initialized and no ad-related data is collected.

---

## 7. Purchases (Google Play Billing)

If you purchase the one-time Paperkeep Pro upgrade, the transaction is handled entirely by Google Play Billing. Google collects the payment information; we only receive an anonymous purchase token from the Play Store that tells us "this installation is Pro." We do not see your name, payment method, billing address, or purchase history.

Google's handling of your purchase is governed by: https://policies.google.com/privacy

---

## 8. Backups and exports you create

Paperkeep supports local encrypted backups (AES-256 ZIP, password-protected) and export formats (PDF, JPEG, PNG, plain-text OCR, encrypted ZIP). When you create a backup or export:

- You choose the destination yourself via Android's Storage Access Framework (Google Drive, Dropbox, OneDrive, internal storage, SD card, USB-OTG, or anywhere else)
- Paperkeep writes the file to the location you chose
- We have no visibility into where the file went

The security of anything you place in a third-party cloud service (Drive, Dropbox, etc.) is governed by that service's own policy. Your backup password is never transmitted to us or to anyone else.

---

## 9. Data security on your device

Paperkeep implements:

- **AES-256-GCM** encryption for every page image, thumbnail, OCR blob, saved signature, and crash log on disk
- **Android Keystore** (StrongBox-backed where available) for master keys — keys cannot be exported from the device
- **HMAC-SHA-256** keyed-hash tokens for the encrypted OCR search index
- **Argon2id (m=128 MiB, t=4)** key derivation for user-set backup passwords
- **Optional biometric lock** gating access to the App (`BIOMETRIC_STRONG | DEVICE_CREDENTIAL`)
- **FLAG_SECURE** on document-viewing screens to block screenshots and recent-apps thumbnails
- **APK signature pinning, root/tamper detection, Play Integrity attestation** for Pro features

No system is perfect. If you lose your device and do not have a backup, your scans are gone — we cannot recover them because we never had them. This is the cost of a true no-server product, and we want you to understand it before you rely on the App.

---

## 10. Your rights

You have complete control over all Paperkeep data because it never leaves your device:

- **Access / export:** Use the in-app export features (PDF, JPEG, encrypted ZIP) at any time.
- **Deletion:** Use "Delete" on any document, folder, or the App's **Settings → Storage → Erase all data** option. Uninstalling the App also deletes everything it stored. Once deleted, the data is unrecoverable.
- **Ad preferences:** Change AdMob consent at any time in **Settings → Privacy → Ad preferences**.

For EU / UK / EEA / California residents, we want to be clear: rights under GDPR, UK GDPR, and CCPA/CPRA concerning personal data stored on our servers are, by definition, not applicable to Paperkeep — we do not store your personal data on any server. Your device is the only storage location, and you control it.

If you have questions, email us at **security@paperkeep.app** (or the contact address in §13 below).

---

## 11. Children's privacy

Paperkeep is not directed at children under 13 (or 16 in the EEA). We do not knowingly collect personal data from children. Because Paperkeep does not collect personal data from anyone, this is a restriction on store-listing targeting rather than a data-handling obligation.

---

## 12. Third-party services

| Service | Purpose | Privacy Policy |
|---|---|---|
| Google AdMob + UMP | Advertising + consent (free tier only) | https://policies.google.com/privacy |
| Google Play Services | App delivery, in-app review, ML Kit models | https://policies.google.com/privacy |
| Google Play Billing | One-time Pro purchase | https://policies.google.com/privacy |
| Google Play Integrity | Anti-tamper attestation for Pro features | https://policies.google.com/privacy |

Paperkeep does **not** integrate Firebase Analytics, Crashlytics, Sentry, Adjust, AppsFlyer, Facebook SDK, or any other telemetry or attribution SDK.

---

## 13. Changes to this policy

If we change this policy, we will update the "Last updated" date at the top and the new version will ship with the next App update. Because there is no account and no server, we cannot notify you individually by email. The policy is visible in-app at **Settings → About → Privacy Policy** and at the URL published in the Play Store listing.

Material changes (anything that would expand the data we collect) will additionally be surfaced as an in-app banner on first launch of the new version.

---

## 14. Contact

**Developer:** Nikhil Bhagat
**Email:** knewboy.nykhil@gmail.com
**Security reports:** security@paperkeep.app (forwarded to the email above)
**App:** Paperkeep (`app.paperkeep`)
**Play Store:** https://play.google.com/store/apps/details?id=app.paperkeep
