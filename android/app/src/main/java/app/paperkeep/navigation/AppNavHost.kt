package app.paperkeep.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.paperkeep.feature.reader.reorder.ReaderReorderHost
import app.paperkeep.core.security.LockController
import app.paperkeep.core.security.LockScreen
import app.paperkeep.feature.library.LibraryScreen
import app.paperkeep.feature.onboarding.OnboardingScreen
import app.paperkeep.feature.reader.ReaderScreen
import app.paperkeep.feature.scanner.ScannerScreen
import app.paperkeep.feature.scanner.capture.CaptureViewModel
import app.paperkeep.feature.scanner.capture.CropScreen
import app.paperkeep.feature.scanner.filter.FilterReviewScreen
import app.paperkeep.feature.settings.ProUpgradeScreen
import app.paperkeep.feature.settings.SettingsScreen
import app.paperkeep.feature.settings.backup.BackupScreen
import app.paperkeep.feature.settings.storage.StorageManagerScreen

/**
 * Root navigation graph for Paperkeep.
 *
 * Navigation structure:
 *  - Bottom navigation bar on "home" destinations (Library, Settings)
 *  - Centered FAB for scan (navigates to ScannerRoute, hides bottom bar)
 *  - Scanner, Crop, Reader, Backup, Storage, ProUpgrade are full-screen (no bottom bar)
 *  - LockController gate: when isLocked == true, shows LockScreen above all routes
 */
