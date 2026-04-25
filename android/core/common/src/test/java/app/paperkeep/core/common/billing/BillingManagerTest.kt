package app.paperkeep.core.common.billing

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class BillingManagerTest {

    private lateinit var billing: BillingManager

    @Before
    fun setUp() {
        billing = BillingManager(ApplicationProvider.getApplicationContext())
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is Idle`() {
        assertTrue(billing.billingState.value is BillingState.Idle)
    }

    @Test
    fun `isPro is always false until Phase 5`() {
        assertFalse("Pro must be disabled in Phase 3 stub", billing.isPro)
    }

    // ── initialize ────────────────────────────────────────────────────────────

    @Test
    fun `initialize transitions to ProductAvailable`() {
        billing.initialize()
        assertTrue(billing.billingState.value is BillingState.ProductAvailable)
    }

    @Test
    fun `initialize second call is no-op (idempotent)`() {
        billing.initialize()
        val stateAfterFirst = billing.billingState.value
        billing.initialize()
        assertEquals(stateAfterFirst, billing.billingState.value)
    }

    @Test
    fun `ProductAvailable contains correct product ID`() {
        billing.initialize()
        val state = billing.billingState.value as BillingState.ProductAvailable
        assertEquals(BillingManager.PRODUCT_ID, state.product.productId)
    }

    @Test
    fun `ProductAvailable has non-blank title`() {
        billing.initialize()
        val state = billing.billingState.value as BillingState.ProductAvailable
        assertTrue(state.product.title.isNotBlank())
    }

    @Test
    fun `ProductAvailable has non-blank formatted price`() {
        billing.initialize()
        val state = billing.billingState.value as BillingState.ProductAvailable
        assertTrue(state.product.formattedPrice.isNotBlank())
    }

    @Test
    fun `ProductAvailable has positive price micros`() {
        billing.initialize()
        val state = billing.billingState.value as BillingState.ProductAvailable
        assertTrue(state.product.priceMicros > 0)
    }

    // ── launchPurchaseFlow ────────────────────────────────────────────────────

    @Test
    fun `launchPurchaseFlow transitions to PurchasePending`() {
        billing.initialize()
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).get()
        billing.launchPurchaseFlow(activity)
        assertTrue(
            "After launch, state must be PurchasePending",
            billing.billingState.value is BillingState.PurchasePending,
        )
        assertFalse("isPro must remain false after purchase flow in Phase 3", billing.isPro)
    }

    @Test
    fun `launchPurchaseFlow from Idle is a no-op`() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).get()
        billing.launchPurchaseFlow(activity)
        assertTrue(billing.billingState.value is BillingState.Idle)
    }

    // ── PRODUCT_ID constant ───────────────────────────────────────────────────

    @Test
    fun `PRODUCT_ID is paperkeep_pro_lifetime`() {
        assertEquals("paperkeep_pro_lifetime", BillingManager.PRODUCT_ID)
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset returns to Idle from any state`() {
        billing.initialize()
        billing.reset()
        assertTrue(billing.billingState.value is BillingState.Idle)
    }
}
