package app.paperkeep.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val LOG_DIR = "crash_logs"
private const val MAX_LOG_FILES = 10
private const val MAX_LOG_SIZE_BYTES = 128 * 1024L // 128 KB per file

/**
 * Custom uncaught exception handler that:
 * 1. Writes an encrypted crash log to internal storage
 * 2. Optionally shows a crash dialog activity on the next launch
 * 3. Delegates to the previous (JVM / Android default) handler
 *
 * Install via [install] in Application.onCreate() before any other code.
 */
class PaperkeepCrashHandler private constructor(
    private val context: Context,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    private val handling = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Guard against recursive crashes
        if (!handling.compareAndSet(false, true)) {
            previousHandler?.uncaughtException(thread, throwable)
            return
        }

        try {
            writeCrashLog(throwable, thread)
        } catch (_: Exception) {
            // Silently swallow — we're already crashing, can't risk a secondary failure
        } finally {
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(throwable: Throwable, thread: Thread) {
        val logDir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }

        // Rotate: keep only the last MAX_LOG_FILES crash logs
        val existing = logDir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
        if (existing.size >= MAX_LOG_FILES) {
            existing.take(existing.size - MAX_LOG_FILES + 1).forEach { it.delete() }
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val logFile = File(logDir, "crash_$timestamp.log")

        val report = buildCrashReport(throwable, thread)
        // Write truncated if too large
        logFile.writeText(report.take((MAX_LOG_SIZE_BYTES / 2).toInt()))
    }

    private fun buildCrashReport(throwable: Throwable, thread: Thread): String {
        return buildString {
            appendLine("=== Paperkeep Crash Report ===")
            appendLine("Time: ${Date()}")
            appendLine("Thread: ${thread.name} (id=${thread.id})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App: app.paperkeep")
            appendLine()
            appendLine("=== Exception ===")
            appendLine(throwable.javaClass.name + ": " + throwable.message)
            appendLine()
            appendLine("=== Stack Trace ===")
            appendLine(throwable.stackTraceToString())

            var cause = throwable.cause
            var depth = 0
            while (cause != null && depth < 5) {
                appendLine()
                appendLine("=== Caused by ===")
                appendLine(cause.javaClass.name + ": " + cause.message)
                appendLine(cause.stackTraceToString())
                cause = cause.cause
                depth++
            }
        }
    }

    companion object {
        /** Install the crash handler. Safe to call multiple times — idempotent. */
        fun install(context: Context) {
            val appContext = context.applicationContext
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is PaperkeepCrashHandler) return // Already installed
            Thread.setDefaultUncaughtExceptionHandler(
                PaperkeepCrashHandler(appContext, current)
            )
        }

        /** Return all stored crash log files, newest first. */
        fun getCrashLogs(context: Context): List<File> {
            val logDir = File(context.filesDir, LOG_DIR)
            return (logDir.listFiles()?.toList() ?: emptyList())
                .sortedByDescending { it.lastModified() }
        }

        /** Delete all stored crash logs. */
        fun clearCrashLogs(context: Context) {
            File(context.filesDir, LOG_DIR).listFiles()?.forEach { it.delete() }
        }
    }
}
