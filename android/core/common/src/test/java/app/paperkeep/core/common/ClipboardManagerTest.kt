package app.paperkeep.core.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [PaperkeepClipboardManager] — P2.15 clipboard hygiene.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClipboardManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var systemClipboard: ClipboardManager
    private lateinit var manager: PaperkeepClipboardManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager = PaperkeepClipboardManager(context)
    }

    @After
    fun tearDown() {
        manager.cancelAutoClear()
        Dispatchers.resetMain()
    }

    // ── copyOcrText ───────────────────────────────────────────────────────────

    @Test
    fun `copyOcrText places text in system clipboard`() = runTest {
        manager.copyOcrText("Invoice total: $123.45")

        val clip = systemClipboard.primaryClip
        assertNotNull(clip)
        assertEquals("Invoice total: \$123.45", clip!!.getItemAt(0).text.toString())
    }

    @Test
    fun `copyOcrText uses provided label`() = runTest {
        manager.copyOcrText("Hello", label = "My label")
        val desc = systemClipboard.primaryClip?.description
        assertEquals("My label", desc?.label?.toString())
    }

    @Test
    fun `copyOcrText API 33+ sets EXTRA_IS_SENSITIVE`() = runTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@runTest

        manager.copyOcrText("Sensitive text")

        val extras = systemClipboard.primaryClip?.description?.extras
        assertNotNull("Extras bundle must be set on API 33+", extras)
        val isSensitive = extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
        assertEquals("EXTRA_IS_SENSITIVE must be true", true, isSensitive)
    }

    // ── auto-clear ────────────────────────────────────────────────────────────

    @Test
    fun `clipboard is cleared after AUTO_CLEAR_DELAY_MS`() = runTest {
        manager.copyOcrText("auto-clear test")

        // Advance past the 60-second delay
        advanceTimeBy(PaperkeepClipboardManager.AUTO_CLEAR_DELAY_MS + 1000)

        val clip = systemClipboard.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString() ?: ""
        assertNotEquals(
            "Clipboard should be cleared after auto-clear delay",
            "auto-clear test",
            text,
        )
    }

    @Test
    fun `clipboard is NOT cleared before AUTO_CLEAR_DELAY_MS`() = runTest {
        manager.copyOcrText("keep this")

        // Advance to just before the delay
        advanceTimeBy(PaperkeepClipboardManager.AUTO_CLEAR_DELAY_MS - 1000)

        val text = systemClipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("Clipboard should still hold text before delay", "keep this", text)
    }

    @Test
    fun `cancelAutoClear prevents auto-clear from running`() = runTest {
        manager.copyOcrText("keep forever")
        manager.cancelAutoClear()

        advanceTimeBy(PaperkeepClipboardManager.AUTO_CLEAR_DELAY_MS + 5000)

        val text = systemClipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("Text should remain after cancel", "keep forever", text)
    }

    // ── clearIfOurs ───────────────────────────────────────────────────────────

    @Test
    fun `clearIfOurs clears clipboard when text matches`() = runTest {
        manager.copyOcrText("my text")
        manager.clearIfOurs("my text")

        val text = systemClipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertNotEquals("Clipboard should be cleared", "my text", text)
    }

    @Test
    fun `clearIfOurs does NOT clear when text differs`() = runTest {
        // Set some other content in the clipboard (simulates another app)
        val otherClip = ClipData.newPlainText("other", "other app content")
        systemClipboard.setPrimaryClip(otherClip)

        manager.clearIfOurs("my text")

        val text = systemClipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("Other app content should not be cleared", "other app content", text)
    }

    // ── Second copyOcrText cancels first auto-clear ────────────────────────────

    @Test
    fun `second copyOcrText resets the auto-clear timer`() = runTest {
        manager.copyOcrText("first")
        advanceTimeBy(30_000) // halfway through first timer

        manager.copyOcrText("second") // resets timer
        advanceTimeBy(30_000) // halfway through second timer — should NOT clear yet

        val text = systemClipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("Second copy should still be present", "second", text)

        advanceTimeBy(31_000) // now past second timer
        val textAfter = systemClipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        assertNotEquals("Second copy should now be cleared", "second", textAfter)
    }

    // ── AUTO_CLEAR_DELAY_MS constant ─────────────────────────────────────────

    @Test
    fun `AUTO_CLEAR_DELAY_MS is 60 seconds`() {
        assertEquals(60_000L, PaperkeepClipboardManager.AUTO_CLEAR_DELAY_MS)
    }
}
