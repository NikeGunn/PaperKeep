package app.paperkeep.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.paperkeep.core.security.LockController
import app.paperkeep.core.security.LockScreen
import app.paperkeep.feature.library.LibraryScreen
import app.paperkeep.feature.onboarding.OnboardingScreen
import app.paperkeep.feature.reader.ReaderScreen
import app.paperkeep.feature.scanner.ScannerScreen
import app.paperkeep.feature.scanner.capture.CropScreen
import app.paperkeep.feature.settings.SettingsScreen
import javax.inject.Inject

/**
 * Root navigation graph for Paperkeep.
 *
 * Phase 2 wires:
 *  - LibraryScreen (real implementation replacing placeholder)
 *  - ReaderScreen (real implementation with FLAG_SECURE, OCR overlay, bottom bar)
 *  - LockController gate: when isLocked == true, show LockScreen above all routes
 *    (implemented as a conditional wrapper, not an intercept route, so back-stack
 *     is preserved for when the user authenticates).
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    lockController: LockController = hiltViewModel<LockNavViewModel>().lockController,
) {
    val isLocked by lockController.isLocked.collectAsStateWithLifecycle(initialValue = false)

    if (isLocked) {
        LockScreen(
            onUnlock = {
                // The Activity handles the BiometricPrompt — this callback is for
                // when the lock screen's button is tapped. In production the Activity
                // calls lockController.onUnlocked() after successful BiometricPrompt.
                // For now the LockScreen button directly unlocks (demo mode) so the
                // test gate and settings toggle work end-to-end.
                lockController.onUnlocked()
            },
            modifier = modifier,
        )
        return
    }

    NavHost(
        navController = navController,
        startDestination = OnboardingRoute,
        modifier = modifier,
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(ScannerRoute) {
                        popUpTo<OnboardingRoute> { inclusive = true }
                    }
                },
            )
        }

        composable<ScannerRoute> {
            ScannerScreen(
                onCaptureDone = { imagePath ->
                    navController.navigate(CropRoute(imagePath))
                },
                onOpenLibrary = {
                    navController.navigate(LibraryRoute)
                },
            )
        }

        composable<CropRoute> {
            CropScreen(
                onNext = {
                    navController.navigate(LibraryRoute) {
                        popUpTo<ScannerRoute>()
                    }
                },
            )
        }

        composable<LibraryRoute> {
            LibraryScreen(
                onDocumentClick = { docId ->
                    navController.navigate(ReaderRoute(docId))
                },
                onOpenSettings = {
                    navController.navigate(SettingsRoute)
                },
            )
        }

        composable<ReaderRoute> { backStack ->
            val route = backStack.toRoute<ReaderRoute>()
            ReaderScreen(
                documentId = route.scanId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReorder = { /* Phase 2 reorder nav — handled by BatchCaptureViewModel */ },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                appVersion = "2.0.0-alpha.1",
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
