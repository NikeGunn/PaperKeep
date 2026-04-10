# ScanVault — Design System & Competitive Feature Strategy

**Purpose:** How the app looks, feels, moves, and *differentiates from CamScanner*. This is the document that turns "another document scanner" into "the document scanner people tell their friends about."

Two parts:
1. **Competitive Feature Strategy** — what we build and why, feature by feature, specifically to beat CamScanner and every other scanner on Google Play
2. **Visual Design System** — typography, color, motion, iconography, layout rules

---

# Part 1: Competitive Feature Strategy

## 1.1 Who we are beating

**Primary competitor:** CamScanner. Dominant, installed on hundreds of millions of devices, battle-tested, feature-rich, but widely hated in reviews. Our job is not to replicate every feature — it's to fix the things people complain about and ship the features people wish existed.

**Secondary competitors:**
- Microsoft Lens — good quality, Microsoft account required, limited filters
- Adobe Scan — good quality, forced Adobe account, subscription paywall
- Google Drive Scan — basic, tied to Drive, no library
- TapScanner — copycat with aggressive ads
- Genius Scan — paid upfront, small but loyal audience

## 1.2 CamScanner's actual weaknesses (from review mining)

Systematically reading CamScanner's recent 1-star reviews on Google Play yields these repeated complaints. Each one is a feature we build to kill:

| CamScanner complaint                                 | Our counter-feature                                                 |
|------------------------------------------------------|---------------------------------------------------------------------|
| Forced account creation just to scan                 | Zero account ever. Core works offline forever without login.       |
| Watermark on free exports                            | No watermark, ever, on any tier.                                   |
| Aggressive fullscreen ads on every action            | Interstitial only after every 5th export, never mid-scan.         |
| "Premium" locks basic features                       | Everything essential is free. Rewarded ads unlock convenience.     |
| 180+ MB APK eats phone storage                       | Under 25 MB target.                                                 |
| Slow edge detection, laggy preview                   | Real-time 60fps edge detection (under 16ms/frame).                 |
| Privacy scandal (2019 SDK malware incident)          | Zero-knowledge architecture, no analytics SDKs, open audit.       |
| Unnecessary permissions (contacts, phone state)      | Camera only. Nothing else.                                          |
| Uploads data to unclear servers                      | Everything offline by default. Cloud sync opt-in + E2E encrypted.  |
| OCR behind paywall                                   | OCR completely free, on-device, works offline, 100+ languages.     |
| Bloated home screen with "discover" feed             | Zero feed. Home = your scans. Full stop.                           |
| "PDF merge requires premium"                         | All exports free. PDF, searchable PDF, JPEG, PNG, TXT, ZIP.       |
| Gets slower the more documents you have              | Room + indexed FTS4, library stays fast at 10k+ documents.         |
| Shares data with third parties                       | We literally cannot share what we cannot decrypt.                   |
| Frequent unwanted notifications                      | Zero marketing notifications. Only sync status (if user enabled). |
| Confusing pricing tiers                              | No pricing tiers. Free with ads. One-time "remove ads" later (v2).|

**This table is the product spec.** If you're ever unsure whether a feature belongs in the app, ask: "does this solve a row in this table?" If yes, build it. If no, defer.

## 1.3 Feature pillars (the 6 things that make us better)

### Pillar 1: SPEED
The app must feel instant. Every tap. Every capture. Every swipe.

- Cold start under 500ms
- Camera ready under 300ms after tapping the "scan" button
- Edge detection preview at locked 60fps
- Library scroll at 60fps with thousands of documents
- PDF export of a 20-page document in under 3 seconds
- Search across 1000 documents in under 200ms
- APK under 25 MB so download is fast on 3G

**Why:** people scan documents in bursts — 5 receipts from a restaurant, 10 pages of a contract at the notary. Speed is felt in the aggregate. A one-second lag per capture becomes a minute of frustration across a batch.

### Pillar 2: PRIVACY
Not as a marketing slogan — as the engineering default.

- No account required, forever, for every core feature
- All scans encrypted at rest with hardware-backed keys
- OCR runs on-device. Text never leaves the phone unless the user exports it.
- Zero analytics SDKs in the release build (AdMob is the only third-party network SDK)
- Optional cloud sync is zero-knowledge: server has no ability to decrypt anything
- Biometric app lock with configurable timeout
- `FLAG_SECURE` to block screenshots of sensitive documents
- Destructive redaction (the pixels are actually gone, not covered with a layer)
- No permissions requested beyond what is strictly necessary
- Open source the security-sensitive parts (v2) so anyone can audit

