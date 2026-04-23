package app.paperkeep.feature.onboarding

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeOnboardingRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeOnboardingRepository()
        viewModel = OnboardingViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── page count ────────────────────────────────────────────────────────────

    @Test
    fun `page count constant is 3`() {
        assertEquals(3, ONBOARDING_PAGE_COUNT)
    }

    // ── navigation ────────────────────────────────────────────────────────────

    @Test
    fun `initial page is 0`() = runTest {
        viewModel.currentPage.test {
            assertEquals(0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nextPage advances from page 0 to page 1`() = runTest {
        viewModel.nextPage()
        viewModel.currentPage.test {
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nextPage on last page triggers completion`() = runTest {
        // Navigate to last page (page 2 = index ONBOARDING_PAGE_COUNT - 1)
        repeat(ONBOARDING_PAGE_COUNT - 1) { viewModel.nextPage() }
        viewModel.nextPage() // triggers complete()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("repository must be marked completed", fakeRepo.completed)
    }

    // ── skip ──────────────────────────────────────────────────────────────────

    @Test
    fun `skip triggers completion immediately`() = runTest {
        viewModel.skip()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("isCompleted must be true after skip", fakeRepo.completed)
    }

    // ── completion persists via DataStore ─────────────────────────────────────

    @Test
    fun `completion flag written to repository on complete`() = runTest {
        viewModel.skip()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("markCompleted must be called", fakeRepo.completed)
    }

    @Test
    fun `isCompleted flow emits true after skip`() = runTest {
        viewModel.isCompleted.test {
            val initial = awaitItem() // false initially
            if (!initial) {
                viewModel.skip()
                testDispatcher.scheduler.advanceUntilIdle()
                assertTrue(awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** Fake repository for unit tests — no DataStore/Context required. */
class FakeOnboardingRepository : OnboardingRepository {
    var completed: Boolean = false
    private val _flow = MutableStateFlow(false)

    override val isCompleted: Flow<Boolean> = _flow

    override suspend fun markCompleted() {
        completed = true
        _flow.value = true
    }
}
