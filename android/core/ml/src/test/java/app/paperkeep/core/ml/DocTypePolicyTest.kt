package app.paperkeep.core.ml

import app.paperkeep.core.imaging.ImageFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocTypePolicyTest {

    // ── forType lookups ───────────────────────────────────────────────────────

    @Test
    fun `forType RECEIPT returns BLACK_AND_WHITE filter`() {
        val policy = DocTypePolicies.forType(DocumentType.RECEIPT)
        assertEquals(ImageFilter.BLACK_AND_WHITE, policy.recommendedFilter)
    }

    @Test
    fun `forType ID_CARD returns MAGIC_COLOR filter`() {
        val policy = DocTypePolicies.forType(DocumentType.ID_CARD)
        assertEquals(ImageFilter.MAGIC_COLOR, policy.recommendedFilter)
    }

    @Test
    fun `forType WHITEBOARD returns AUTO filter`() {
        val policy = DocTypePolicies.forType(DocumentType.WHITEBOARD)
        assertEquals(ImageFilter.AUTO, policy.recommendedFilter)
    }

    @Test
    fun `forType DOCUMENT returns AUTO filter`() {
        val policy = DocTypePolicies.forType(DocumentType.DOCUMENT)
        assertEquals(ImageFilter.AUTO, policy.recommendedFilter)
    }

    @Test
    fun `forType BOOK_PAGE returns GRAYSCALE filter`() {
        val policy = DocTypePolicies.forType(DocumentType.BOOK_PAGE)
        assertEquals(ImageFilter.GRAYSCALE, policy.recommendedFilter)
    }

    @Test
    fun `forType UNKNOWN returns ORIGINAL filter`() {
        val policy = DocTypePolicies.forType(DocumentType.UNKNOWN)
        assertEquals(ImageFilter.ORIGINAL, policy.recommendedFilter)
    }

    // ── All types have a policy ───────────────────────────────────────────────

    @Test
    fun `all DocumentTypes have a policy with non-null filter`() {
        DocumentType.entries.forEach { type ->
            val policy = DocTypePolicies.forType(type)
            assertNotNull("Policy must exist for $type", policy)
            assertNotNull("recommendedFilter must not be null for $type", policy.recommendedFilter)
        }
    }

    @Test
    fun `all policies have non-blank recommendation labels`() {
        DocumentType.entries.forEach { type ->
            val policy = DocTypePolicies.forType(type)
            assertTrue("Label must not be blank for $type", policy.recommendationLabel.isNotBlank())
        }
    }

    // ── Aspect ratios ─────────────────────────────────────────────────────────

    @Test
    fun `A4_ASPECT is approximately 1_414`() {
        assertTrue("A4 aspect ~1.414", DocTypePolicies.A4_ASPECT in 1.40f..1.43f)
    }

    @Test
    fun `RECEIPT_ASPECT is 3_0`() {
        assertEquals(3.0f, DocTypePolicies.RECEIPT_ASPECT, 0.001f)
    }

    @Test
    fun `ID_CARD_ASPECT is less than 1 (landscape)`() {
        assertTrue("ID card is landscape", DocTypePolicies.ID_CARD_ASPECT < 1f)
    }

    @Test
    fun `WHITEBOARD_ASPECT is less than 1 (landscape)`() {
        assertTrue("Whiteboard is landscape", DocTypePolicies.WHITEBOARD_ASPECT < 1f)
    }

    @Test
    fun `BOOK_ASPECT is A5 portrait approximately 1_42`() {
        assertTrue("Book A5 aspect ~1.42", DocTypePolicies.BOOK_ASPECT in 1.40f..1.45f)
    }

    // ── recommendedFilter convenience ─────────────────────────────────────────

    @Test
    fun `recommendedFilter matches forType filter`() {
        DocumentType.entries.forEach { type ->
            val direct = DocTypePolicies.recommendedFilter(type)
            val viaForType = DocTypePolicies.forType(type).recommendedFilter
            assertEquals("recommendedFilter must match forType for $type", viaForType, direct)
        }
    }
}
