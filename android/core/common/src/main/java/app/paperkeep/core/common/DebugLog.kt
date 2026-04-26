package app.paperkeep.core.common

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Diagnostic logger for Paperkeep — emits to logcat ONLY when the app is
 * installed as a debuggable build (FLAG_DEBUGGABLE).
 *
 * Use this for ground-truth pipeline tracing where unit tests can't reach:
 * Room write commits, Coil decode failures, edge-detector confidence, etc.
 *
 * Release builds: every call is a no-op. The `enabled` flag is computed once
 * at process start from the application's manifest flags, so there is no
 * runtime cost beyond a volatile boolean read.
 */
object DebugLog {
    @Volatile
    private var enabled: Boolean = false

    /** Call once from Application.onCreate(). */
    fun init(app: Application) {
        enabled = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /** Late init from any Context (used in tests / before Application.onCreate). */
    fun initFromContext(context: Context) {
        enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun d(tag: String, msg: String) {
        if (enabled) Log.d(tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (enabled) {
            if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
        }
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (enabled) {
            if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        }
    }
}
