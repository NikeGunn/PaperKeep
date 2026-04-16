package com.scanvault.feature.scanner

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class IdCardCaptureModeTest {

    private lateinit var mode: IdCardCaptureMode

    @Before
    fun setUp() {
        mode = IdCardCaptureMode()
    }

    @Test
    fun `front and back capture produces two pages in composite document`() {
        val front = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        val back = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)

        mode.captureFront(front)
        mode.captureBack(back)

        val composite = mode.buildComposite()
        assertEquals("Composite must have exactly 2 pages", 2, composite.pages.size)
    }

    @Test
    fun `composite pages use A4 dimensions (595 x 842)`() {
        val front = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        val back = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)

        mode.captureFront(front)
        mode.captureBack(back)

        val composite = mode.buildComposite()

        composite.pages.forEachIndexed { index, page ->
            assertEquals("Page $index width should be 595pt (A4)", 595, page.width)
            assertEquals("Page $index height should be 842pt (A4)", 842, page.height)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `buildComposite throws if front not captured`() {
        val back = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        mode.captureBack(back)
        mode.buildComposite()
    }

    @Test(expected = IllegalStateException::class)
    fun `buildComposite throws if back not captured`() {
        val front = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        mode.captureFront(front)
        mode.buildComposite()
    }
}
