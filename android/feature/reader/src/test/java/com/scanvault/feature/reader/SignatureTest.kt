package com.scanvault.feature.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.scanvault.feature.reader.signature.PlaceSignature
import com.scanvault.feature.reader.signature.SignatureRepository
import com.scanvault.feature.reader.signature.SignatureState
import com.scanvault.feature.reader.signature.SignatureStroke
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import androidx.compose.ui.geometry.Offset

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SignatureTest {

    private lateinit var repository: SignatureRepository

    @Before
    fun setUp() {
        repository = SignatureRepository(ApplicationProvider.getApplicationContext())
        repository.delete() // clean state between tests
    }

    // ── SignatureRepository ───────────────────────────────────────────────────

    @Test
    fun `save returns storable non-empty bytes — exists() returns true`() {
        val bytes = pngBytes()
        repository.save(bytes)
        assertTrue("signature should exist after save", repository.exists())
    }

    @Test
    fun `load returns previously saved bytes (round-trip)`() {
        val bytes = pngBytes()
        repository.save(bytes)
        val loaded = repository.load()
        assertNotNull("loaded bytes must not be null", loaded)
        assertArrayEquals("loaded bytes must match saved bytes", bytes, loaded)
    }

    @Test
    fun `load returns null when nothing saved`() {
        val loaded = repository.load()
        assertTrue("load should return null when nothing saved", loaded == null)
    }

    @Test
    fun `delete removes signature`() {
        repository.save(pngBytes())
        assertTrue(repository.exists())
        repository.delete()
        assertFalse("signature should not exist after delete", repository.exists())
    }

    // ── PlaceSignature ────────────────────────────────────────────────────────

    @Test
    fun `PlaceSignature overlay returns bitmap with same dimensions as page`() {
        val page = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        page.eraseColor(Color.WHITE)
        val sigPng = pngBytes()
        val result = PlaceSignature.overlay(page, sigPng)
        assertTrue("result width must be 595", result.width == 595)
        assertTrue("result height must be 842", result.height == 842)
    }

    @Test
    fun `PlaceSignature overlay returns a mutable bitmap distinct from input`() {
        // Create a white page
        val page = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        page.eraseColor(Color.WHITE)

        // Create a red signature bitmap
        val sigBmp = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        sigBmp.eraseColor(Color.RED)
        val sigPng = bitmapToPng(sigBmp)

        val result = PlaceSignature.overlay(page, sigPng)

        // Result must be a distinct, mutable bitmap (not the input reference)
        assertTrue("result must be mutable so the signature can be composited", result.isMutable)
        assertTrue("result dimensions must match page width", result.width == page.width)
        assertTrue("result dimensions must match page height", result.height == page.height)
        // The result must be a different object (copy, not reference to original)
        assertTrue("result must not be the same object as page", result !== page)
    }

    // ── SignatureState ────────────────────────────────────────────────────────

    @Test
    fun `SignatureState toPngBytes returns null when empty`() {
        val state = SignatureState()
        assertTrue("toPngBytes must return null for empty state", state.toPngBytes() == null)
    }

    @Test
    fun `SignatureState toPngBytes returns non-null bytes after drawing`() {
        val state = SignatureState()
        state.addStroke(
            SignatureStroke(listOf(Offset(10f, 10f), Offset(50f, 50f), Offset(100f, 80f))),
        )
        val bytes = state.toPngBytes()
        assertNotNull("toPngBytes must return bytes after a stroke is added", bytes)
        assertTrue("PNG bytes must be non-empty", bytes!!.isNotEmpty())
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun pngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLACK)
        return bitmapToPng(bmp)
    }

    private fun bitmapToPng(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
