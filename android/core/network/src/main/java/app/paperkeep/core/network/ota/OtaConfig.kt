package app.paperkeep.core.network.ota

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OtaConfig(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("force_upgrade") val forceUpgrade: ForceUpgrade = ForceUpgrade(),
    @SerialName("soft_upgrade") val softUpgrade: SoftUpgrade = SoftUpgrade(),
    @SerialName("kill_switches") val killSwitches: KillSwitches = KillSwitches(),
    @SerialName("rate_limits") val rateLimits: RateLimits = RateLimits(),
    @SerialName("maintenance") val maintenance: Maintenance = Maintenance(),
    @SerialName("feature_flags") val featureFlags: FeatureFlags = FeatureFlags(),
) {
    companion object {
        val DEFAULT = OtaConfig()
    }
}

@Serializable
data class ForceUpgrade(
    val enabled: Boolean = false,
    @SerialName("min_version_code") val minVersionCode: Int = 0,
    val message: String = "",
    @SerialName("store_url") val storeUrl: String = "",
)

@Serializable
data class SoftUpgrade(
    val enabled: Boolean = false,
    @SerialName("min_version_code") val minVersionCode: Int = 0,
    val message: String = "",
)

@Serializable
data class KillSwitches(
    @SerialName("ocr_enabled") val ocrEnabled: Boolean = true,
    @SerialName("cloud_sync_enabled") val cloudSyncEnabled: Boolean = true,
    @SerialName("ai_classify_enabled") val aiClassifyEnabled: Boolean = true,
    @SerialName("super_resolve_enabled") val superResolveEnabled: Boolean = true,
    @SerialName("admob_enabled") val admobEnabled: Boolean = true,
    @SerialName("backup_enabled") val backupEnabled: Boolean = true,
)

@Serializable
data class RateLimits(
    @SerialName("ocr_per_day") val ocrPerDay: Int = 100,
    @SerialName("ai_tasks_per_day") val aiTasksPerDay: Int = 20,
    @SerialName("super_resolve_per_day") val superResolvePerDay: Int = 10,
)

@Serializable
data class Maintenance(
    val active: Boolean = false,
    val message: String = "",
    @SerialName("ends_at") val endsAt: String? = null,
)

@Serializable
data class FeatureFlags(
    @SerialName("show_widget_promo") val showWidgetPromo: Boolean = true,
    @SerialName("show_qs_tile_promo") val showQsTilePromo: Boolean = true,
    @SerialName("new_onboarding_flow") val newOnboardingFlow: Boolean = false,
)
