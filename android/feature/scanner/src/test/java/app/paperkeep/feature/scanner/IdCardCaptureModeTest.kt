package app.paperkeep.feature.scanner

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `front and back capture produces single composite page`() {
        val front = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        val back  = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)

        mode.captureFront(front)
        mode.captureBack(back)

        val composite = mode.buildComposite()
        assertNotNull("Composite page must not be null", composite.page)
    }

    @Test
    fun `composite page uses A4 dimensions (595 x 842)`() {
        val front = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        val back  = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)

        mode.captureFront(front)
        mode.captureBack(back)

        val composite = mode.buildComposite()
        assertEquals("A4 width (595)", IdCardCaptureMode.A4_W, composite.page.width)
        assertEquals("A4 height (842)", IdCardCaptureMode.A4_H, composite.page.height)
    }

    @Test(expected = IllegalStateException::class)
    fun `buildComposite throws if front not captured`() {
        mode.captureBack(Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888))
        mode.buildComposite()
    }

    @Test(expected = IllegalStateException::class)
    fun `buildComposite throws if back not captured`() {
        mode.captureFront(Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888))
        mode.buildComposite()
    }
}
