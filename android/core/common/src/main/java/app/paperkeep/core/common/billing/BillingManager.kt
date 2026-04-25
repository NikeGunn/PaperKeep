package app.paperkeep.core.common.billing

import android.app.Activity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Billing shelf for Paperkeep Pro (P3.11).
 *
 * Wires BillingClient and queries ONE product: [PRODUCT_ID].
 * Purchase flow is **stubbed** — real unlock ships in Phase 5.
 * Unlock logic is intentionally disabled here; [isPro] always stays false
 * until Phase 5 activates it.
 *
 * Phase 5 will:
 *  1. Replace the stub with a real BillingClient.initialize() call.
 *  2. Verify the purchase token on-device with Play Integrity before setting [isPro].
 *  3. Persist isPro state in encrypted DataStore.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    /** Whether the user owns Pro. Always false until Phase 5 enables it. */
    val isPro: Boolean get() = false

    /**
     * Initialise the billing connection and query product details.
     *
     * Phase 3 stub: transitions to [BillingState.ProductAvailable] immediately
     * with a synthetic [ProProductDetails]. A real BillingClient setup ships
     * in Phase 5.
     */
    fun initialize() {
        if (_billingState.value !is BillingState.Idle) return
        _billingState.value = BillingState.Connecting
        // Stub: simulate a successful connection + product query
        _billingState.value = BillingState.ProductAvailable(
            product = ProProductDetails(
                productId   = PRODUCT_ID,
                title       = "Paperkeep Pro",
                description = "Lifetime Pro: remove ads, unlimited batch export, AI summariser.",
                formattedPrice = "\$4.99",
                priceMicros = 4_990_000L,
                currencyCode = "USD",
            ),
        )
    }

    /**
     * Launch the purchase flow for [PRODUCT_ID].
     *
     * Phase 3 stub: immediately transitions to [BillingState.PurchasePending]
     * without opening any UI (purchase logic is disabled until Phase 5).
     *
     * @param activity The foreground Activity required by BillingClient.
     */
    fun launchPurchaseFlow(activity: Activity) {
        val current = _billingState.value
        if (current !is BillingState.ProductAvailable) return
        // Stub: show pending, but do NOT unlock Pro (Phase 5 job)
        _billingState.value = BillingState.PurchasePending
    }

    /** Reset to [BillingState.Idle] — used in tests and on disconnect. */
    fun reset() {
        _billingState.value = BillingState.Idle
    }

    companion object {
        /** The single Pro IAP product ID on the Play Store. */
        const val PRODUCT_ID = "paperkeep_pro_lifetime"
    }
}

// ── State machine ────────────────────────────────────────────────────────────

sealed interface BillingState {
    /** Not yet connected to Play Billing. */
    data object Idle : BillingState

    /** BillingClient connection in progress. */
    data object Connecting : BillingState

    /** Product queried and available for purchase. */
    data class ProductAvailable(val product: ProProductDetails) : BillingState

    /** Purchase flow launched — awaiting user action. Unlock disabled until Phase 5. */
    data object PurchasePending : BillingState

    /** A billing error occurred. [message] is for developer logging only. */
    data class Error(val message: String) : BillingState
}

/**
 * Synthetic product details (replaces real [ProductDetails] in Phase 3 stub).
 */
data class ProProductDetails(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
)
