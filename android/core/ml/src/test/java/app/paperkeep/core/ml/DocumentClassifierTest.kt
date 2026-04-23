package app.paperkeep.core.ml

import android.graphics.Bitmap
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
class DocumentClassifierTest {

    private lateinit var classifier: DocumentClassifier

    @Before
    fun setUp() {
        classifier = DocumentClassifier()
    }

    @Test
    fun `classify returns DOCUMENT for generic landscape bitmap`() {
        // 800x600 — wider than tall, generic document
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val result = classifier.classify(bitmap)
        assertEquals(DocumentType.DOCUMENT, result.type)
    }

    @Test
    fun `classify returns confidence score in 0_0 to 1_0 range`() {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val result = classifier.classify(bitmap)
        assertTrue("confidence must be >= 0.0", result.confidence >= 0f)
        assertTrue("confidence must be <= 1.0", result.confidence <= 1f)
    }

    @Test
    fun `classify handles tiny 1x1 bitmap without crashing`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val result = classifier.classify(bitmap)
        // Must not throw — result can be any valid type
        assertTrue(result.confidence in 0f..1f)
    }

    @Test
    fun `model is not loaded before first classify call`() {
        val freshClassifier = DocumentClassifier()
        assertFalse("model should not be loaded before first call", freshClassifier.modelLoaded)
    }

    @Test
    fun `model is loaded after first classify call`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        assertFalse(classifier.modelLoaded)
        classifier.classify(bitmap)
        assertTrue("model must be loaded after first classify", classifier.modelLoaded)
    }

    @Test
    fun `second classify call does not reinitialise model`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        classifier.classify(bitmap) // first call — loads model
        assertTrue(classifier.modelLoaded)
        // second call — modelLoaded stays true, no side-effects
        classifier.classify(bitmap)
        assertTrue(classifier.modelLoaded)
    }

    @Test
    fun `ClassificationResult confidence out of range throws`() {
        try {
            ClassificationResult(type = DocumentType.UNKNOWN, confidence = 1.5f)
            assert(false) { "Expected IllegalArgumentException" }
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
