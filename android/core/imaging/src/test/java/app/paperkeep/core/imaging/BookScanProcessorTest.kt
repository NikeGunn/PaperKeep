package app.paperkeep.core.imaging

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class BookScanProcessorTest {

    @Test
    fun `split 1000x800 spread into two 500x800 pages`() {
        val spread = Bitmap.createBitmap(1000, 800, Bitmap.Config.ARGB_8888)
        val pages = BookScanProcessor.split(spread)

        assertEquals("Must return exactly 2 pages", 2, pages.size)
        assertEquals("Left page width should be 500", 500, pages[0].width)
        assertEquals("Left page height should be 800", 800, pages[0].height)
        assertEquals("Right page width should be 500", 500, pages[1].width)
        assertEquals("Right page height should be 800", 800, pages[1].height)
    }

    @Test
    fun `split produces exactly 2 entries`() {
        val spread = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val pages = BookScanProcessor.split(spread)
        assertEquals(2, pages.size)
    }

    @Test
    fun `dewarp output has positive width and height`() {
        val page = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val dewarped = BookScanProcessor.dewarp(page)
        assertTrue("width must be > 0", dewarped.width > 0)
        assertTrue("height must be > 0", dewarped.height > 0)
    }

    @Test
    fun `dewarp preserves dimensions`() {
        val page = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val dewarped = BookScanProcessor.dewarp(page)
        assertEquals(300, dewarped.width)
        assertEquals(400, dewarped.height)
    }
}
