Play Store Screenshots
======================

Required: ≥2 phone screenshots (Play policy). Recommended: 8.
Dimensions: 1080 × 1920 px portrait (9:16). PNG or JPEG, no alpha, ≤8 MB.

CAPTURE SCRIPT
--------------
Connect phone with USB debugging and run:

  adb shell screencap -p /sdcard/ss.png && adb pull /sdcard/ss.png <filename>

Or in Android Studio: Device Manager → camera icon → save PNG.

REQUIRED SCREENSHOTS (capture in this order)
---------------------------------------------

  01_scanner_live.png
    Screen:   ScannerScreen — camera live, edge detection quad visible
    Show:     Real document on a table, orange corner handles overlaid
    Caption:  "Real-time edge detection"

  02_crop_doctype.png
    Screen:   CropScreen — DocTypeChip showing "Receipt 🧾", filter strip
    Show:     Receipt in crop view, filter strip visible at bottom
    Caption:  "Smart document classification"

  03_library_grid.png
    Screen:   LibraryScreen — 2-col grid with 6–8 scanned documents
    Show:     Mix of receipts, ID cards, A4 docs with thumbnails
    Caption:  "Everything organised"

  04_reader_ocr.png
    Screen:   ReaderScreen — full-page doc, bottom bar visible
    Show:     A4 document with readable text
    Caption:  "Search inside every document"

  05_id_card.png
    Screen:   IdCardScreen — step 2 (AwaitingBack), front side preview
    Show:     Front side thumbnail displayed, "Now scan the back side" text
    Caption:  "ID + card front & back on one page"

  06_receipt_data.png
    Screen:   LibraryScreen — receipt card showing extracted chips
    Show:     Receipt document card with total/date/merchant chips visible
    Caption:  "Receipt data extracted automatically"

  07_signature.png
    Screen:   ReaderScreen — signature placement drag handle visible
    Show:     Signature overlaid on a document page
    Caption:  "Sign documents on your phone"

  08_settings_security.png
    Screen:   SettingsScreen — Security section, biometric lock toggle ON
    Show:     "100% offline" pill at top, lock settings section
    Caption:  "AES-256 encrypted. Biometric lock."

FEATURE GRAPHIC (see feature-graphic-spec.txt)
----------------------------------------------

  feature_graphic.jpg  — 1024 × 500 px, JPEG 90%, ~80 KB target
  Content: Paperkeep wordmark + "Scan. Organise. Export." tagline
           on ScanAmber gradient (#F9A825 → #F57F17)

CHECKLIST BEFORE SUBMISSION
----------------------------
[ ] All 8 screenshots captured on a real device (Pixel 6a or similar)
[ ] feature_graphic.jpg present (1024×500, <1MB)
[ ] No status bar icons showing notifications or low battery
[ ] No personal data visible in any screenshot
[ ] Screenshots match current app UI (no outdated placeholders)
