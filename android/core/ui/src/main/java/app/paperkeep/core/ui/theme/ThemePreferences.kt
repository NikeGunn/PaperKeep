package app.paperkeep.core.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

enum class AppTheme(val key: String, val label: String) {
    SYSTEM("system", "Follow system"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    ;
    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyTheme = stringPreferencesKey("app_theme")
    private val keyOledBlack = booleanPreferencesKey("oled_true_black")

    val appTheme: Flow<AppTheme> = context.themeDataStore.data
        .map { prefs -> AppTheme.fromKey(prefs[keyTheme] ?: AppTheme.SYSTEM.key) }

    val oledTrueBlack: Flow<Boolean> = context.themeDataStore.data
        .map { prefs -> prefs[keyOledBlack] ?: false }

    suspend fun setAppTheme(theme: AppTheme) {
        context.themeDataStore.edit { prefs -> prefs[keyTheme] = theme.key }
    }

    suspend fun setOledTrueBlack(enabled: Boolean) {
        context.themeDataStore.edit { prefs -> prefs[keyOledBlack] = enabled }
    }
}
