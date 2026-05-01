# Paperkeep — Production Submission Checklist (P5.9)

Last updated: 2026-05-01
Status: Ready for submission once Play Console account is purchased

---

## Pre-submission checklist

### Play Console account
- [ ] Purchase Play Console developer account ($25 one-time)
- [ ] Complete developer profile (Nikhil Bhagat, individual developer)
- [ ] Accept Play Developer Distribution Agreement
- [ ] Set up payments profile (for AdMob revenue)
- [ ] Set up Google Payments merchant account (for IAP revenue)

### App signing
- [ ] Generate upload keystore: `keytool -genkey -v -keystore paperkeep-upload.jks -alias paperkeep-upload -keyalg RSA -keysize 4096 -validity 10000`
- [ ] Store keystore file in a secure location (NOT in git)
- [ ] Fill in `keystore.properties` from `keystore.properties.example`
- [ ] Bake signing cert SHA-256 into `ApkSignatureVerifier.EXPECTED_SHA256`
  - Run: `keytool -exportcert -keystore paperkeep-upload.jks -alias paperkeep-upload | sha256sum`
  - Replace the 64-char zeroes sentinel with the actual SHA-256 hex string
- [ ] Build signed AAB: `./gradlew :app:bundleRelease`
- [ ] Verify signing: `apksigner verify --verbose app-release.aab` (expect v2+v3)

### In-app purchase setup
- [ ] Create "paperkeep_pro_lifetime" product in Play Console → Monetize → Products
  - Type: One-time product
  - Name: "Paperkeep Pro"
  - Price: $4.99 USD (set per-country pricing as desired)
  - Status: Active
- [ ] Verify product ID matches `BillingManager.PRODUCT_ID = "paperkeep_pro_lifetime"`

### AdMob setup
- [ ] Create AdMob account linked to Play Console app
- [ ] Create Interstitial ad unit for export flow
- [ ] Replace test ad unit IDs with production IDs in `:core:ads` module
- [ ] Verify frequency cap: 1 per 3 minutes, after every 5th export
- [ ] Test UMP consent dialog on EU locale (VPN to Germany)

### Play Integrity setup
- [ ] Enable Play Integrity API in Google Cloud Console for app.paperkeep
- [ ] No server-side verification needed (client-only, feature gate only)

### App content
- [ ] Upload 8 narrative screenshots (see `screenshots/README.txt` for specs)
  - 1080x1920 or 2160x3840 PNG/JPG
  - No device frame (Play Console adds it)
- [ ] Upload 1024x500 feature graphic (see `feature-graphic-spec.txt`)
- [ ] Upload 512x512 hi-res app icon (launcher icon from `:app` resources)
- [ ] Confirm store listing copy from `listing.txt`

### Data Safety form
- [ ] Open Data Safety form in Play Console
- [ ] Answer per `data_safety.txt`:
  - Does your app collect or share user data? **No**
  - Device or other IDs (AdMob): **Yes** — Used for advertising, no sharing, optional to opt out
  - App activity (AdMob): **Yes** — Used for analytics/advertising, no sharing
  - All other categories: **No**
- [ ] Submit for review

### Release notes
- [ ] Copy from `release-notes-v2.0.0-alpha.1.txt`
- [ ] Update version to "2.0.0" for production release

---

## Rollout plan

### Step 1: Internal testing
- Upload signed AAB
- Add internal testers (yourself + 2-3 trusted people)
- Verify: app installs, Pro IAP flow visible, ads show, biometric lock works

### Step 2: Closed testing (alpha)
- Recruit ≥ 12 testers (see `tester-recruitment-email.md`)
- 14-day window minimum
- Monitor crash rate in Play Console (target < 0.5%)
- Fix any critical issues before proceeding

### Step 3: Open testing (beta)
- Expand to public opt-in
- Run for at least 7 days
- Monitor: crash rate, ANR rate, ratings

### Step 4: Production rollout
- Staged rollout: 10% → 50% → 100%
- Wait 3 days between each increase
- Monitor Play Console dashboard daily:
  - Crash-free users > 99.5%
  - ANR rate < 0.47% (Play Store threshold)
  - Rating trend

### Tier-1 launch countries
Start with: US, UK, CA, AU, DE, FR
Add remaining countries after 2 weeks of stability

---

## Post-launch monitoring (P5.10)

### Daily
- [ ] Check Play Console dashboard (crashes, ANRs, ratings)
- [ ] Check AdMob dashboard (eCPM, fill rate, estimated revenue)
- [ ] Respond to any 1-star reviews

### Weekly
- [ ] Review all new ratings and reviews
- [ ] Check retention metrics (1-day, 7-day, 30-day)
- [ ] Review AdMob mediation performance
- [ ] Check install conversion rate from listing views

### Targets
| Metric | Target |
|--------|--------|
| Crash-free users | > 99.5% |
| Crash rate | < 0.5% |
| Avg rating (first 20 reviews) | ≥ 4.3 |
| Week-1 organic installs | ≥ 50 |
| Day-1 retention | ≥ 40% |

---

## Play Console navigation reference

| Task | Path in Play Console |
|------|---------------------|
| Upload AAB | Release → Production → Create new release |
| Create IAP product | Monetize → Products → In-app products |
| Data Safety form | Policy → App content → Data safety |
| Add testers | Release → Testing → Closed testing |
| View crashes | Android vitals → Crashes & ANRs |
| AdMob dashboard | apps.admob.com |
| View ratings | Rating & reviews → Reviews |

---

## Notes on Play Console account timing

You mentioned purchasing the account in 2-5 days (as of 2026-05-01).
Once purchased:
1. Complete developer profile first (can take 1-2 days for verification)
2. Create app listing (package `app.paperkeep`)
3. Set up IAP product BEFORE uploading the AAB (product must exist for billing to work)
4. Upload AAB to internal testing track first
5. Then follow the rollout plan above

All code-side work is complete. The app is ready to submit.
