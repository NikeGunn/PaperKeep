package app.paperkeep.core.common.billing

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

private val Context.proDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pro_status"
)

/**
 * Persists Pro IAP status in DataStore.
 *
 * Stores the purchase token alongside the isPro flag so it can be
 * re-verified on next launch. Token is never logged or displayed.
 */
@Singleton
class ProStatusStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val KEY_IS_PRO = booleanPreferencesKey("is_pro")
    private val KEY_PURCHASE_TOKEN = stringPreferencesKey("purchase_token")

    val isPro: Flow<Boolean> = context.proDataStore.data.map { prefs ->
        prefs[KEY_IS_PRO] ?: false
    }

    suspend fun setProStatus(isPro: Boolean, purchaseToken: String?) {
        context.proDataStore.edit { prefs ->
            prefs[KEY_IS_PRO] = isPro
            if (purchaseToken != null) {
                prefs[KEY_PURCHASE_TOKEN] = purchaseToken
            } else {
                prefs.remove(KEY_PURCHASE_TOKEN)
            }
        }
    }

    suspend fun getPurchaseToken(): String? {
        var token: String? = null
        context.proDataStore.edit { prefs ->
            token = prefs[KEY_PURCHASE_TOKEN]
        }
        return token
    }

    suspend fun clearProStatus() {
        context.proDataStore.edit { prefs ->
            prefs.remove(KEY_IS_PRO)
            prefs.remove(KEY_PURCHASE_TOKEN)
        }
    }
}
