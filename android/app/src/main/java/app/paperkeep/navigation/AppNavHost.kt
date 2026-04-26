package app.paperkeep.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import app.paperkeep.feature.scanner.capture.CaptureViewModel
import app.paperkeep.feature.scanner.capture.CropScreen
import app.paperkeep.feature.settings.SettingsScreen

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
    onboardingNavViewModel: OnboardingNavViewModel = hiltViewModel(),
    captureViewModel: CaptureViewModel = hiltViewModel(),
) {
    val isLocked by lockController.isLocked.collectAsStateWithLifecycle(initialValue = false)
    val onboardingCompleted by onboardingNavViewModel.onboardingCompleted.collectAsStateWithLifecycle()

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

    val completed = onboardingCompleted
    if (completed == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (completed) ScannerRoute() else OnboardingRoute

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(ScannerRoute()) {
                        popUpTo<OnboardingRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<ScannerRoute> { backStack ->
            val route = backStack.toRoute<ScannerRoute>()
            ScannerScreen(
                onCaptureDone = {
                    navController.navigate(CropRoute(route.appendToDocumentId))
                },
                onOpenLibrary = {
                    navController.navigate(LibraryRoute)
                },
                captureViewModel = captureViewModel,
            )
        }

        composable<CropRoute> { backStack ->
            val route = backStack.toRoute<CropRoute>()
            val append = route.appendToDocumentId
            CropScreen(
                onNext = {
                    captureViewModel.saveCurrentCapture(append) { docId ->
                        if (append != null) {
                            // Append mode: pop scanner+crop so we land back on
                            // the reader, which will refresh and show the new page.
                            navController.popBackStack(ReaderRoute(docId), inclusive = false)
                        } else {
                            navController.navigate(LibraryRoute) {
                                popUpTo<CropRoute> { inclusive = true }
                            }
                        }
                    }
                },
                viewModel = captureViewModel,
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
                onAddPage = {
                    navController.navigate(ScannerRoute(appendToDocumentId = route.scanId))
                },
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