@Composable
fun AppNavHost(
    onRequestBiometric: (onSuccess: () -> Unit, onFailure: (String) -> Unit) -> Unit = { _, _ -> },
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
            onRequestBiometric = onRequestBiometric,
            onUnlocked = { lockController.onUnlocked() },
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

    val startDestination: Any = if (completed) LibraryRoute else OnboardingRoute

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Bottom nav is visible only on the two top-level destinations.
    // Use type-safe hasRoute<T>() rather than matching KClass.qualifiedName against
    // the route string — the latter breaks under R8 obfuscation in release builds
    // (the back-stack route and the reflected qualified name desync), which hid the
    // bottom bar entirely on the published APK.
    val isLibrary = currentDestination?.hasRoute<LibraryRoute>() == true
    val isSettings = currentDestination?.hasRoute<SettingsRoute>() == true
    val showBottomNav = isLibrary || isSettings

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomNav,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                PaperkeepBottomBar(
                    isLibrarySelected = isLibrary,
                    isSettingsSelected = isSettings,
                    onLibraryClick = {
                        navController.navigate(LibraryRoute) {
                            popUpTo(LibraryRoute) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onScanClick = {
                        navController.navigate(ScannerRoute())
                    },
                    onSettingsClick = {
                        navController.navigate(SettingsRoute) {
                            popUpTo(LibraryRoute) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(LibraryRoute) {
                            popUpTo<OnboardingRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable<ScannerRoute> { backStack ->
                val route = backStack.toRoute<ScannerRoute>()
                ScannerScreen(
                    onPagesReady = {
                        // ML Kit handed us a batch of cropped pages. Move to
                        // the filter-review screen so the user picks an
                        // image effect before persistence.
                        navController.navigate(
                            FilterReviewRoute(
                                appendToDocumentId = route.appendToDocumentId,
                                replacePageId = route.replacePageId,
                            )
                        ) {
                            popUpTo<ScannerRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onCancelled = { navController.popBackStack() },
                    onError = { navController.popBackStack() },
                    captureViewModel = captureViewModel,
                )
            }

            composable<FilterReviewRoute> { backStack ->
                val route = backStack.toRoute<FilterReviewRoute>()
                FilterReviewScreen(
                    appendToDocumentId = route.appendToDocumentId,
                    replacePageId = route.replacePageId,
                    onSaved = { docId ->
                        // When appending/replacing into an existing document
                        // there's already a ReaderRoute below us in the back
                        // stack. Pop back to it so its ON_RESUME refresh()
                        // reloads the document with the freshly-added pages,
                        // rather than pushing a stale duplicate.
                        val isAppending = route.appendToDocumentId != null ||
                            route.replacePageId != null
                        val poppedToReader = if (isAppending) {
                            navController.popBackStack(ReaderRoute(docId), inclusive = false)
                        } else false
                        if (!poppedToReader) {
                            navController.navigate(ReaderRoute(docId)) {
                                popUpTo<FilterReviewRoute> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onCancelled = { navController.popBackStack() },
                    captureViewModel = captureViewModel,
                )
            }

            composable<CropRoute> { backStack ->
                val route = backStack.toRoute<CropRoute>()
                val append = route.appendToDocumentId
                val replaceId = route.replacePageId
                CropScreen(
                    onNext = {
                        captureViewModel.saveCurrentCapture(
                            appendToDocumentId = append,
                            replacePageId = replaceId,
                        ) { docId ->
                            if (append != null) {
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
                    contentPadding = innerPadding,
                    onDocumentClick = { docId ->
                        navController.navigate(ReaderRoute(docId))
                    },
                    onScanNew = {
                        navController.navigate(ScannerRoute())
                    },
                )
            }

            composable<ReaderRoute> { backStack ->
                val route = backStack.toRoute<ReaderRoute>()
                ReaderScreen(
                    documentId = route.scanId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReorder = { docId ->
                        navController.navigate(PageReorderRoute(docId))
                    },
                    onAddPage = {
                        navController.navigate(ScannerRoute(appendToDocumentId = route.scanId))
                    },
                    onRetakePage = { docId, pageId ->
                        navController.navigate(
                            ScannerRoute(appendToDocumentId = docId, replacePageId = pageId)
                        )
                    },
                    onReCropPage = { docId, pageId ->
                        // Re-crop currently navigates to scanner so the user can recapture
                        // (a full in-place re-crop flow against the stored encrypted page
                        //  is queued for the next release alongside the other 5 edit tools).
                        navController.navigate(
                            ScannerRoute(appendToDocumentId = docId, replacePageId = pageId)
                        )
                    },
                )
            }

            composable<PageReorderRoute> { backStack ->
                val route = backStack.toRoute<PageReorderRoute>()
                ReaderReorderHost(
                    documentId = route.documentId,
                    onDone = { navController.popBackStack() },
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    appVersion = "2.0.0-alpha.1",
                    showBackButton = false,
                    contentPadding = innerPadding,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenBackup = { navController.navigate(BackupRoute) },
                    onOpenStorage = { navController.navigate(StorageRoute) },
                    onOpenProUpgrade = { navController.navigate(ProUpgradeRoute) },
                )
            }

            composable<BackupRoute> {
                BackupScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<StorageRoute> {
                StorageManagerScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<ProUpgradeRoute> {
                ProUpgradeScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

// ── Bottom navigation bar ──────────────────────────────────────────────────────

@Composable
private fun PaperkeepBottomBar(
    isLibrarySelected: Boolean,
    isSettingsSelected: Boolean,
    onLibraryClick: () -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        NavigationBar {
            NavigationBarItem(
                selected = isLibrarySelected,
                onClick = onLibraryClick,
                icon = {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = null,
                    )
                },
                label = { Text("Library") },
                modifier = Modifier.semantics { contentDescription = "Library tab" },
            )

            // Center placeholder — the FAB sits above this
            NavigationBarItem(
                selected = false,
                onClick = onScanClick,
                icon = { Box(modifier = Modifier.size(24.dp)) },
                label = { Text("Scan") },
                enabled = false,
            )

            NavigationBarItem(
                selected = isSettingsSelected,
                onClick = onSettingsClick,
                icon = {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                    )
                },
                label = { Text("Settings") },
                modifier = Modifier.semantics { contentDescription = "Settings tab" },
            )
        }

        // Centered elevated scan FAB floating above the navigation bar
        FloatingActionButton(
            onClick = onScanClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp,
            ),
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .semantics { contentDescription = "Scan new document" },
        ) {
            Icon(
                Icons.Filled.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
