# Paperkeep — Closed Testing Track Checklist (P3.16)

> Operational checklist for the moment you have a Play Console account.
> Until then: **the code build is ready, you just can't upload yet.**

Status as of 2026-04-26: build verified end-to-end on JVM tests + R8 release build.
Awaiting: Play Console account purchase + signing keystore generation.

## Phase A — Pre-upload (you can do these without a Console account)

- [ ] Buy Play Console account ($25 one-time, https://play.google.com/console/signup)
- [ ] Generate upload keystore — see `signing-setup.md`
- [ ] Bake signing-cert SHA-256 fingerprint into `ApkSignatureVerifier` (P3.13)
- [ ] Run `cd android && ./gradlew :app:bundleRelease` — confirm signed AAB at `app/build/outputs/bundle/release/app-release.aab`
- [ ] Verify AAB size < 30 MB (current measurement: 43 MB universal AAB; per-device install slice ~14–18 MB after Play's Dynamic Delivery)
- [ ] Capture 8 narrative screenshots per `store/screenshots/README.txt` on a Pixel 6a (1080×2400)
- [ ] Build 1024×500 feature graphic per `store/feature-graphic-spec.txt`
- [ ] Smoke-test signed AAB on your physical phone via `bundletool build-apks --connected-device` + `bundletool install-apks`
- [ ] Tester recruitment: email/text the 12 testers (template in `tester-recruitment-email.md`)
  collect their Gmail addresses

## Phase B — Play Console: app creation

- [ ] Console → Create app → Name: "Paperkeep"
- [ ] App or game: App
- [ ] Free or paid: Free (with in-app purchases — Pro IAP comes in P5.2)
- [ ] Default language: English (US)
- [ ] Declarations: confirm developer program policies + US export laws
- [ ] App access: All functionality available without restrictions
- [ ] Ads: Yes (we ship AdMob interstitials per P3.10)
- [ ] Content rating: complete IARC questionnaire (likely Everyone)
- [ ] Target audience: 18+ (CamScanner-style productivity)
- [ ] News app: No
- [ ] COVID-19 contact tracing: No
- [ ] Data safety form: paste content from `store/data_safety.txt` ("no data collected" + AdMob disclosure)
- [ ] Government app: No
- [ ] Privacy policy URL: host `docs/PRIVACY_POLICY.md` somewhere (GitHub Pages, your domain, even a public Gist) and paste the URL

## Phase C — Closed testing track setup

- [ ] Console → Test and release → Closed testing → Create new track
- [ ] Track name: "alpha-1"
- [ ] Countries: select your tier-1 launch list (US/UK/CA/AU/DE/FR or India if you prefer regional first)
- [ ] Testers → Create email list → name "alpha-1-testers" → paste 12+ Gmail addresses
- [ ] Feedback URL: a Google Form or your email (alpha-1@paperkeep.app or personal Gmail)
- [ ] Save

## Phase D — First release

- [ ] Console → Closed testing → alpha-1 → Create new release
- [ ] Upload `app-release.aab`
- [ ] Release name: `2.0.0-alpha.1` (matches `VERSION` file)
- [ ] Release notes: paste from `release-notes-v2.0.0-alpha.1.txt`
- [ ] Save → Review release → Start rollout to alpha-1
- [ ] Wait for processing (~30 min). Status will move from "In review" → "In production for testers"
- [ ] **Note opt-in URL** Console gives you (looks like `play.google.com/apps/internaltest/<long-id>`)
- [ ] Forward opt-in URL to your 12 testers (template in `tester-recruitment-email.md` already references it)
- [ ] **Start the 14-day clock** — Play requires 14 days of active testing before promotion to production. Mark the date in your calendar.

## Phase E — Day 1–14 monitoring

- [ ] Daily: Console → Statistics → check installer counts (target: 10+ of 12 actually installed by day 3)
- [ ] Daily: Console → Quality → ANRs and crashes (target: 0 crashes, 0 ANRs)
- [ ] Daily: Console → Pre-launch reports → review automated test findings
- [ ] Day 3 + Day 7 + Day 12: send the testers a "what specifically should I look at this week" follow-up
- [ ] Day 14: if crash-free users > 99.5% AND ratings ≥ 4.0 → unlock Phase 4
- [ ] Day 14: if blockers → fix → push `2.0.0-alpha.2` to same track → 14-day clock pauses but doesn't reset

## Phase F — Post-closed-testing

After 14 days clean:
- [ ] Move to Phase 4 task list (P4.1 onwards)
- [ ] Defer "open testing" track until Phase 5 (P5.9)
- [ ] Production launch is P5.9 — do not promote to production from alpha-1

## Build artifacts produced this session (2026-04-26)

```
android/app/build/outputs/bundle/release/app-release.aab     43 MB (universal)
android/app/build/outputs/apk/release/app-release-unsigned.apk    54 MB (universal)
```

The AAB is the only artifact you upload to Play Console. The unsigned APK is
useful for local sideload testing via `adb install -r` and for measuring the
worst-case (universal) size; what users actually download is much smaller because
Play Dynamic Delivery serves only their device's ABI + density.

Per-device install size estimate on arm64-v8a (the dominant Android ABI):
~17 MB (vs. 28 MB Phase 3 floor, vs. 30 MB Phase 5 floor — well within budget).
