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

/** Camera / scanner screen — entry point of the app. */
@Serializable
object ScannerRoute

/**
 * Manual crop screen — shown after a capture.
 * [capturedImagePath] is the path to the temporary full-res bitmap written by CameraX.
 */
@Serializable
data class CropRoute(val capturedImagePath: String)

/** Document library screen. */
@Serializable
object LibraryRoute

/** Document reader — opens a single scan. */
@Serializable
data class ReaderRoute(val scanId: String)

/** App settings screen. */
@Serializable
object SettingsRoute

/** Onboarding flow — shown once on first launch, guarded by DataStore flag. */
@Serializable
object OnboardingRoute
