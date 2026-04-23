package app.paperkeep.core.common

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class InAppReviewManagerTest {

    private lateinit var clock: FakeReviewClock
    private lateinit var manager: InAppReviewManager

    @Before
    fun setUp() {
        clock = FakeReviewClock(nowMs = 0L)
        // Use a real Application context (Robolectric provides one)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Clear prefs to avoid cross-test contamination
        context.getSharedPreferences("in_app_review", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        manager = InAppReviewManager(context, clock)
    }

    @Test
    fun `shouldShowReview returns false when fewer than 3 exports`() {
        // Advance past install+3 days and never-prompted
        clock.nowMs = InAppReviewManager.MILLIS_PER_DAY * 10

        manager.recordExport()
        manager.recordExport()
        // 2 exports — not enough
        assertFalse(manager.shouldShowReview())
    }

    @Test
    fun `shouldShowReview returns false when fewer than 3 days since install`() {
        // Only 1 day since install
        clock.nowMs = InAppReviewManager.MILLIS_PER_DAY * 1

        repeat(3) { manager.recordExport() }
        assertFalse(manager.shouldShowReview())
    }

    @Test
    fun `shouldShowReview returns false when prompted within 90 days`() {
        clock.nowMs = InAppReviewManager.MILLIS_PER_DAY * 10 // 10 days since install

        repeat(3) { manager.recordExport() }
        manager.onReviewPrompted() // prompted now (t=10 days)

        // Advance by 50 days (still within 90-day cooldown)
        clock.nowMs = InAppReviewManager.MILLIS_PER_DAY * 60

        assertFalse(manager.shouldShowReview())
    }

    @Test
    fun `shouldShowReview returns true when all 3 conditions met`() {
        // 1. Record 3+ exports at t=0
        repeat(3) { manager.recordExport() }

        // 2. Advance 4 days (> 3) — never prompted
        clock.nowMs = InAppReviewManager.MILLIS_PER_DAY * 4

        assertTrue(manager.shouldShowReview())
    }

    @Test
    fun `shouldShowReview returns true after 90-day cooldown expires`() {
        clock.nowMs = 0L
        repeat(3) { manager.recordExport() }
        clock.nowMs = InAppReviewManager.MILLIS_PER_DAY * 5
        manager.onReviewPrompted() // prompted at day 5

        // Advance past 90-day cooldown (5 + 91 = 96 days)
        clock.nowMs = InAppReviewManager.MILLIS_PER_DAY * 96

        assertTrue(manager.shouldShowReview())
    }
}
