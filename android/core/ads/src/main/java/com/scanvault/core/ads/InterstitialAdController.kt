package com.scanvault.core.ads

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls interstitial ad display logic (3B.10).
 *
 * - Shows after every 5th export action.
 * - Enforces a 3-minute (180 000 ms) frequency cap between shows.
 * - No banner ad support.
 */
@Singleton
class InterstitialAdController @Inject constructor(
    private val clock: Clock = SystemClock,
) {

    private var exportCount: Int = 0
    // Initialised to a value far enough in the past that the cap is always expired on startup.
    private var lastShownMs: Long = Long.MIN_VALUE / 2

    /**
     * Record an export event.
     *
     * @return `true` if an interstitial should be shown (5th export AND
     *         frequency cap not active). The counter is reset on show.
     */
    fun onExport(): Boolean {
        exportCount++
        if (exportCount < EXPORTS_PER_AD) return false
        val now = clock.now()
        val capExpired = now - lastShownMs >= FREQUENCY_CAP_MS
        return if (capExpired) {
            exportCount = 0
            lastShownMs = now
            true
        } else {
            // Frequency cap still active — do not show, but keep counter reset
            // for the next cycle once cap expires.
            false
        }
    }

    /** Reset state (useful in tests). */
    fun reset() {
        exportCount = 0
        lastShownMs = Long.MIN_VALUE / 2
    }

    companion object {
        const val EXPORTS_PER_AD = 5
        const val FREQUENCY_CAP_MS = 3 * 60 * 1000L // 3 minutes
    }
}

/** Abstraction over wall clock for testability. */
interface Clock {
    fun now(): Long
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

/** Test-friendly mutable clock. */
class FakeClock(var nowMs: Long = 0L) : Clock {
    override fun now(): Long = nowMs
}
