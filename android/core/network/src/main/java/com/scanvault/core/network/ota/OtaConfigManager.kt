package com.scanvault.core.network.ota

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "OtaConfigManager"
private const val POLL_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
private const val RETRY_INTERVAL_MS = 5 * 60 * 1000L     // 5 min on failure

@Singleton
class OtaConfigManager @Inject constructor(
    private val httpClient: HttpClient,
    @Named("ota_config_url") private val configUrl: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _config = MutableStateFlow(OtaConfig.DEFAULT)
    val config: StateFlow<OtaConfig> = _config.asStateFlow()

    val killSwitches: KillSwitches get() = _config.value.killSwitches
    val maintenance: Maintenance get() = _config.value.maintenance
    val forceUpgrade: ForceUpgrade get() = _config.value.forceUpgrade

    init {
        scope.launch { pollForever() }
    }

    suspend fun fetchNow(): OtaConfig {
        return try {
            val response = httpClient.get(configUrl)
            if (response.status.isSuccess()) {
                val raw: String = response.body()
                val parsed = json.decodeFromString<OtaConfig>(raw)
                _config.value = parsed
                Log.d(TAG, "OTA config refreshed: schema=${parsed.schemaVersion} updated=${parsed.updatedAt}")
                parsed
            } else {
                Log.w(TAG, "OTA config fetch failed: ${response.status}")
                _config.value
            }
        } catch (e: Exception) {
            Log.w(TAG, "OTA config fetch error (using cached): ${e.message}")
            _config.value
        }
    }

    private suspend fun pollForever() {
        while (true) {
            val before = _config.value.updatedAt
            fetchNow()
            val interval = if (_config.value.updatedAt == before && before.isEmpty()) {
                RETRY_INTERVAL_MS
            } else {
                POLL_INTERVAL_MS
            }
            delay(interval)
        }
    }
}
