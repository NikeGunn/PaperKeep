package app.paperkeep.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.paperkeep.feature.onboarding.OnboardingScreen
import app.paperkeep.feature.scanner.ScannerScreen
import app.paperkeep.feature.scanner.capture.CropScreen
import app.paperkeep.feature.settings.SettingsScreen

/**
 * Root navigation graph.
 *
 * Uses the type-safe Navigation Compose API (Navigation 2.8+):
 *  - composable<T> instead of string routes
 *  - navigate<T>() for compile-time safety
 *
 * Onboarding gate: start destination is [OnboardingRoute]. The OnboardingScreen
 * itself observes a DataStore flag and calls onComplete() immediately if the user
 * has already completed onboarding — no flash, no delay.
 *
 * Placeholder screens (Library, Reader) are replaced in Phase 2.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
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

        composable<CropRoute> { backStack ->
            val route = backStack.toRoute<CropRoute>()
            CropScreen(
                onNext = {
                    navController.navigate(LibraryRoute) {
                        popUpTo<ScannerRoute>()
                    }
                },
            )
        }

        composable<LibraryRoute> {
            LibraryPlaceholderScreen(
                onOpenScan = { scanId ->
                    navController.navigate(ReaderRoute(scanId))
                },
                onOpenSettings = {
                    navController.navigate(SettingsRoute)
                },
            )
        }

        composable<ReaderRoute> { backStack ->
            val route = backStack.toRoute<ReaderRoute>()
            ReaderPlaceholderScreen(scanId = route.scanId)
        }

        composable<SettingsRoute> {
            SettingsScreen(
                appVersion = "2.0.0-alpha.1",
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

// ── Phase-1/2 placeholder screens ─────────────────────────────────────────────
// These are replaced by real implementations in Phase 2.

@Composable
private fun LibraryPlaceholderScreen(
    onOpenScan: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    androidx.compose.material3.Text("Library")
}

@Composable
private fun ReaderPlaceholderScreen(scanId: String) {
    androidx.compose.material3.Text("Reader: $scanId")
}
