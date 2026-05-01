package app.paperkeep.core.ml.summarizer

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks free-tier daily summarizer quota (3 uses/day).
 *
 * Uses plain SharedPreferences — the count + date are not sensitive.
 * Pro users skip this check entirely (see [TextSummarizer.summarize]).
 */
@Singleton
class SummarizerQuotaTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun canSummarize(): Boolean {
        val today = todayKey()
        val count = prefs.getInt(today, 0)
        return count < FREE_DAILY_LIMIT
    }

    fun recordUsage() {
        val today = todayKey()
        val count = prefs.getInt(today, 0)
        prefs.edit().putInt(today, count + 1).apply()
        pruneOldKeys(today)
    }

    fun usagesRemainingToday(): Int {
        val count = prefs.getInt(todayKey(), 0)
        return (FREE_DAILY_LIMIT - count).coerceAtLeast(0)
    }

    fun usagesTodayForTest(): Int = prefs.getInt(todayKey(), 0)

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }

    private fun pruneOldKeys(today: String) {
        val toRemove = prefs.all.keys.filter { it != today }
        if (toRemove.isNotEmpty()) prefs.edit().apply { toRemove.forEach { remove(it) } }.apply()
    }

    companion object {
        const val FREE_DAILY_LIMIT = 3
        private const val PREFS_NAME = "paperkeep_summarizer_quota"
    }
}
