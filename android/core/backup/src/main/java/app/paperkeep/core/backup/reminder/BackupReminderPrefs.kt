package app.paperkeep.core.backup.reminder

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupReminderDataStore by preferencesDataStore(name = "backup_reminder_prefs")

/**
 * P4.4 — persists the user's choice of [BackupReminderCadence].
 *
 * Pure DataStore wrapper; the [BackupReminderScheduler] is invoked by the
 * settings ViewModel after this Flow updates so we keep crash-recovery cheap.
 */
@Singleton
class BackupReminderPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val cadence: Flow<BackupReminderCadence> =
        context.backupReminderDataStore.data.map { prefs ->
            BackupReminderCadence.fromKey(prefs[KEY_CADENCE])
        }

    suspend fun set(cadence: BackupReminderCadence) {
        context.backupReminderDataStore.edit { prefs ->
            prefs[KEY_CADENCE] = cadence.storageKey
        }
    }

    companion object {
        private val KEY_CADENCE = stringPreferencesKey("cadence_v1")
    }
}
