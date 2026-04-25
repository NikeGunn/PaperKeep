package app.paperkeep

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.paperkeep.core.ui.theme.PaperkeepTheme
import app.paperkeep.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setRecentAppsAppearance()
        setContent {
            PaperkeepTheme {
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
