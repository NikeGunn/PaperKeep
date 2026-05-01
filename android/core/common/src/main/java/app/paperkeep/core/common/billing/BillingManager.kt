package app.paperkeep.core.common.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Play Billing implementation for Paperkeep Pro (P5.2).
 *
 * Product: [PRODUCT_ID] ("paperkeep_pro_lifetime"), one-time $4.99 purchase.
 *
 * Flow:
 *  1. [initialize] → connects BillingClient → queries product details.
 *  2. [launchPurchaseFlow] → opens Play purchase sheet.
 *  3. [PurchasesUpdatedListener] receives result → verifies + acknowledges → persists isPro.
 *
 * Security:
 *  - Purchase token stored in DataStore via [ProStatusStore].
 *  - Pro Integrity API gate enforced externally by [IntegrityGate].
 *  - Rooted devices are blocked at [IntegrityGate]; we do NOT silently take money.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proStatusStore: ProStatusStore,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    /** Emits true when the user owns Pro. Backed by encrypted DataStore. */
    val isPro: Flow<Boolean> get() = proStatusStore.isPro

    private var billingClient: BillingClient? = null
    private var queriedProductDetails: ProductDetails? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Connect to Play Billing and query product details.
     * Safe to call multiple times — no-op if already connecting/connected.
     */
    fun initialize() {
        if (_billingState.value !is BillingState.Idle) return
        _billingState.value = BillingState.Connecting

        val client = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        billingClient = client

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { queryProductAndExistingPurchases(client) }
                } else {
                    _billingState.value = BillingState.Error("Setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingState.value = BillingState.Idle
                billingClient = null
            }
        })
    }

    /** Disconnect and reset. Safe to call any time. */
    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
        _billingState.value = BillingState.Idle
    }

    /** Reset to [BillingState.Idle] — used in tests and on disconnect. */
    fun reset() {
        disconnect()
    }

    // ── Purchase flow ─────────────────────────────────────────────────────────

    /**
     * Launch the Play purchase sheet for [PRODUCT_ID].
     * Requires the state to be [BillingState.ProductAvailable].
     */
    fun launchPurchaseFlow(activity: Activity) {
        val details = queriedProductDetails ?: return
        val client = billingClient ?: return
        if (_billingState.value !is BillingState.ProductAvailable) return

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        _billingState.value = BillingState.PurchasePending
        client.launchBillingFlow(activity, params)
    }

    // ── PurchasesUpdatedListener ───────────────────────────────────────────────

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingState.value = queriedProductDetails?.let {
                    BillingState.ProductAvailable(it.toProProductDetails())
                } ?: BillingState.Idle
            }
            else -> {
                _billingState.value = BillingState.Error(result.debugMessage)
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private suspend fun queryProductAndExistingPurchases(client: BillingClient) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val details = result.productDetailsList?.firstOrNull()
            if (details != null) {
                queriedProductDetails = details
                _billingState.value = BillingState.ProductAvailable(details.toProProductDetails())
            } else {
                _billingState.value = BillingState.Error("Product not found in Play Console")
            }
        } else {
            _billingState.value = BillingState.Error(result.billingResult.debugMessage)
        }

        restoreExistingPurchases(client)
    }

    private suspend fun restoreExistingPurchases(client: BillingClient) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = client.queryPurchasesAsync(params)
        result.purchasesList
            .filter { it.products.contains(PRODUCT_ID) }
            .forEach { handlePurchase(it) }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        scope.launch {
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                val ackResult = billingClient?.acknowledgePurchase(ackParams)
                if (ackResult?.responseCode != BillingClient.BillingResponseCode.OK) return@launch
            }
            proStatusStore.setProStatus(isPro = true, purchaseToken = purchase.purchaseToken)
        }
    }

    companion object {
        /** The single Pro IAP product ID on the Play Store. */
        const val PRODUCT_ID = "paperkeep_pro_lifetime"
    }
}

// ── State machine ────────────────────────────────────────────────────────────

sealed interface BillingState {
    data object Idle : BillingState
    data object Connecting : BillingState
    data class ProductAvailable(val product: ProProductDetails) : BillingState
    data object PurchasePending : BillingState
    data class Error(val message: String) : BillingState
}

/** Snapshot of product details shown in the Pro upgrade UI. */
data class ProProductDetails(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
)

private fun ProductDetails.toProProductDetails() = ProProductDetails(
    productId = productId,
    title = title,
    description = description,
    formattedPrice = oneTimePurchaseOfferDetails?.formattedPrice ?: "\$4.99",
    priceMicros = oneTimePurchaseOfferDetails?.priceAmountMicros ?: 4_990_000L,
    currencyCode = oneTimePurchaseOfferDetails?.priceCurrencyCode ?: "USD",
)
