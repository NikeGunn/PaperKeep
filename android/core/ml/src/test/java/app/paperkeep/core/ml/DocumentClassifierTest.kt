package app.paperkeep.core.ml

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class DocumentClassifierTest {

    private lateinit var classifier: DocumentClassifier

    @Before
    fun setUp() {
        classifier = DocumentClassifier()
    }

    // ── Lazy loading ──────────────────────────────────────────────────────────

    @Test
    fun `model not loaded before first classify`() {
        val fresh = DocumentClassifier()
        assertFalse(fresh.modelLoaded)
    }

    @Test
    fun `model loaded after first classify`() {
        classifier.classify(whiteBitmap(100, 100))
        assertTrue(classifier.modelLoaded)
    }

    @Test
    fun `second classify does not reinitialise model`() {
        val bmp = whiteBitmap(100, 100)
        classifier.classify(bmp)
        assertTrue(classifier.modelLoaded)
        classifier.classify(bmp)
        assertTrue(classifier.modelLoaded)
    }

    // ── ClassificationResult invariants ──────────────────────────────────────

    @Test
    fun `confidence always in 0_0 to 1_0 range`() {
        val bitmaps = listOf(
            whiteBitmap(100, 100),
            whiteBitmap(800, 600),
            whiteBitmap(200, 500),
            whiteBitmap(1, 1),
        )
        bitmaps.forEach { bmp ->
            val r = classifier.classify(bmp)
            assertTrue("confidence must be >= 0 for $bmp", r.confidence >= 0f)
            assertTrue("confidence must be <= 1 for $bmp", r.confidence <= 1f)
        }
    }

    @Test
    fun `ClassificationResult rejects confidence above 1`() {
        try {
            ClassificationResult(DocumentType.DOCUMENT, 1.5f)
            assertTrue("Expected IllegalArgumentException", false)
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun `ClassificationResult rejects negative confidence`() {
        try {
            ClassificationResult(DocumentType.DOCUMENT, -0.1f)
            assertTrue("Expected IllegalArgumentException", false)
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    // ── Aspect-ratio heuristics ────────────────────────────────────────────────

    @Test
    fun `very tall narrow bitmap classified as RECEIPT`() {
        // aspect ratio = 700/200 = 3.5 >= 2.5 → simple receipt path (no edge density required)
        val bmp = linedBitmap(width = 200, height = 700, lineSpacing = 10, lineColor = Color.BLACK)
        val result = classifier.classify(bmp)
        assertEquals(DocumentType.RECEIPT, result.type)
    }

    @Test
    fun `landscape bitmap classified as ID_CARD`() {
        // aspect ratio = 540/856 ≈ 0.63 < 0.75 → ID card heuristic (no edge threshold)
        val bmp = coloredBitmap(width = 856, height = 540, color = Color.LTGRAY)
        val result = classifier.classify(bmp)
        assertEquals(DocumentType.ID_CARD, result.type)
    }

    @Test
    fun `1x1 bitmap does not throw`() {
        val bmp = whiteBitmap(1, 1)
        val result = classifier.classify(bmp)
        assertNotNull(result)
        assertTrue(result.confidence in 0f..1f)
    }

    @Test
    fun `recycled bitmap returns fallback gracefully`() {
        val bmp = whiteBitmap(100, 100)
        bmp.recycle()
        val result = classifier.classify(bmp)
        // Should not throw; result is the fallback
        assertNotNull(result)
    }

    // ── DocumentType enum ─────────────────────────────────────────────────────

    @Test
    fun `all document types have non-blank keys`() {
        DocumentType.entries.forEach { type ->
            assertTrue("key must not be blank for $type", type.key.isNotBlank())
        }
    }

    @Test
    fun `all document types have non-blank labels`() {
        DocumentType.entries.forEach { type ->
            assertTrue("label must not be blank for $type", type.label.isNotBlank())
        }
    }

    @Test
    fun `all document types have non-blank emojis`() {
        DocumentType.entries.forEach { type ->
            assertTrue("emoji must not be blank for $type", type.emoji.isNotBlank())
        }
    }

    // ── ImageFeatures extraction ───────────────────────────────────────────────

    @Test
    fun `white bitmap has high luminance`() {
        val bmp = whiteBitmap(200, 200)
        val features = extractFeaturesReflective(bmp)
        assertTrue("white bitmap should have high luminance", features.meanLuminance > 0.9f)
    }

    @Test
    fun `black bitmap has low luminance`() {
        val bmp = coloredBitmap(200, 200, Color.BLACK)
        val features = extractFeaturesReflective(bmp)
        assertTrue("black bitmap should have low luminance", features.meanLuminance < 0.1f)
    }

    @Test
    fun `portrait bitmap has aspect ratio greater than 1`() {
        val bmp = whiteBitmap(300, 600) // h > w
        val features = extractFeaturesReflective(bmp)
        assertTrue("portrait should have aspect > 1", features.aspectRatio > 1f)
    }

    @Test
    fun `landscape bitmap has aspect ratio less than 1`() {
        val bmp = whiteBitmap(600, 300) // w > h
        val features = extractFeaturesReflective(bmp)
        assertTrue("landscape should have aspect < 1", features.aspectRatio < 1f)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun whiteBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(Color.WHITE)
        }

    private fun coloredBitmap(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(color)
        }

    private fun linedBitmap(width: Int, height: Int, lineSpacing: Int, lineColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        // Draw horizontal lines by setting pixels directly — Canvas is a no-op in Robolectric
        var y = lineSpacing
        while (y < height) {
            for (x in 0 until width) bmp.setPixel(x, y, lineColor)
            y += lineSpacing
        }
        return bmp
    }

    private fun extractFeaturesReflective(bitmap: Bitmap): ImageFeatures =
        classifier.extractFeatures(bitmap)
}
