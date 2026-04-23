package app.paperkeep.feature.sync

import kotlin.math.min
import kotlin.math.pow

/**
 * Computes exponential backoff delay for retry attempt [n] (0-indexed).
 *
 * Formula: min(BASE * 2^n, MAX_DELAY_MS)
 */
object ExponentialBackoff {

    private const val BASE_DELAY_MS = 1_000L   // 1 s
    private const val MAX_DELAY_MS = 60_000L   // 60 s

    /**
     * Returns the delay in milliseconds for the given attempt index.
     * @param attempt 0-indexed retry attempt (0 = first retry → 1 s)
     */
    fun delayFor(attempt: Int): Long {
        require(attempt >= 0) { "attempt must be >= 0" }
        val raw = BASE_DELAY_MS * 2.0.pow(attempt.toDouble())
        return min(raw.toLong(), MAX_DELAY_MS)
    }
}
