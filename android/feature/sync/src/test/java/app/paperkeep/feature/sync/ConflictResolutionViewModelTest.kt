package app.paperkeep.feature.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictResolutionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ConflictResolutionViewModel

    private val sampleConflict = ConflictResolutionState(
        docId = "doc-001",
        serverVersion = "v3",
        localVersion = "v2",
        serverTimestamp = "2026-04-01T10:00:00Z",
        localTimestamp = "2026-04-01T09:00:00Z",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ConflictResolutionViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is null`() {
        assertNull(viewModel.state.value)
    }

    @Test
    fun `loadConflict sets state with UNDECIDED choice`() = runTest {
        viewModel.loadConflict(sampleConflict)
        val state = viewModel.state.value
        assertNotNull(state)
        assertEquals(ConflictChoice.UNDECIDED, state!!.choice)
    }

    @Test
    fun `loadConflict sets all conflict fields`() = runTest {
        viewModel.loadConflict(sampleConflict)
        val state = viewModel.state.value!!
        assertEquals("doc-001", state.docId)
        assertEquals("v3", state.serverVersion)
        assertEquals("v2", state.localVersion)
    }

    @Test
    fun `resolveConflict SERVER sets choice to SERVER`() = runTest {
        viewModel.loadConflict(sampleConflict)
        viewModel.resolveConflict(ConflictChoice.SERVER)
        assertEquals(ConflictChoice.SERVER, viewModel.state.value!!.choice)
    }

    @Test
    fun `resolveConflict LOCAL sets choice to LOCAL`() = runTest {
        viewModel.loadConflict(sampleConflict)
        viewModel.resolveConflict(ConflictChoice.LOCAL)
        assertEquals(ConflictChoice.LOCAL, viewModel.state.value!!.choice)
    }

    @Test
    fun `getResolution returns UNDECIDED before any choice`() {
        assertEquals(ConflictChoice.UNDECIDED, viewModel.getResolution("doc-001"))
    }

    @Test
    fun `getResolution returns persisted choice after resolveConflict`() = runTest {
        viewModel.loadConflict(sampleConflict)
        viewModel.resolveConflict(ConflictChoice.SERVER)
        assertEquals(ConflictChoice.SERVER, viewModel.getResolution("doc-001"))
    }

    @Test
    fun `resolution persists across loadConflict calls`() = runTest {
        viewModel.loadConflict(sampleConflict)
        viewModel.resolveConflict(ConflictChoice.LOCAL)

        // Reload the same conflict (simulates navigation back)
        viewModel.loadConflict(sampleConflict)
        assertEquals(ConflictChoice.LOCAL, viewModel.state.value!!.choice)
    }

    @Test
    fun `resolving one doc does not affect another doc`() = runTest {
        viewModel.loadConflict(sampleConflict)
        viewModel.resolveConflict(ConflictChoice.SERVER)

        assertEquals(ConflictChoice.UNDECIDED, viewModel.getResolution("doc-other"))
    }

    @Test
    fun `resolveConflict without loadConflict does not crash`() {
        viewModel.resolveConflict(ConflictChoice.SERVER) // state is null — should be a no-op
        assertNull(viewModel.state.value)
    }

    @Test
    fun `choice can be changed from SERVER to LOCAL`() = runTest {
        viewModel.loadConflict(sampleConflict)
        viewModel.resolveConflict(ConflictChoice.SERVER)
        viewModel.resolveConflict(ConflictChoice.LOCAL)
        assertEquals(ConflictChoice.LOCAL, viewModel.state.value!!.choice)
        assertEquals(ConflictChoice.LOCAL, viewModel.getResolution("doc-001"))
    }
}
