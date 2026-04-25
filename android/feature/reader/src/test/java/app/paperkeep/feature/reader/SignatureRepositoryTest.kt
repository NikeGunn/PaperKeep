package app.paperkeep.feature.reader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.paperkeep.feature.reader.signature.SignatureRepository
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SignatureRepositoryTest {

    private lateinit var repo: SignatureRepository

    @Before
    fun setUp() {
        repo = SignatureRepository(ApplicationProvider.getApplicationContext())
        repo.deleteAll()
    }

    @After
    fun tearDown() = repo.deleteAll()

    // ── MAX_SLOTS constant ─────────────────────────────────────────────────────

    @Test
    fun `MAX_SLOTS is 3`() = assertEquals(3, SignatureRepository.MAX_SLOTS)

    // ── Single slot CRUD ──────────────────────────────────────────────────────

    @Test
    fun `save then exists returns true`() {
        repo.save(png(), slot = 0)
        assertTrue(repo.exists(slot = 0))
    }

    @Test
    fun `save then load round-trip`() {
        val bytes = png()
        repo.save(bytes, slot = 1)
        assertArrayEquals(bytes, repo.load(slot = 1))
    }

    @Test
    fun `load returns null when slot empty`() = assertNull(repo.load(slot = 2))

    @Test
    fun `delete removes signature from slot`() {
        repo.save(png(), slot = 0)
        repo.delete(slot = 0)
        assertFalse(repo.exists(slot = 0))
    }

    // ── Multi-slot ────────────────────────────────────────────────────────────

    @Test
    fun `all three slots are independent`() {
        val b0 = png(Color.RED)
        val b1 = png(Color.GREEN)
        val b2 = png(Color.BLUE)
        repo.save(b0, 0); repo.save(b1, 1); repo.save(b2, 2)
        assertArrayEquals(b0, repo.load(0))
        assertArrayEquals(b1, repo.load(1))
        assertArrayEquals(b2, repo.load(2))
    }

    @Test
    fun `count reflects occupied slots`() {
        assertEquals(0, repo.count())
        repo.save(png(), 0)
        assertEquals(1, repo.count())
        repo.save(png(), 1)
        assertEquals(2, repo.count())
    }

    @Test
    fun `loadAll returns all occupied slots in order`() {
        repo.save(png(Color.RED),   0)
        repo.save(png(Color.BLUE),  2)
        val all = repo.loadAll()
        assertEquals(2, all.size)
        assertEquals(0, all[0].slot)
        assertEquals(2, all[1].slot)
    }

    @Test
    fun `deleteAll empties all slots`() {
        repeat(3) { repo.save(png(), it) }
        repo.deleteAll()
        assertEquals(0, repo.count())
    }

    // ── Error paths ───────────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `slot -1 is rejected`() = repo.save(png(), -1)

    @Test(expected = IllegalArgumentException::class)
    fun `slot MAX_SLOTS is rejected`() = repo.save(png(), SignatureRepository.MAX_SLOTS)

    @Test(expected = IllegalArgumentException::class)
    fun `empty bytes are rejected`() = repo.save(ByteArray(0), 0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun png(color: Int = Color.BLACK): ByteArray {
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
