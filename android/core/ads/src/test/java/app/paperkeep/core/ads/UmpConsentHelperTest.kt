package app.paperkeep.core.ads

import androidx.test.core.app.ApplicationProvider
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
class UmpConsentHelperTest {

    private lateinit var helper: UmpConsentHelper

    @Before
    fun setUp() { helper = UmpConsentHelper() }

    @Test
    fun `consentGathered is false before any request`() {
        assertFalse(helper.consentGathered)
    }

    @Test
    fun `requestConsentIfRequired calls onResult with NOT_REQUIRED in stub`() {
        var received: ConsentStatus? = null
        helper.requestConsentIfRequired(ApplicationProvider.getApplicationContext()) {
            received = it
        }
        assertEquals(ConsentStatus.NOT_REQUIRED, received)
    }

    @Test
    fun `consentGathered is true after requestConsentIfRequired`() {
        helper.requestConsentIfRequired(ApplicationProvider.getApplicationContext()) {}
        assertTrue(helper.consentGathered)
    }

    @Test
    fun `ConsentStatus enum has all required values`() {
        val values = ConsentStatus.entries.map { it.name }.toSet()
        assertTrue(values.contains("NOT_REQUIRED"))
        assertTrue(values.contains("REQUIRED"))
        assertTrue(values.contains("OBTAINED"))
        assertTrue(values.contains("UNKNOWN"))
    }
}
