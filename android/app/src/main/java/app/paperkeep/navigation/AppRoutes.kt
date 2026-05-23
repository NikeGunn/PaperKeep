package app.paperkeep.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for Paperkeep (1B.18).
 *
 * Uses @Serializable objects/data classes as required by Navigation Compose 2.8+
 * with the type-safe API (navigate<T>, composable<T>).
 *
 * Graph:
 *   Scanner (camera) ──► Crop ──► Library
 *                    └──► Library (from bottom nav)
 */

/**
 * Camera / scanner screen — entry point of the app.
 *
 * @param appendToDocumentId when non-null, the captured page is appended to
 * the existing document with this id (instead of creating a new document).
 * Used by the reader's "Add page" button.
 * @param replacePageId when non-null, the captured page replaces the page
 * with this id in [appendToDocumentId] (Edit toolbar — Retake tool). Mutually
 * exclusive in intent with the append-only flow but the same fields are
 * reused so existing capture code keeps working.
 */
@Serializable
data class ScannerRoute(
    val appendToDocumentId: String? = null,
    val replacePageId: String? = null,
)

/**
 * Filter review screen — shown after Google's ML Kit scanner returns cropped
 * pages. Lets the user pick one of the 10 image effects (Original, Auto,
 * Magic Color, Document, Lighten, Vivid, Whiteboard, Grayscale, B&W, Sepia)
 * before the pages are persisted.
 *
 * Pages live in [app.paperkeep.feature.scanner.capture.CaptureViewModel];
 * this route only carries the append/replace intent identical to [ScannerRoute].
 */
@Serializable
data class FilterReviewRoute(
    val appendToDocumentId: String? = null,
    val replacePageId: String? = null,
)

/**
 * Manual crop screen — shown after a capture.
 * Crop state is kept in [app.paperkeep.feature.scanner.capture.CaptureViewModel],
 * so this route does not carry image data — only the append target id.
 *
 * [replacePageId], when non-null, signals the save flow to overwrite that
 * existing page instead of appending a new one (Edit toolbar — Retake tool).
 */
@Serializable
data class CropRoute(
    val appendToDocumentId: String? = null,
    val replacePageId: String? = null,
)

/** Document library screen. */
@Serializable
object LibraryRoute

/** Document reader — opens a single scan. */
@Serializable
data class ReaderRoute(val scanId: String)

/** Page reorder screen (Edit toolbar — Reorder Pages tool). */
@Serializable
data class PageReorderRoute(val documentId: String)

/** App settings screen. */
@Serializable
object SettingsRoute

/** Onboarding flow — shown once on first launch, guarded by DataStore flag. */
@Serializable
object OnboardingRoute

/** Backup & Restore screen (P4.1–P4.5). */
@Serializable
object BackupRoute

/** Storage manager screen (P4.6). */
@Serializable
object StorageRoute

/** Pro upgrade / IAP screen (P5.2). */
@Serializable
object ProUpgradeRoute

/** Document summarizer result screen (P5.1). */
@Serializable
data class SummaryRoute(val documentId: String)
