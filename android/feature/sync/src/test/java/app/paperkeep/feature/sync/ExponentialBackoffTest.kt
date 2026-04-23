package app.paperkeep.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ExponentialBackoffTest {

    @Test
    fun `attempt 0 returns 1 second`() {
        assertEquals(1_000L, ExponentialBackoff.delayFor(0))
    }

    @Test
    fun `attempt 1 returns 2 seconds`() {
        assertEquals(2_000L, ExponentialBackoff.delayFor(1))
    }

    @Test
    fun `attempt 2 returns 4 seconds`() {
        assertEquals(4_000L, ExponentialBackoff.delayFor(2))
    }

    @Test
    fun `attempt 5 returns 32 seconds`() {
        assertEquals(32_000L, ExponentialBackoff.delayFor(5))
    }

    @Test
    fun `attempt 10 is capped at 60 seconds`() {
        assertEquals(60_000L, ExponentialBackoff.delayFor(10))
    }

    @Test
    fun `attempt 20 is capped at 60 seconds`() {
        assertEquals(60_000L, ExponentialBackoff.delayFor(20))
    }

    @Test
    fun `delays are monotonically increasing until cap`() {
        val delays = (0..6).map { ExponentialBackoff.delayFor(it) }
        for (i in 0 until delays.size - 1) {
            assert(delays[i] <= delays[i + 1]) { "Not monotone at index $i: ${delays[i]} > ${delays[i + 1]}" }
        }
    }
}
