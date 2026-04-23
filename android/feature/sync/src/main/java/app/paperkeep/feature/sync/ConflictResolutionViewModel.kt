package app.paperkeep.feature.sync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for the conflict resolution screen.
 *
 * Resolution choices are stored in [resolutions] — keyed by docId.
 * An in-memory map is used so tests don't need DataStore/Room.
 */
@HiltViewModel
class ConflictResolutionViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow<ConflictResolutionState?>(null)
    val state: StateFlow<ConflictResolutionState?> = _state.asStateFlow()

    /** In-memory persistence of resolved choices (docId → choice). */
    private val resolutions = mutableMapOf<String, ConflictChoice>()

    /** Load a conflict for the user to resolve. */
    fun loadConflict(conflict: ConflictResolutionState) {
        // Restore any previously persisted choice
        val saved = resolutions[conflict.docId]
        _state.value = if (saved != null) conflict.copy(choice = saved) else conflict
    }

    /**
     * Persist the user's [choice] for the current conflict.
     */
    fun resolveConflict(choice: ConflictChoice) {
        val current = _state.value ?: return
        resolutions[current.docId] = choice
        _state.value = current.copy(choice = choice)
    }

    /** Return the persisted choice for [docId], or [ConflictChoice.UNDECIDED] if none. */
    fun getResolution(docId: String): ConflictChoice =
        resolutions[docId] ?: ConflictChoice.UNDECIDED
}
