package app.paperkeep

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.ui.theme.AppTheme
import app.paperkeep.core.ui.theme.PaperkeepTheme
import app.paperkeep.core.ui.theme.ThemePreferences
import app.paperkeep.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themePreferences: ThemePreferences

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
                    AppNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setRecentAppsAppearance()
    }

    /**
     * P2.14 — Sets a neutral task description so the system task switcher
     * shows only "Paperkeep" + a static brand color instead of a live screenshot
     * of document content. This supplements FLAG_SECURE (which prevents the
     * screenshot entirely on most launchers) with a defence-in-depth fallback.
     *
     * Icon null → system falls back to the launcher icon.
     * Color 0xFF7A4F00 matches the M3 light primary derived from our Saffron anchor.
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
