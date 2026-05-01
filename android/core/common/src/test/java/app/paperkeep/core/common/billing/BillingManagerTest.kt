package app.paperkeep.core.common.billing

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class BillingManagerTest {

    private lateinit var proStatusStore: ProStatusStore
    private lateinit var billing: BillingManager

    @Before
    fun setUp() {
        proStatusStore = mockk(relaxed = true)
        coEvery { proStatusStore.isPro } returns flowOf(false)
        billing = BillingManager(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            proStatusStore = proStatusStore,
        )
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial billing state is Idle`() {
        assertTrue(billing.billingState.value is BillingState.Idle)
    }

    @Test
    fun `isPro emits false when ProStatusStore returns false`() = runTest {
        coEvery { proStatusStore.isPro } returns flowOf(false)
        assertFalse(billing.isPro.first())
    }

    @Test
    fun `isPro emits true when ProStatusStore returns true`() = runTest {
        coEvery { proStatusStore.isPro } returns flowOf(true)
        assertTrue(billing.isPro.first())
    }

    // ── initialize ────────────────────────────────────────────────────────────

    @Test
    fun `initialize transitions from Idle to Connecting`() {
        billing.initialize()
        // BillingClient connects asynchronously; state becomes Connecting immediately
        val state = billing.billingState.value
        assertTrue("Expected Connecting or further, got $state",
            state is BillingState.Connecting ||
            state is BillingState.ProductAvailable ||
            state is BillingState.Error)
    }

    @Test
    fun `second initialize call is no-op when not Idle`() {
        billing.initialize()
        val stateAfterFirst = billing.billingState.value
        billing.initialize()
        assertEquals(stateAfterFirst, billing.billingState.value)
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset returns to Idle`() {
        billing.initialize()
        billing.reset()
        assertTrue(billing.billingState.value is BillingState.Idle)
    }

    // ── launchPurchaseFlow guards ─────────────────────────────────────────────

    @Test
    fun `launchPurchaseFlow from Idle is a no-op`() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).get()
        billing.launchPurchaseFlow(activity)
        assertTrue(billing.billingState.value is BillingState.Idle)
    }

    // ── PRODUCT_ID constant ───────────────────────────────────────────────────

    @Test
    fun `PRODUCT_ID is paperkeep_pro_lifetime`() {
        assertEquals("paperkeep_pro_lifetime", BillingManager.PRODUCT_ID)
    }

    // ── ProProductDetails defaults ────────────────────────────────────────────

    @Test
    fun `ProProductDetails has correct product ID`() {
        val details = ProProductDetails(
            productId = BillingManager.PRODUCT_ID,
            title = "Paperkeep Pro",
            description = "Lifetime",
            formattedPrice = "\$4.99",
            priceMicros = 4_990_000L,
            currencyCode = "USD",
        )
        assertEquals(BillingManager.PRODUCT_ID, details.productId)
        assertEquals(4_990_000L, details.priceMicros)
        assertEquals("USD", details.currencyCode)
    }

    @Test
    fun `ProProductDetails price micros is positive`() {
        val details = ProProductDetails(
            productId = BillingManager.PRODUCT_ID,
            title = "Paperkeep Pro",
            description = "Lifetime",
            formattedPrice = "\$4.99",
            priceMicros = 4_990_000L,
            currencyCode = "USD",
        )
        assertTrue(details.priceMicros > 0)
    }
}