**Why:** the CamScanner scandal proved that users care, even if they don't talk about it. "My bank statements are on this phone" is a visceral feeling. Privacy isn't a feature — it's a promise that either holds or doesn't.

### Pillar 3: QUALITY
The scans have to look better than CamScanner's, not just equal.

- Full-resolution capture (not downsampled)
- Real-time edge detection on the preview AND a refinement pass on the full-res capture
- Custom TFLite corner refinement model (~2 MB) for pixel-accurate corners
- Adaptive B&W filter using OpenCV's adaptive threshold with Gaussian C — sharper than CamScanner's "Magic Color" B&W
- CLAHE-based lighting correction for uneven illumination
- Book dewarp using a TFLite `DewarpNet` port (removes book-spine curvature, which CamScanner doesn't do)
- Whiteboard glare removal using inpainting on over-exposed regions
- Auto-detection of document type → applies the right filter automatically
- Non-destructive filters (original is always kept)

**Why:** a document scanner that produces worse scans than CamScanner has no reason to exist. If we can visibly produce better scans, we've won.

### Pillar 4: DELIGHT
The things nobody asks for but everyone loves when they see them.

- Haptic feedback on capture (subtle, not buzzy)
- Shutter sound that's physically satisfying (not a cheesy digital click)
- Animated edge detection corners with spring physics
- "Shake to undo" on the crop screen
- Long-press the capture button → burst mode (3 captures rapid-fire)
- Magnifier lens when dragging crop corners
- Thumbnail-based search results (shows the page, not just text)
- Monochromatic mode for the reader (like a Kindle) — optimized for OLED, easier on the eyes at night
- Physical volume keys as capture shortcuts
- Pinch-to-zoom on library cards to preview without opening
- Adaptive Material You colors on Android 12+

**Why:** these are the moments people screenshot and send to friends. Free marketing.

### Pillar 5: REACH
The app must work everywhere — not just on flagship phones in wealthy countries.

- Min API 26 (Android 8, 97%+ of devices)
- Works offline, fully, forever
- Handles low-end hardware gracefully (Snapdragon 4xx class, 2 GB RAM)
- Localized to English, Hindi, Nepali, Spanish, Portuguese, Arabic, French, German, Indonesian on day one
- RTL layout support
- Text scales to 200% without breaking layouts
- Accessible: full TalkBack support, minimum 48dp touch targets
- Degrades gracefully: if OpenCV fails to load, falls back to ML Kit's built-in scanner API

**Why:** the biggest growth market for Android apps is not the US. A scanner that runs smoothly on a $120 phone in Jakarta is a scanner that gets 10x the installs. More installs + smart geo-targeting of ads = more revenue.

### Pillar 6: EXPORT FREEDOM
Get scans OUT of the app easily, to anywhere.

- PDF (regular + searchable with invisible text layer)
- PDF with signatures, highlights, annotations
- JPEG (single or batch ZIP)
- PNG (lossless)
- Plain text (just the OCR)
- Encrypted ZIP with user password
- Direct share to any app via the system share sheet
- "Send to printer" via Android's built-in print service
- Save to Files (any storage provider)
- Generate a QR code that opens the document on another device (via pre-signed URL if synced)

**Why:** CamScanner locks the most useful exports behind premium. We don't.

## 1.4 Differentiator features (the "why choose us" list)

Every app store listing needs a 30-second pitch. Here's ours:

> **ScanVault — the private document scanner that actually respects you.**
>
> - No watermarks. No forced login. No ads inside the camera.
> - Everything scans and processes on your phone. Nothing leaves unless you choose.
> - Real-time edge detection that actually works, even in low light.
> - Searchable PDF exports with OCR — free, forever.
> - Under 25 MB. Runs on any phone made in the last 7 years.
> - ID card mode, receipt mode, whiteboard mode, book scan mode.
> - Optional encrypted cloud sync where even we can't read your documents.

## 1.5 Features we do NOT build (even though CamScanner has them)

Saying no is how we ship fast. These are explicitly out of scope for MVP and v1:

- **In-app social feed / "discover" / "community"** — CamScanner has this. It's garbage. We don't.
- **Cloud OCR** — slower, privacy-invasive, server cost. On-device is strictly better.
- **E-signatures with legal/notarization integrations** — regulatory nightmare.
- **Fax integration** — irrelevant to target audience.
- **Team / shared workspaces** — complexity explosion for a solo dev.
- **Subscription tiers** — MVP is free + ads. Subscription is a v2 question once we have traction.
- **LLM "ask your document"** — cool but expensive to run and unclear privacy model. v2 candidate if on-device models improve.
- **Translation built-in** — use the system share sheet to send text to Google Translate. Don't reinvent.
- **Handwriting recognition beyond ML Kit** — ML Kit already does basic handwriting for free.

## 1.6 Home screen layout (a direct response to CamScanner's clutter)

```
┌─────────────────────────────────────────┐
│  [≡]   ScanVault              [🔍]  [👤] │  ← 56dp, flat, no shadow
├─────────────────────────────────────────┤
│                                         │
│  All documents                    [⌄]   │  ← Folder picker, minimal
│                                         │
│  ┌─────────┐  ┌─────────┐              │
│  │         │  │         │              │  ← Grid of thumbnails
│  │         │  │         │              │     2 columns phone
│  │  thumb  │  │  thumb  │              │     4 columns tablet
│  │         │  │         │              │
│  │         │  │         │              │
│  └─────────┘  └─────────┘              │
│   Receipt      Contract                 │
│   2 pages      8 pages                  │
│   2h ago       yesterday                │
│                                         │
│  ┌─────────┐  ┌─────────┐              │
│  │         │  │         │              │
│  │  thumb  │  │  thumb  │              │
│  │         │  │         │              │
│  └─────────┘  └─────────┘              │
│                                         │
│                                         │
│                          ┌─────────┐    │
│                          │         │    │  ← FAB, 64dp
│                          │   📷    │    │     Tap: open camera
│                          │         │    │     Long-press: quick actions
│                          └─────────┘    │
└─────────────────────────────────────────┘
```

**What's NOT on this screen:**
- No banner ads
- No "premium upgrade" button
- No "news" or "tips" feed
- No tutorial overlays (shown once during onboarding and gone)
- No social icons
- No cloud storage pitches

**What IS on this screen:**
- Your scans, organized, findable, nothing else.

---

# Part 2: Visual Design System

## 2.1 Design philosophy: "calm technical"

The aesthetic is deliberately understated. This is a privacy-first utility — it should feel like a well-made tool, not a flashy consumer app. The reference points are:

- **1Password** — calm, trustworthy, technical
- **Things 3** — obsessive attention to typography and spacing
- **Notion** — generous whitespace, confident typography
- **Arc browser** — playful moments inside a refined frame

**What we deliberately avoid:**
- Purple gradients on white (the AI-app cliché)
- Over-rounded "consumer app" corners
- Animated emoji-heavy onboarding
- Exclamation marks in copy
- Generic stock illustrations
- Lottie animations for decoration (use them only for function, like loading states)

## 2.2 Color system

We use a monochromatic-plus-accent system. The bulk of the UI is neutral grayscale. A single strong accent color carries brand identity and active states.

### Brand color: "Scan Amber"

`#FFB020` — a warm amber that evokes a scanner lamp and stands out against both light and dark themes. Tested for contrast against black and white text (passes WCAG AA at 18pt+).

### Full palette (Material 3 compatible)

```kotlin
// Light theme
object LightColors {
    val primary          = Color(0xFFB07400)  // Darker amber for contrast
    val onPrimary        = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFFFDDA8)
    val onPrimaryContainer = Color(0xFF271900)

    val secondary        = Color(0xFF6F5B40)
    val onSecondary      = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFFADEBC)
    val onSecondaryContainer = Color(0xFF271806)

    val surface          = Color(0xFFFFF8F2)  // Warm off-white, not stark
    val onSurface        = Color(0xFF1F1B16)
    val surfaceVariant   = Color(0xFFF0E0CF)
    val onSurfaceVariant = Color(0xFF4F4539)

    val background       = Color(0xFFFFF8F2)
    val onBackground     = Color(0xFF1F1B16)

    val error            = Color(0xFFBA1A1A)
    val onError          = Color(0xFFFFFFFF)
    val errorContainer   = Color(0xFFFFDAD6)
    val onErrorContainer = Color(0xFF410002)

    val outline          = Color(0xFF817567)
    val outlineVariant   = Color(0xFFD3C4B4)
}

// Dark theme
object DarkColors {
    val primary          = Color(0xFFFFB95C)
    val onPrimary        = Color(0xFF422C00)
    val primaryContainer = Color(0xFF5E4200)
    val onPrimaryContainer = Color(0xFFFFDDA8)

    val secondary        = Color(0xFFDDC2A1)
    val onSecondary      = Color(0xFF3E2D16)
    val secondaryContainer = Color(0xFF55432B)
    val onSecondaryContainer = Color(0xFFFADEBC)

    val surface          = Color(0xFF17120C)  // Near-black warm
    val onSurface        = Color(0xFFEBE0D4)
    val surfaceVariant   = Color(0xFF4F4539)
    val onSurfaceVariant = Color(0xFFD3C4B4)

    val background       = Color(0xFF17120C)
    val onBackground     = Color(0xFFEBE0D4)

    val error            = Color(0xFFFFB4AB)
    val onError          = Color(0xFF690005)
    val errorContainer   = Color(0xFF93000A)
    val onErrorContainer = Color(0xFFFFDAD6)

    val outline          = Color(0xFF9C8E7E)
    val outlineVariant   = Color(0xFF4F4539)
}
```

### Dynamic color (Android 12+)

On Android 12+, we use Material You dynamic color as the primary theme, derived from the user's wallpaper. Our fixed Scan Amber becomes a reserved accent that only appears for:
- The primary CTA button (scan)
- Active edge detection corner indicators
- The app icon

This way, the app integrates with the user's phone aesthetic but still has a recognizable identity.

### Semantic colors (NOT tied to Material roles)

Some colors carry meaning regardless of theme:

```kotlin
val EdgeDetected    = Color(0xFF00E676)  // Bright green, always
val EdgePartial     = Color(0xFFFFB020)  // Our amber
val EdgeMissing     = Color.Transparent  // Invisible when nothing detected
val ShutterFlash    = Color.White         // Capture animation
val RedactionBlack  = Color(0xFF000000)  // Deliberate, recognizable
val ConfidenceHigh  = Color(0xFF00C853)  // OCR confidence indicator
val ConfidenceLow   = Color(0xFFFFA000)
```

## 2.3 Typography

Two font families:

- **Display:** Inter Tight — used for large titles, numbers, brand moments
- **Body:** Inter — used for everything else

These are chosen because:
- Both are open source (SIL Open Font License)
- Inter is already highly legible at small sizes and is the gold standard for UI
- Inter Tight's condensed feel gives visual energy to headlines without being trendy
- Ship the variable font files (`.ttf` with axes) — one file covers every weight

**File size:** include only the Latin + Extended Latin + Cyrillic subset as bundled. Devanagari (for Nepali/Hindi) downloads on demand via ML Kit language pack. Arabic same. Chinese/Japanese/Korean use the system font (too large to bundle).

### Type scale (Material 3 expressive)

```kotlin
object ScanVaultTypography {
    val displayLarge = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.Medium,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    )
    val displayMedium = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.Medium,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    )
    val headlineLarge = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    )
    val titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
    val titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )
    val bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    val bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )
    val labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
    val labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
}
```

### Typography rules

- Headlines use Inter Tight Medium, never Bold (Bold is too aggressive for our aesthetic)
- Body text uses Inter Regular at 16sp, never smaller on primary content
- Labels use Inter Medium at 14sp
- Numbers use `fontFeatureSettings = "tnum"` (tabular figures) so page counts and timestamps don't wobble
- Never use uppercase headings (ALL CAPS feels dated)
- Prefer sentence case over title case: "Export as PDF" not "Export As PDF"
- One exclamation mark in the entire app maximum (probably zero)

## 2.4 Iconography

All icons are from the **Tabler Icons** set (2000+ icons, MIT license, 24×24 stroke-based).

Rules:
- Always `24dp` in the main UI, `20dp` in dense areas
- Stroke weight `1.5` (not the default 2) — feels more refined
- Rounded joins
- Color uses `onSurfaceVariant` by default, `primary` for active/selected states
- Never combine multiple icon sets (no Material Icons mixed with Tabler)

### Custom icons (our own)

Three icons are custom, because they represent our core features and nothing in Tabler fits:

1. **Edge detector** — four corners with a dashed box, animated
2. **Vault** — a stylized padlock merged with a document corner, used for the encrypted library state
3. **Scan Amber logo mark** — an angled "S" that also reads as a scanning beam

These live in `/android/core/ui/src/main/res/drawable/` as vector drawables.

### App icon

Adaptive icon with:
- **Foreground:** the Scan Amber logo mark in white on transparent
- **Background:** solid `#1F1B16` (our dark surface) — monochromatic, recognizable
- **Monochrome layer** (API 33+): line-art version of the logo for themed icons

No gradient. No glow. No shadow. Confident and minimal — instantly distinguishable in a folder of consumer apps.

## 2.5 Motion & animation

Motion is deliberate. Every animation has a purpose. Material 3 expressive motion spec is our starting point, with custom tweaks.

### Duration tokens

```kotlin
object Motion {
    val instant   = 0.milliseconds          // State changes that shouldn't feel animated
    val fast      = 150.milliseconds        // Small element transitions
    val medium    = 300.milliseconds        // Screen transitions
    val slow      = 500.milliseconds        // Hero moments (capture animation)
    val extraSlow = 800.milliseconds        // Onboarding reveals
}
```

### Easing curves

```kotlin
val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
val DeceleratedEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
val SpringBouncy = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)
```

### Key animations

**Capture animation:**
1. Shutter button scales 0.9 → 1.0 (haptic fires at 0.9)
2. Full-screen white flash at 10% opacity for 50ms
3. Captured image scales from preview rect to "recent scans" thumbnail strip
4. Thumbnail strip shifts to make room with a `SpringBouncy` curve
5. Total duration: 400ms

**Edge detection corners:**
- When 4 corners found, they fade in from 0 to 100% opacity over 80ms
- Position changes animate with `spring(stiffness = 300f)` — feels responsive but not jittery
- When corners are lost, they fade out over 150ms (slower than fade-in, feels more forgiving)

**Library scroll:**
- Standard 60fps, no parallax tricks
- Cards do NOT animate in on scroll — it feels gimmicky and adds overhead
- Pull-to-refresh uses a custom indicator: a pulsing amber scan line (callback to our brand mark)

**Screen transitions:**
- Forward: shared-element transition for the tapped card → opening document
- Back: reverse of forward
- Modal sheets: slide up with `EmphasizedEasing` over 300ms

**Empty states:**
- Subtle breathing animation on the illustration (scale 1.0 ↔ 1.02, 3-second cycle)
- Don't overdo it — empty states shouldn't beg for attention

### Motion anti-patterns (things we don't do)

- Parallax on scroll (dated)
- Rotating loading spinners (use linear progress bars instead)
- Ken Burns effect on images
- Bouncing emojis
- Sliding text character-by-character ("typing" effect)
- Confetti
- Page flip effects on PDF viewer

## 2.6 Layout & spacing

### 4-point spacing scale

```kotlin
object Spacing {
    val xxs = 2.dp
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val xxxxl = 64.dp
}
```

Only these values. Never use 7.dp or 13.dp or any magic number — if something needs a custom value, you're probably doing it wrong.

### Common layout rules

- Screen padding: 16dp horizontal (`lg`), 24dp top/bottom (`xl`)
- Between sections: 32dp (`xxl`)
- Between related items: 12dp (`md`)
- Tight grouping: 4dp (`xs`)
- Minimum touch target: 48dp × 48dp, ALWAYS
- Maximum content width (for tablets): 640dp, centered, never full-width for reading content
- Card corner radius: 16dp (one value, used everywhere)
- Button corner radius: 12dp
- Chip corner radius: 8dp

### Safe areas

Every screen respects `WindowInsets.safeDrawing`. No hardcoded status bar offsets. Gesture navigation bar is respected. Camera screen goes edge-to-edge but keeps the capture button above the gesture bar.

## 2.7 Component library

Standard components we build once and use everywhere. These live in `:core:ui`.

### Buttons

```kotlin
// Primary
ScanButton(
    text = "Scan",
    icon = Icons.Scan,
    onClick = {},
    style = ScanButtonStyle.Primary  // filled, amber, 56dp tall
)

// Secondary
ScanButton(
    text = "Cancel",
    onClick = {},
    style = ScanButtonStyle.Secondary  // tonal, 48dp tall
)

// Tertiary / text
ScanButton(
    text = "Learn more",
    onClick = {},
    style = ScanButtonStyle.Text  // no background
)

// Destructive
ScanButton(
    text = "Delete",
    onClick = {},
    style = ScanButtonStyle.Destructive  // error color
)
```

### Cards

```kotlin
DocumentCard(
    document = doc,
    onTap = {},
    onLongPress = {},
    showSyncStatus = true
)

// Rules:
// - 16dp corner radius
// - 1dp outline in outlineVariant (no drop shadow)
// - Title: titleMedium, max 1 line, ellipsis
// - Metadata: bodyMedium in onSurfaceVariant
// - Thumbnail: 16:9 or actual aspect ratio, never stretched
```

### Bottom sheets

Used for:
- Export options
- Filter selection
- Batch actions
- Settings sub-screens

```kotlin
ScanBottomSheet(
    title = "Export as",
    onDismiss = {}
) {
    // Content slots
}

// Rules:
// - Title bar with drag handle
// - Max height: 75% of screen
// - Rounded top corners (24dp)
// - Content scrolls independently of the sheet
```

### Empty states

```kotlin
EmptyState(
    illustration = R.drawable.illustration_empty_library,
    title = "Your library is empty",
    description = "Scan your first document to get started.",
    primaryAction = "Scan now" to { navigateToCamera() }
)

// Rules:
// - Illustration: ~200dp, monochromatic with amber accent
// - Title: headlineSmall
// - Description: bodyMedium in onSurfaceVariant
// - Primary CTA button if there's an obvious action
// - Never show an empty state that only says "Nothing here"
```

### Toasts / snackbars

- Use Material snackbars
- Position above any bottom bar
- One action max (e.g., "Undo")
- Auto-dismiss at 4 seconds (8 seconds if there's an action)
- Never stack — new snackbars replace old ones

## 2.8 Haptics

Haptics are a massive delight lever on modern Android phones and cost nothing.

```kotlin
object Haptics {
    fun shutter() = HapticFeedbackConstants.CONFIRM                         // capture
    fun select()  = HapticFeedbackConstants.GESTURE_START                   // tap selection
    fun toggle()  = HapticFeedbackConstants.TOGGLE_ON / TOGGLE_OFF          // switches
    fun error()   = HapticFeedbackConstants.REJECT                          // invalid input
    fun success() = HapticFeedbackConstants.CONFIRM                         // success moments
    fun tick()    = HapticFeedbackConstants.CLOCK_TICK                      // scrubbing crop corners
}
```

Haptics fire on:
- Capture button press
- Crop corner snap to edge
- Filter change
- Selection mode toggle
- Successful export
- Error (subtle buzz)
- Switch toggle

Haptics do NOT fire on:
- Every button press (annoying)
- Scroll events
- Text input
- Navigation transitions

## 2.9 Sound design

One sound only: the shutter click.

Record (or license under CC0) a real camera shutter sample. Not a plastic click. Not a cartoon sound. A satisfying mechanical shutter.

Volume: respects system volume, can be muted in settings, respects silent mode.

No other sound effects. No "success" chimes. No button click sounds. No onboarding voiceover.

## 2.10 Dark mode rules

- Dark mode is not just "invert the colors"
- Surface colors are near-black but warm (`#17120C`) — pure black is harsh on OLED but too pure for our warm aesthetic
- Reduce white point: never use `#FFFFFF` on dark mode surfaces — max is `#EBE0D4`
- Shadows become more subtle (or disappear entirely — outlines are stronger indicators in dark mode)
- Images keep their full color, but we dim whites in the PDF reader by 10% in dark mode to reduce eye strain
- Amber accent stays similar but gains slight warmth

## 2.11 Accessibility rules

Non-negotiable:

- Every interactive element has a `contentDescription` in Compose
- Touch targets ≥ 48dp × 48dp
- Text contrast ≥ 4.5:1 for body, ≥ 3:1 for large text (WCAG AA)
- Font scaling up to 200% doesn't break layouts — test with Android accessibility settings
- All color-carrying information also uses icons or labels (no "green means success, red means fail" alone)
- Focus order makes sense for keyboard/D-pad navigation
- TalkBack announcements for important state changes (capture complete, OCR done, export ready)
- Reduced motion mode: if `Settings.Global.TRANSITION_ANIMATION_SCALE` is 0, disable all non-essential animations

## 2.12 Content & copy guidelines

Voice: **friendly, direct, technical-but-not-cold.**

Rules:

- Active voice: "Delete document" not "Document will be deleted"
- Second person: "Your scans stay on your phone" not "Scans stay on the user's phone"
- No technical jargon in user-facing text: "encrypted" yes, "AES-256-GCM" no
- Specific, not vague: "Exports as a PDF under 2 MB" not "Exports efficiently"
- One idea per sentence
- Sentence case for everything
- No emoji in the main UI (emoji in marketing copy is fine)
- No exclamation marks in system messages
- Error messages always include a next step: "Couldn't save. Try again or check your storage."

Example error messages:

| Wrong                                 | Right                                                |
|---------------------------------------|------------------------------------------------------|
| "An error occurred"                   | "Couldn't save this scan. Try again?"               |
| "Network request failed (HTTP 500)"   | "Couldn't reach the server. We'll retry soon."      |
| "Invalid input"                       | "Password must be at least 10 characters."          |
| "Operation successful"                | (no message — just let the UI reflect the result)   |

## 2.13 Onboarding

Three screens. Skippable. Never shown again.

**Screen 1:** "Scan anything, keep it private."
- Illustration: a stylized document inside a vault outline
- CTA: "Next"

**Screen 2:** "Works offline. No account needed."
- Illustration: a phone with an airplane icon and a big checkmark
- CTA: "Next"

**Screen 3:** "Allow camera access to start scanning."
- Clear rationale text
- Primary CTA: "Allow" (triggers the system permission dialog)
- Secondary: "Not now" (goes to the library, which is empty with a scan prompt)

Total time to first scan: under 20 seconds from first launch.

## 2.14 Play Store listing visual strategy

The Play Store listing is the first thing potential users see. It has to scream "better than CamScanner" at a glance.

### Screenshots (in order)

1. **Hero:** the camera view with a document being detected, amber corners glowing, text overlay "Real-time edge detection, 60fps"
2. **No watermark:** two side-by-side PDFs — CamScanner (watermarked) vs ScanVault (clean) — with a red X and green check. Text overlay: "No watermarks. Ever."
3. **No account required:** a big checkmark with "Works offline. No login." overlaid
4. **Library:** clean grid of documents, text overlay "Fast search across thousands of documents"
5. **OCR search:** a search result with highlighted text, overlay "Free OCR in 100+ languages"
6. **ID card mode:** front+back composite, overlay "Perfect ID scans in seconds"
7. **Privacy:** a padlock with "Your documents never leave your phone unless you choose"
8. **Export options:** showing PDF, JPEG, encrypted ZIP, plain text icons

### Feature graphic (1024×500)

- The Scan Amber logo mark, large, on the dark surface background
- App name in Inter Tight to the right
- Tagline: "The private document scanner."
- No feature list in the graphic itself — that's what screenshots are for

### Short description (80 chars)

> "Private, fast, free document scanner. No watermarks. No account. Works offline."

### Full description (4000 chars)

Opens with the differentiator, not the feature list. Structure:

1. One-sentence hook
2. The promise (no watermarks, no account, no ads in camera, offline)
3. What it does (scan, crop, OCR, export)
4. The modes (ID, receipt, book, whiteboard)
5. Privacy section
6. What you get free (everything essential)
7. Supported languages
8. Link to privacy policy

Keywords naturally included: document scanner, PDF scanner, OCR, free scanner, offline scanner, no watermark, ID scanner, receipt scanner, book scanner, privacy scanner.

## 2.15 Marketing site (post-launch)

`scanvault.app` — a single static page on GitHub Pages or Cloudflare Pages:

- Logo + one-sentence tagline
- One hero screenshot / video
- Three feature callouts (Privacy, Speed, Quality)
- "Download on Google Play" badge
- Privacy policy link
- Contact email
- Zero tracking, zero cookies, zero analytics

---

# Part 3: How This Doc Plugs Into the Build

Every phase in `FRONTEND_MVP.md` references this document for:

- **Phase 1:** haptics on capture, spring animations on edge detection, empty state for permission denial
- **Phase 2:** document card component, library empty state, bottom sheet for export options, typography scale for OCR text display
- **Phase 3:** color system for filter previews, onboarding screens, Play Store assets
- **Phase 4:** sync status UI using the semantic colors, account screens using the component library
- **Phase 5:** full accessibility pass, localization, final visual QA against this doc

If any decision comes up that isn't answered here, the default is "do the simpler, more restrained thing" and update this doc so the next decision is automatic.

This document is the single source of truth for how ScanVault looks and feels. When it contradicts Material 3 defaults, this document wins. When it contradicts itself, the latest committed version wins and the earlier rule gets deleted.
