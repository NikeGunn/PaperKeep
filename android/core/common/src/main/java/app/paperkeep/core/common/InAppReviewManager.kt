package app.paperkeep.core.common

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app review trigger manager (3B.11).
 *
 * Conditions before prompting:
 * 1. User has exported at least 3 times.
 * 2. At least 3 days have passed since install.
 * 3. The review prompt has not been shown within the last 90 days.
 *
 * Backed by [SharedPreferences] so state persists across process restarts.
 * Call [recordExport] after every successful export.
 * Call [shouldShowReview] before launching the Play In-App Review flow.
 */
@Singleton
class InAppReviewManager @Inject constructor(
    context: Context,
    private val clock: ReviewClock = SystemReviewClock,
) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Record install timestamp on first run
        if (!prefs.contains(KEY_INSTALL_TIME)) {
            prefs.edit().putLong(KEY_INSTALL_TIME, clock.now()).apply()
        }
    }

    /** Increment the export counter. */
    fun recordExport() {
        val count = prefs.getInt(KEY_EXPORT_COUNT, 0)
        prefs.edit().putInt(KEY_EXPORT_COUNT, count + 1).apply()
    }

    /**
     * Mark that the review prompt was just shown.
     * Resets the 90-day cooldown.
     */
    fun onReviewPrompted() {
        prefs.edit().putLong(KEY_LAST_PROMPTED, clock.now()).apply()
    }

    /**
     * @return `true` iff all 3 conditions are met:
     *   - exportCount >= 3
     *   - days since install >= 3
     *   - days since last prompt >= 90 (or never prompted)
     */
    fun shouldShowReview(): Boolean {
        val exportCount = prefs.getInt(KEY_EXPORT_COUNT, 0)
        if (exportCount < MIN_EXPORTS) return false

        val installTime = prefs.getLong(KEY_INSTALL_TIME, clock.now())
        val daysSinceInstall = (clock.now() - installTime) / MILLIS_PER_DAY
        if (daysSinceInstall < MIN_DAYS_SINCE_INSTALL) return false

        val lastPrompted = prefs.getLong(KEY_LAST_PROMPTED, 0L)
        val daysSincePrompt = if (lastPrompted == 0L) Long.MAX_VALUE
        else (clock.now() - lastPrompted) / MILLIS_PER_DAY
        if (daysSincePrompt < MIN_DAYS_BETWEEN_PROMPTS) return false

        return true
    }

    /** Visible for testing — direct prefs access. */
    internal fun getExportCount(): Int = prefs.getInt(KEY_EXPORT_COUNT, 0)

    companion object {
        private const val PREFS_NAME = "in_app_review"
        private const val KEY_INSTALL_TIME = "install_time"
        private const val KEY_EXPORT_COUNT = "export_count"
        private const val KEY_LAST_PROMPTED = "last_prompted"

        const val MIN_EXPORTS = 3
        const val MIN_DAYS_SINCE_INSTALL = 3L
        const val MIN_DAYS_BETWEEN_PROMPTS = 90L
        const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
}

/** Wall-clock abstraction for testability. */
interface ReviewClock {
    fun now(): Long
}

object SystemReviewClock : ReviewClock {
    override fun now(): Long = System.currentTimeMillis()
}

/** Mutable fake clock for tests. */
class FakeReviewClock(var nowMs: Long = 0L) : ReviewClock {
    override fun now(): Long = nowMs
}
