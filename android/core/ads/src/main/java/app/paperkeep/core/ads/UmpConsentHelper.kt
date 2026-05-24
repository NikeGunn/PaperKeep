package app.paperkeep.core.ads

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UMP (User Messaging Platform) consent helper for EU locales (3B.10).
 *
 * In Phase 3B this is a stub. The real Google UMP SDK will be integrated
 * in Phase 5. For now [requestConsentIfRequired] is a no-op that always
 * reports consent as not required (non-EU locale assumed in dev builds).
 */
@Singleton
class UmpConsentHelper @Inject constructor() {

    /** Whether consent has been gathered (or skipped for non-EU locales). */
    var consentGathered: Boolean = false
        private set

    /**
     * Request consent form if required by the user's locale.
     *
     * Phase 3B stub — always calls [onResult] with `ConsentStatus.NOT_REQUIRED`.
     */
    // context is part of the production contract (UMP requires an Activity/Context);
    // the Phase 3B stub doesn't use it yet — wired in Phase 5.
    @Suppress("UnusedParameter")
    fun requestConsentIfRequired(
        context: Context,
        onResult: (status: ConsentStatus) -> Unit,
    ) {
        // Stub: skip consent in Phase 3B
        consentGathered = true
        onResult(ConsentStatus.NOT_REQUIRED)
    }
}

enum class ConsentStatus {
    NOT_REQUIRED,
    REQUIRED,
    OBTAINED,
    UNKNOWN,
}
