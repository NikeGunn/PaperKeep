package app.paperkeep.core.network.ota

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val VALID_CONFIG = """
{
  "schema_version": 2,
  "updated_at": "2026-04-17T10:00:00Z",
  "force_upgrade": {"enabled": false, "min_version_code": 0, "message": "", "store_url": ""},
  "soft_upgrade": {"enabled": false, "min_version_code": 0, "message": ""},
  "kill_switches": {
    "ocr_enabled": true,
    "cloud_sync_enabled": false,
    "ai_classify_enabled": true,
    "super_resolve_enabled": true,
    "admob_enabled": true,
    "backup_enabled": true
  },
  "rate_limits": {"ocr_per_day": 50, "ai_tasks_per_day": 10, "super_resolve_per_day": 5},
  "maintenance": {"active": false, "message": "", "ends_at": null},
  "feature_flags": {"show_widget_promo": false, "show_qs_tile_promo": true, "new_onboarding_flow": false}
}
""".trimIndent()

private val MAINTENANCE_CONFIG = """
{
  "schema_version": 1,
  "updated_at": "2026-04-17T12:00:00Z",
  "force_upgrade": {"enabled": false, "min_version_code": 0, "message": "", "store_url": ""},
  "soft_upgrade": {"enabled": false, "min_version_code": 0, "message": ""},
  "kill_switches": {"ocr_enabled": true, "cloud_sync_enabled": true, "ai_classify_enabled": true, "super_resolve_enabled": true, "admob_enabled": true, "backup_enabled": true},
  "rate_limits": {"ocr_per_day": 100, "ai_tasks_per_day": 20, "super_resolve_per_day": 10},
  "maintenance": {"active": true, "message": "Scheduled maintenance until 2pm IST.", "ends_at": "2026-04-17T08:30:00Z"},
  "feature_flags": {"show_widget_promo": true, "show_qs_tile_promo": true, "new_onboarding_flow": false}
}
""".trimIndent()

private fun buildClient(responseBody: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
    val engine = MockEngine { _ ->
        if (status == HttpStatusCode.OK) {
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        } else {
            respondError(status)
        }
    }
    return HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }
}

class OtaConfigManagerTest {

    @Test
    fun `fetchNow returns default config when URL returns 500`() = runTest {
        val client = buildClient("", HttpStatusCode.InternalServerError)
        val manager = OtaConfigManager(client, "https://example.com/ota/config.json")
        val result = manager.fetchNow()
        assertEquals(OtaConfig.DEFAULT, result)
    }

    @Test
    fun `fetchNow parses valid config`() = runTest {
        val client = buildClient(VALID_CONFIG)
        val manager = OtaConfigManager(client, "https://example.com/ota/config.json")
        val result = manager.fetchNow()
        assertEquals(2, result.schemaVersion)
        assertEquals("2026-04-17T10:00:00Z", result.updatedAt)
        assertFalse(result.killSwitches.cloudSyncEnabled)
        assertTrue(result.killSwitches.ocrEnabled)
        assertEquals(50, result.rateLimits.ocrPerDay)
        assertFalse(result.featureFlags.showWidgetPromo)
    }

    @Test
    fun `config StateFlow updates after fetchNow`() = runTest {
        val client = buildClient(VALID_CONFIG)
        val manager = OtaConfigManager(client, "https://example.com/ota/config.json")
        assertEquals(OtaConfig.DEFAULT, manager.config.value)
        manager.fetchNow()
        assertEquals(2, manager.config.value.schemaVersion)
    }

    @Test
    fun `maintenance active flag is surfaced`() = runTest {
        val client = buildClient(MAINTENANCE_CONFIG)
        val manager = OtaConfigManager(client, "https://example.com/ota/config.json")
        manager.fetchNow()
        assertTrue(manager.maintenance.active)
        assertEquals("Scheduled maintenance until 2pm IST.", manager.maintenance.message)
    }

    @Test
    fun `kill switches default to all enabled`() {
        val ks = KillSwitches()
        assertTrue(ks.ocrEnabled)
        assertTrue(ks.cloudSyncEnabled)
        assertTrue(ks.aiClassifyEnabled)
        assertTrue(ks.superResolveEnabled)
        assertTrue(ks.admobEnabled)
        assertTrue(ks.backupEnabled)
    }

    @Test
    fun `force upgrade defaults to disabled`() {
        val fu = ForceUpgrade()
        assertFalse(fu.enabled)
        assertEquals(0, fu.minVersionCode)
    }

    @Test
    fun `unknown JSON keys are ignored (future compat)`() = runTest {
        val configWithExtra = VALID_CONFIG.replace(
            "\"schema_version\": 2",
            "\"schema_version\": 2, \"future_field\": \"ignored\""
        )
        val client = buildClient(configWithExtra)
        val manager = OtaConfigManager(client, "https://example.com/ota/config.json")
        val result = manager.fetchNow()
        assertEquals(2, result.schemaVersion)
    }

    @Test
    fun `fetchNow returns cached config on network exception`() = runTest {
        val engine = MockEngine { _ -> throw java.io.IOException("network down") }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val manager = OtaConfigManager(client, "https://example.com/ota/config.json")
        val result = manager.fetchNow()
        assertEquals(OtaConfig.DEFAULT, result)
    }
}
