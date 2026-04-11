package com.scanvault.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.scanvault.feature.scanner.ScannerScreen

/**
 * Root navigation graph (1B.18).
 *
 * Uses the type-safe Navigation Compose API (Navigation 2.8+):
 *  - composable<T> instead of composable(route = "...") strings
 *  - navigate<T>() for compile-time route safety
 *
 * Screens are simple placeholders for Phase 1; full implementations come in
 * Phase 2 (Library, Reader) and Phase 3 (Settings, Onboarding).
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = ScannerRoute,
        modifier = modifier,
    ) {
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
            CropPlaceholderScreen(
                capturedImagePath = route.capturedImagePath,
                onNext = {
                    navController.navigate(LibraryRoute) {
                        popUpTo<ScannerRoute>()
                    }
                },
                onRetake = { navController.popBackStack() },
            )
        }

        composable<LibraryRoute> {
            LibraryPlaceholderScreen(
                onOpenScan = { scanId ->
                    navController.navigate(ReaderRoute(scanId))
                },
            )
        }

        composable<ReaderRoute> { backStack ->
            val route = backStack.toRoute<ReaderRoute>()
            ReaderPlaceholderScreen(scanId = route.scanId)
        }

        composable<SettingsRoute> {
            SettingsPlaceholderScreen()
        }
    }
}

// ── Phase-1 placeholder screens ───────────────────────────────────────────────
// These are replaced by real implementations in later phases.

@Composable
private fun CropPlaceholderScreen(
    capturedImagePath: String,
    onNext: () -> Unit,
    onRetake: () -> Unit,
) {
    // Real CropScreen (1B.14) will be wired here; for now it's a no-op placeholder
    // that the navigation tests can verify exists in the graph.
    androidx.compose.material3.Text("Crop: $capturedImagePath")
}

@Composable
private fun LibraryPlaceholderScreen(onOpenScan: (String) -> Unit) {
    androidx.compose.material3.Text("Library")
}

@Composable
private fun ReaderPlaceholderScreen(scanId: String) {
    androidx.compose.material3.Text("Reader: $scanId")
}

@Composable
private fun SettingsPlaceholderScreen() {
    androidx.compose.material3.Text("Settings")
}
