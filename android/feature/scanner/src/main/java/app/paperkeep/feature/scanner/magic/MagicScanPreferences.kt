package app.paperkeep.feature.scanner.magic

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.magicScanDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "magic_scan_prefs",
)

/**
 * Persists the Magic Scan toggle (auto-capture default on, user can disable
 * for manual photo mode).
 *
 * Default is **on** so first-time users get the CamScanner-style experience
 * without having to discover a setting.
 */
@Singleton
class MagicScanPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyEnabled = booleanPreferencesKey("magic_scan_enabled")

    val isEnabled: Flow<Boolean> = context.magicScanDataStore.data
        .map { prefs -> prefs[keyEnabled] ?: DEFAULT_ENABLED }

    suspend fun setEnabled(enabled: Boolean) {
        context.magicScanDataStore.edit { prefs -> prefs[keyEnabled] = enabled }
    }

    companion object {
        const val DEFAULT_ENABLED: Boolean = true
    }
}
