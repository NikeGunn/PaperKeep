package app.paperkeep

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.paperkeep.core.security.BiometricLockManager
import app.paperkeep.core.security.LockController
import app.paperkeep.core.ui.theme.AppTheme
import app.paperkeep.core.ui.theme.PaperkeepTheme
import app.paperkeep.core.ui.theme.ThemePreferences
import app.paperkeep.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var themePreferences: ThemePreferences
    @Inject lateinit var biometricLockManager: BiometricLockManager
    @Inject lateinit var lockController: LockController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setRecentAppsAppearance()
        setContent {
            val appTheme by themePreferences.appTheme.collectAsStateWithLifecycle(AppTheme.SYSTEM)
            val oledTrueBlack by themePreferences.oledTrueBlack.collectAsStateWithLifecycle(false)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appTheme) {
                AppTheme.DARK   -> true
                AppTheme.LIGHT  -> false
                AppTheme.SYSTEM -> systemDark
            }
            PaperkeepTheme(
                darkTheme = darkTheme,
                oledTrueBlack = oledTrueBlack,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost(
                        onRequestBiometric = { onSuccess, onFailure ->
                            biometricLockManager.showPrompt(
                                activity = this,
                                onSuccess = onSuccess,
                                onFailure = onFailure,
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setRecentAppsAppearance()
    }

    override fun onStop() {
        super.onStop()
        // IMMEDIATE timeout: lock the moment the app leaves the foreground.
        // Other timeouts: LockController.isLocked re-evaluates on next onResume
        // because the elapsed time is checked reactively against the stored timestamp.
        lockController.onAppBackground()
    }

    /**
     * P2.14 — Sets a neutral task description so the system task switcher shows
     * only "Paperkeep" + a static brand color instead of a live screenshot of
     * document content.
     */
    @Suppress("DEPRECATION")
    private fun setRecentAppsAppearance() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(
                ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    /* icon = */ null,
                    /* colorPrimary = */ 0xFF7A4F00.toInt(),
                )
            )
        }
    }
}
