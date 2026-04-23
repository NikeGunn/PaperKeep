package app.paperkeep.core.ads

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages AdMob SDK lifecycle (3B.10).
 *
 * The SDK is lazily initialised — **never** in Application.onCreate().
 * Call [initialize] before any ad load, e.g. on first user interaction.
 *
 * In Phase 3B this is a stub. The real Google Mobile Ads SDK will be wired
 * in Phase 5 when the Play Store submission workflow requires a real App ID.
 */
@Singleton
class AdMobManager @Inject constructor() {

    /** Whether [initialize] has been called. Exposed for testing. */
    @Volatile
    var initialized: Boolean = false
        private set

    /**
     * Lazily initialise the AdMob SDK.
     *
     * Safe to call multiple times — second call is a no-op.
     * Must be called from the main thread in production (MobileAds.initialize
     * contract), but the stub has no such restriction.
     */
    fun initialize(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    // Phase 3B stub — real SDK init:
                    // MobileAds.initialize(context) { }
                    initialized = true
                }
            }
        }
    }

    companion object {
        // Banner ads are explicitly NOT supported.
        const val BANNER_ADS_SUPPORTED = false
    }
}
