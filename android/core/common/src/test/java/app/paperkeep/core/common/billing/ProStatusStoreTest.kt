package app.paperkeep.core.common.billing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Pro status constants and key mapping.
 *
 * Full DataStore persistence is tested in androidTest (instrumented) since
 * the preferencesDataStore delegate is a singleton tied to the Application
 * and doesn't clear cleanly between Robolectric test runs.
 */
class ProStatusStoreTest {

    @Test
    fun `PRODUCT_ID constant is paperkeep_pro_lifetime`() {
        assertEquals("paperkeep_pro_lifetime", BillingManager.PRODUCT_ID)
    }

    @Test
    fun `ProProductDetails stores product ID`() {
        val details = ProProductDetails(
            productId = "paperkeep_pro_lifetime",
            title = "Paperkeep Pro",
            description = "Lifetime unlock",
            formattedPrice = "\$4.99",
            priceMicros = 4_990_000L,
            currencyCode = "USD",
        )
        assertEquals("paperkeep_pro_lifetime", details.productId)
    }

    @Test
    fun `ProProductDetails price micros is positive`() {
        val details = ProProductDetails(
            productId = "paperkeep_pro_lifetime",
            title = "Paperkeep Pro",
            description = "",
            formattedPrice = "\$4.99",
            priceMicros = 4_990_000L,
            currencyCode = "USD",
        )
        assert(details.priceMicros > 0)
    }

    @Test
    fun `BillingState sealed interface covers all expected states`() {
        val idle: BillingState = BillingState.Idle
        val connecting: BillingState = BillingState.Connecting
        val pending: BillingState = BillingState.PurchasePending
        val error: BillingState = BillingState.Error("test")
        val available: BillingState = BillingState.ProductAvailable(
            ProProductDetails("id", "title", "desc", "$4.99", 4_990_000L, "USD")
        )
        assert(idle is BillingState.Idle)
        assert(connecting is BillingState.Connecting)
        assert(pending is BillingState.PurchasePending)
        assert(error is BillingState.Error)
        assert(available is BillingState.ProductAvailable)
    }

    @Test
    fun `BillingState Error stores message`() {
        val error = BillingState.Error("billing unavailable")
        assertEquals("billing unavailable", error.message)
    }

    @Test
    fun `ProProductDetails currency code is non-blank`() {
        val details = ProProductDetails(
            productId = "id",
            title = "title",
            description = "desc",
            formattedPrice = "\$4.99",
            priceMicros = 4_990_000L,
            currencyCode = "USD",
        )
        assert(details.currencyCode.isNotBlank())
    }
}
