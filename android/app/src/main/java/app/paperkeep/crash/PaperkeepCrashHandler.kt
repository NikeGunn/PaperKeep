package app.paperkeep.crash

import android.content.Context
import app.paperkeep.core.common.crash.CrashLogStore
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Custom uncaught exception handler.
 *
 * On crash:
 * 1. Builds a human-readable report (timestamp, device info, stack trace).
 * 2. Delegates to [CrashLogStore] which encrypts it with AES-256-GCM using a
 *    hardware-backed Keystore key and writes to `filesDir/crash/crash_<ts>.enc`.
 * 3. Delegates to the previous handler (Android's default crash dialog).
 *
 * Nothing auto-uploads. The user taps "View crash logs" in Settings > Diagnostics
 * to see the list and optionally clear them. Decryption happens in-process on demand.
 */
class PaperkeepCrashHandler private constructor(
    private val context: Context,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    private val handling = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (!handling.compareAndSet(false, true)) {
            previousHandler?.uncaughtException(thread, throwable)
            return
        }
        try {
            val report = CrashLogStore.buildReport(throwable, thread)
            CrashLogStore.write(context, report)
        } catch (_: Exception) {
            // Swallow — secondary failure would hide the original crash.
        } finally {
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        /** Install the handler. Safe to call multiple times — idempotent. */
        fun install(context: Context) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is PaperkeepCrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(
                PaperkeepCrashHandler(context.applicationContext, current)
            )
        }

        /** Return all stored encrypted crash log files, newest first. */
        fun getCrashLogFiles(context: Context): List<File> =
            CrashLogStore.listFiles(context)

        /** Decrypt and return the plaintext content of a crash log file. */
        fun readCrashLog(file: File): String? = CrashLogStore.read(file)

        /** Delete all stored crash logs. */
        fun clearCrashLogs(context: Context) = CrashLogStore.clear(context)
    }
}
