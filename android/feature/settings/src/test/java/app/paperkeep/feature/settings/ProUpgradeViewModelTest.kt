package app.paperkeep.feature.settings

import app.paperkeep.core.common.billing.BillingManager
import app.paperkeep.core.common.billing.BillingState
import app.paperkeep.core.common.billing.ProProductDetails
import app.paperkeep.core.security.IntegrityGate
import app.paperkeep.core.security.IntegrityStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProUpgradeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var billingManager: BillingManager
    private lateinit var integrityGate: IntegrityGate
    private lateinit var billingStateFlow: MutableStateFlow<BillingState>
    private lateinit var isProFlow: MutableStateFlow<Boolean>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        billingManager = mockk(relaxed = true)
        integrityGate = mockk(relaxed = true)
        billingStateFlow = MutableStateFlow(BillingState.Idle)
        isProFlow = MutableStateFlow(false)

        every { billingManager.billingState } returns billingStateFlow
        every { billingManager.isPro } returns isProFlow
        every { integrityGate.isProIapEnabled } returns true
        every { integrityGate.currentStatus } returns IntegrityStatus()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ProUpgradeViewModel(billingManager, integrityGate)

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial isPro is false`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isPro)
    }

    @Test
    fun `initialize is called on creation`() {
        createViewModel()
        verify { billingManager.initialize() }
    }

    @Test
    fun `initial canPurchase is false when state is Idle`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canPurchase)
    }

    // ── Product available state ───────────────────────────────────────────────

    @Test
    fun `canPurchase is true when ProductAvailable and not pro`() = runTest {
        val vm = createViewModel()
        billingStateFlow.value = BillingState.ProductAvailable(
            ProProductDetails(
                productId = "paperkeep_pro_lifetime",
                title = "Paperkeep Pro",
                description = "Lifetime",
                formattedPrice = "\$4.99",
                priceMicros = 4_990_000L,
                currencyCode = "USD",
            )
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canPurchase)
    }

    @Test
    fun `formattedPrice shows product price when available`() = runTest {
        val vm = createViewModel()
        billingStateFlow.value = BillingState.ProductAvailable(
            ProProductDetails(
                productId = "paperkeep_pro_lifetime",
                title = "Paperkeep Pro",
                description = "Lifetime",
                formattedPrice = "€4,99",
                priceMicros = 4_990_000L,
                currencyCode = "EUR",
            )
        )
        advanceUntilIdle()
        assertEquals("€4,99", vm.uiState.value.formattedPrice)
    }

    // ── Pro owned state ───────────────────────────────────────────────────────

    @Test
    fun `canPurchase is false when user already owns Pro`() = runTest {
        isProFlow.value = true
        val vm = createViewModel()
        billingStateFlow.value = BillingState.ProductAvailable(
            ProProductDetails("paperkeep_pro_lifetime", "Pro", "", "\$4.99", 4_990_000L, "USD")
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isPro)
        assertFalse(vm.uiState.value.canPurchase)
    }

    // ── IAP unavailable ────────────────────────────────────────────────────────

    @Test
    fun `isIapAvailable is false when integrity gate blocks Pro`() = runTest {
        every { integrityGate.isProIapEnabled } returns false
        val vm = createViewModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isIapAvailable)
    }

    // ── Purchase pending ─────────────────────────────────────────────────────

    @Test
    fun `isPurchasePending is true when state is PurchasePending`() = runTest {
        val vm = createViewModel()
        billingStateFlow.value = BillingState.PurchasePending
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isPurchasePending)
    }

    // ── restorePurchases ─────────────────────────────────────────────────────

    @Test
    fun `restorePurchases calls reset then initialize`() = runTest {
        val vm = createViewModel()
        vm.restorePurchases()
        verify { billingManager.reset() }
        verify(atLeast = 2) { billingManager.initialize() }
    }
}
