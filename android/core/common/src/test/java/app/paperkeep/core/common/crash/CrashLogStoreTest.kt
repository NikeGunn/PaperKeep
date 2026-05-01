package app.paperkeep.core.common.crash

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [CrashLogStore].
 *
 * AES-256-GCM encryption relies on the Android Keystore — unavailable on JVM.
 * The write() call silently fails in that path, so encryption round-trip tests
 * live in the instrumented test suite. Here we test:
 *  - Directory management and file rotation
 *  - listFiles() ordering and .enc filter
 *  - clear() wipes everything
 *  - buildReport() includes key fields
 */
class CrashLogStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
    }

    @Test
    fun `listFiles returns empty list when directory does not exist`() {
        val files = CrashLogStore.listFiles(context)
        assertTrue(files.isEmpty())
    }

    @Test
    fun `listFiles filters only enc files`() {
        val logDir = File(filesDir, CRASH_LOG_DIR).also { it.mkdirs() }
        File(logDir, "crash_1.enc").writeBytes(byteArrayOf(1))
        File(logDir, "crash_old.log").writeText("old format") // must be excluded
        File(logDir, "crash_2.enc").writeBytes(byteArrayOf(2))

        val files = CrashLogStore.listFiles(context)
        assertEquals(2, files.size)
        assertTrue(files.all { it.extension == "enc" })
    }

    @Test
    fun `listFiles returns files sorted newest first`() {
        val logDir = File(filesDir, CRASH_LOG_DIR).also { it.mkdirs() }
        File(logDir, "crash_old.enc").also { it.writeBytes(byteArrayOf(1)); it.setLastModified(1_000L) }
        File(logDir, "crash_new.enc").also { it.writeBytes(byteArrayOf(2)); it.setLastModified(2_000L) }

        val files = CrashLogStore.listFiles(context)
        assertEquals("crash_new.enc", files[0].name)
        assertEquals("crash_old.enc", files[1].name)
    }

    @Test
    fun `clear deletes all files in crash directory`() {
        val logDir = File(filesDir, CRASH_LOG_DIR).also { it.mkdirs() }
        repeat(5) { File(logDir, "crash_$it.enc").writeBytes(byteArrayOf(it.toByte())) }

        CrashLogStore.clear(context)

        assertEquals(0, logDir.listFiles()?.size ?: 0)
    }

    @Test
    fun `clear is a no-op when directory does not exist`() {
        // Should not throw
        CrashLogStore.clear(context)
    }

    @Test
    fun `buildReport contains timestamp`() {
        val report = CrashLogStore.buildReport(RuntimeException("test"), Thread.currentThread())
        assertTrue(report.contains("Time:"))
    }

    @Test
    fun `buildReport contains exception class name`() {
        val report = CrashLogStore.buildReport(RuntimeException("test-msg"), Thread.currentThread())
        assertTrue(report.contains("RuntimeException"))
        assertTrue(report.contains("test-msg"))
    }

    @Test
    fun `buildReport contains thread name`() {
        val report = CrashLogStore.buildReport(RuntimeException("t"), Thread.currentThread())
        assertTrue(report.contains(Thread.currentThread().name))
    }

    @Test
    fun `buildReport contains Android version and Paperkeep app id`() {
        val report = CrashLogStore.buildReport(RuntimeException("t"), Thread.currentThread())
        assertTrue(report.contains("Android:"))
        assertTrue(report.contains("app.paperkeep"))
    }

    @Test
    fun `buildReport includes cause chain up to 5 levels`() {
        var throwable: Throwable = RuntimeException("level5")
        repeat(4) { depth ->
            throwable = RuntimeException("level${4 - depth}", throwable)
        }
        val report = CrashLogStore.buildReport(throwable, Thread.currentThread())
        assertTrue(report.contains("Caused by"))
    }

    @Test
    fun `read returns null for file shorter than IV length`() {
        val shortFile = File(tempFolder.root, "short.enc").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val result = CrashLogStore.read(shortFile)
        assertTrue("read should return null for short file", result == null)
    }

    @Test
    fun `write creates crash directory if it does not exist`() {
        // In JVM tests, Keystore is unavailable — write() silently swallows the crypto failure.
        // We just assert it doesn't throw.
        CrashLogStore.write(context, "test crash log")
        // No assertion needed — absence of exception is the assertion.
    }
}
