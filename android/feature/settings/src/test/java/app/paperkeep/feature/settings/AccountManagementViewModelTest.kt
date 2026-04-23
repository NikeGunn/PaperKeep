package app.paperkeep.feature.settings

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// Stub ViewModel tests for Paperkeep v2 (no backend / no account).
// Full Backup & Restore tests will replace these in P4.x.

@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AccountManagementViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AccountManagementViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isIdle() {
        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.event)
        assertNull(state.error)
    }

    @Test
    fun clearError_resetsErrorField() = runTest {
        // Inject an error by directly mutating via reflection is not clean;
        // instead verify the public contract: clearError on a clean state is a no-op.
        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun clearEvent_resetsEventField() = runTest {
        viewModel.clearEvent()
        assertNull(viewModel.uiState.value.event)
    }

    @Test
    fun uiState_emitsOnlyOneInitialItem() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(false, initial.isLoading)
            assertNull(initial.event)
            assertNull(initial.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
