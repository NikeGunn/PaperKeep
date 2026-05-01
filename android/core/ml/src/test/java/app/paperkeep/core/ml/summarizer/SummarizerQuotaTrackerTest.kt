package app.paperkeep.core.ml.summarizer

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SummarizerQuotaTrackerTest {

    private lateinit var tracker: SummarizerQuotaTracker

    @Before
    fun setUp() {
        tracker = SummarizerQuotaTracker(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `canSummarize returns true initially`() {
        assertTrue(tracker.canSummarize())
    }

    @Test
    fun `usagesRemainingToday returns FREE_DAILY_LIMIT initially`() {
        assertEquals(SummarizerQuotaTracker.FREE_DAILY_LIMIT, tracker.usagesRemainingToday())
    }

    @Test
    fun `recordUsage decrements remaining count`() {
        tracker.recordUsage()
        assertEquals(SummarizerQuotaTracker.FREE_DAILY_LIMIT - 1, tracker.usagesRemainingToday())
    }

    @Test
    fun `canSummarize returns false after hitting daily limit`() {
        repeat(SummarizerQuotaTracker.FREE_DAILY_LIMIT) { tracker.recordUsage() }
        assertFalse(tracker.canSummarize())
    }

    @Test
    fun `usagesRemainingToday returns 0 after hitting daily limit`() {
        repeat(SummarizerQuotaTracker.FREE_DAILY_LIMIT) { tracker.recordUsage() }
        assertEquals(0, tracker.usagesRemainingToday())
    }

    @Test
    fun `FREE_DAILY_LIMIT is 3`() {
        assertEquals(3, SummarizerQuotaTracker.FREE_DAILY_LIMIT)
    }

    @Test
    fun `usagesRemainingToday never returns negative`() {
        repeat(SummarizerQuotaTracker.FREE_DAILY_LIMIT + 5) { tracker.recordUsage() }
        assertTrue(tracker.usagesRemainingToday() >= 0)
    }

    @Test
    fun `usagesTodayForTest tracks count accurately`() {
        assertEquals(0, tracker.usagesTodayForTest())
        tracker.recordUsage()
        assertEquals(1, tracker.usagesTodayForTest())
        tracker.recordUsage()
        assertEquals(2, tracker.usagesTodayForTest())
    }
}
