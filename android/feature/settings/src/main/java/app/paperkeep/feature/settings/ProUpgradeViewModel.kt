package app.paperkeep.feature.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.billing.BillingManager
import app.paperkeep.core.common.billing.BillingState
import app.paperkeep.core.security.IntegrityGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProUpgradeUiState(
    val isPro: Boolean = false,
    val formattedPrice: String = "$4.99",
    val isIapAvailable: Boolean = true,
    val canPurchase: Boolean = false,
    val isPurchasePending: Boolean = false,
)

@HiltViewModel
class ProUpgradeViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val integrityGate: IntegrityGate,
) : ViewModel() {

    val uiState: StateFlow<ProUpgradeUiState> = combine(
        billingManager.isPro,
        billingManager.billingState,
    ) { isPro, billingState ->
        val productDetails = (billingState as? BillingState.ProductAvailable)?.product
        ProUpgradeUiState(
            isPro = isPro,
            formattedPrice = productDetails?.formattedPrice ?: "$4.99",
            isIapAvailable = integrityGate.isProIapEnabled,
            canPurchase = billingState is BillingState.ProductAvailable && !isPro,
            isPurchasePending = billingState is BillingState.PurchasePending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ProUpgradeUiState(),
    )

    init {
        billingManager.initialize()
    }

    fun launchPurchase(activity: Activity) {
        billingManager.launchPurchaseFlow(activity)
    }

    fun restorePurchases() {
        billingManager.reset()
        billingManager.initialize()
    }
}
