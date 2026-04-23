package app.paperkeep.crash

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

class PaperkeepCrashHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
        every { context.applicationContext } returns context
    }

    @Test
    fun `install sets PaperkeepCrashHandler as default uncaught exception handler`() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        try {
            PaperkeepCrashHandler.install(context)
            assertTrue(Thread.getDefaultUncaughtExceptionHandler() is PaperkeepCrashHandler)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun `install is idempotent — second call does not wrap the handler again`() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        try {
            PaperkeepCrashHandler.install(context)
            val first = Thread.getDefaultUncaughtExceptionHandler()
            PaperkeepCrashHandler.install(context)
            val second = Thread.getDefaultUncaughtExceptionHandler()
            assertEquals(first, second)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun `getCrashLogs returns empty list when no logs exist`() {
        val logs = PaperkeepCrashHandler.getCrashLogs(context)
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `getCrashLogs returns logs sorted newest first`() {
        val logDir = File(filesDir, "crash_logs").also { it.mkdirs() }
        File(logDir, "crash_old.log").also { it.writeText("old"); it.setLastModified(1_000_000L) }
        File(logDir, "crash_new.log").also { it.writeText("new"); it.setLastModified(2_000_000L) }

        val logs = PaperkeepCrashHandler.getCrashLogs(context)

        assertEquals(2, logs.size)
        assertEquals("crash_new.log", logs[0].name)
        assertEquals("crash_old.log", logs[1].name)
    }

    @Test
    fun `clearCrashLogs deletes all log files`() {
        val logDir = File(filesDir, "crash_logs").also { it.mkdirs() }
        repeat(3) { File(logDir, "crash_$it.log").writeText("data") }

        PaperkeepCrashHandler.clearCrashLogs(context)

        assertEquals(0, logDir.listFiles()?.size ?: 0)
    }

    @Test
    fun `install and trigger — crash log is written`() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        // Install a no-op as current handler so crash doesn't propagate
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        try {
            PaperkeepCrashHandler.install(context)
            val handler = Thread.getDefaultUncaughtExceptionHandler()!!
            handler.uncaughtException(Thread.currentThread(), RuntimeException("test crash"))

            val logs = PaperkeepCrashHandler.getCrashLogs(context)
            assertFalse("Expected at least one crash log", logs.isEmpty())
            val content = logs[0].readText()
            assertTrue(content.contains("Paperkeep Crash Report"))
            assertTrue(content.contains("test crash"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun `crash log is truncated to max size`() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        try {
            PaperkeepCrashHandler.install(context)
            val handler = Thread.getDefaultUncaughtExceptionHandler()!!
            val hugeMessage = "X".repeat(300 * 1024) // 300KB > 128KB limit
            handler.uncaughtException(Thread.currentThread(), RuntimeException(hugeMessage))

            val logs = PaperkeepCrashHandler.getCrashLogs(context)
            assertFalse(logs.isEmpty())
            assertTrue(
                "Log file ${logs[0].length()} exceeds 128KB",
                logs[0].length() <= 128 * 1024L,
            )
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun `log rotation keeps at most 10 files`() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        try {
            val logDir = File(filesDir, "crash_logs").also { it.mkdirs() }
            repeat(10) { i ->
                File(logDir, "crash_pre_$i.log").also {
                    it.writeText("old $i")
                    it.setLastModified((i + 1) * 1_000L)
                }
            }

            PaperkeepCrashHandler.install(context)
            val handler = Thread.getDefaultUncaughtExceptionHandler()!!
            handler.uncaughtException(Thread.currentThread(), RuntimeException("overflow"))

            val count = logDir.listFiles()?.size ?: 0
            assertTrue("Expected ≤10 log files, got $count", count <= 10)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }
}
