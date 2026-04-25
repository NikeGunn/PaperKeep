package app.paperkeep.core.imaging

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ReceiptModeTest {

    @Test
    fun `process applies tall aspect ratio — output height is at least width x2`() {
        // 400 wide, 1500 tall — already satisfies 1:3
        val source = Bitmap.createBitmap(400, 1500, Bitmap.Config.ARGB_8888)
        val result = ReceiptMode.process(source)
        assertTrue(
            "output height (${result.height}) should be >= width (${result.width}) * 2",
            result.height >= result.width * 2,
        )
    }

    @Test
    fun `cropTallAspect on a 400x1500 bitmap returns same width, height capped at width x3`() {
        val source = Bitmap.createBitmap(400, 1500, Bitmap.Config.ARGB_8888)
        val result = ReceiptMode.cropTallAspect(source)
        assertEquals(400, result.width)
        assertEquals(1200, result.height) // min(1500, 400*3) = 1200
    }

    @Test
    fun `extractFields finds total from OCR text`() {
        val text = """
            Corner Cafe
            Coffee         ${'$'}3.50
            Muffin         ${'$'}2.75
            Total          ${'$'}6.25
        """.trimIndent()

        val data = ReceiptMode.extractFields(text)
        assertNotNull("total should be extracted", data.total)
        assertEquals("6.25", data.total)
    }

    @Test
    fun `extractFields finds merchant as first non-blank line`() {
        val text = """
            SuperMart
            Eggs x12     ${'$'}4.99
            Total        ${'$'}4.99
        """.trimIndent()

        val data = ReceiptMode.extractFields(text)
        assertEquals("SuperMart", data.merchant)
    }

    @Test
    fun `extractFields returns null total when no total pattern`() {
        val text = "No price here\nJust text"
        val data = ReceiptMode.extractFields(text)
        assertNull(data.total)
    }

    @Test
    fun `extractFields returns null merchant for empty text`() {
        val data = ReceiptMode.extractFields("")
        assertNull(data.merchant)
        assertNull(data.total)
    }

    @Test
    fun `total extracted with no currency symbol`() {
        val text = "Store\ntotal 15.99"
        val data = ReceiptMode.extractFields(text)
        assertNotNull(data.total)
        assertEquals("15.99", data.total)
    }

    // ── Date extraction ──────────────────────────────────────────────────────��─

    @Test
    fun `extractFields finds ISO date`() {
        val text = "Cafe\n2024-03-15\ntotal ${'$'}5.00"
        val data = ReceiptMode.extractFields(text)
        assertEquals("2024-03-15", data.date)
    }

    @Test
    fun `extractFields finds US slash date`() {
        val text = "Shop\n03/15/2024\ntotal ${'$'}10.00"
        val data = ReceiptMode.extractFields(text)
        assertNotNull(data.date)
    }

    @Test
    fun `extractFields finds written month date`() {
        val text = "Market\nMarch 15, 2024\ntotal ${'$'}8.50"
        val data = ReceiptMode.extractFields(text)
        assertNotNull("Written date should be found", data.date)
    }

    @Test
    fun `extractFields returns null date when no date present`() {
        val text = "Store\nNo date here\ntotal ${'$'}5.00"
        val data = ReceiptMode.extractFields(text)
        assertNull(data.date)
    }

    @Test
    fun `ReceiptData has all three fields populated`() {
        val text = "Starbucks\n2024-01-20\nCoffee ${'$'}4.50\ntotal ${'$'}4.50"
        val data = ReceiptMode.extractFields(text)
        assertNotNull(data.merchant)
        assertNotNull(data.total)
        assertNotNull(data.date)
    }
}
