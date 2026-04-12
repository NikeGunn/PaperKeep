package com.scanvault.feature.scanner.batch

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

/**
 * Holds the in-progress multi-page batch capture state.
 *
 * Scoped to the navigation graph so it is shared between the scanner screen
 * and the reorder screen — both obtain it via [hiltViewModel] with a matching
 * graph-scoped back-stack entry.
 *
 * State is kept in memory only; persistence to Room/disk happens when the user
 * confirms the document (handled by the caller).
 */
@HiltViewModel
class BatchCaptureViewModel @Inject constructor() : ViewModel() {

    private val _pages = MutableStateFlow<List<BatchPage>>(emptyList())
    val pages: StateFlow<List<BatchPage>> = _pages.asStateFlow()

    /** True when the batch has at least one page. */
    val hasPages: Boolean get() = _pages.value.isNotEmpty()

    /** Number of captured pages. */
    val pageCount: Int get() = _pages.value.size

    // ── Capture ────────────────────────────────────────────────────────────────

    /**
     * Append a new page to the end of the batch.
     * [bitmap] should be the perspective-corrected image from the capture pipeline.
     */
    fun addPage(bitmap: Bitmap) {
        val page = BatchPage(id = UUID.randomUUID().toString(), bitmap = bitmap)
        _pages.update { current -> current + page }
    }

    // ── Reorder ────────────────────────────────────────────────────────────────

    /**
     * Move the page at [fromIndex] to [toIndex].
     * Both indices must be within [0, pageCount).
     */
    fun movePage(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        _pages.update { current ->
            if (fromIndex !in current.indices || toIndex !in current.indices) return@update current
            val mutable = current.toMutableList()
            val page = mutable.removeAt(fromIndex)
            mutable.add(toIndex, page)
            mutable
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    /** Remove the page with [pageId] from the batch. */
    fun deletePage(pageId: String) {
        _pages.update { current -> current.filter { it.id != pageId } }
    }

    /** Remove the page at [index] from the batch. */
    fun deletePageAt(index: Int) {
        _pages.update { current ->
            if (index !in current.indices) current
            else current.toMutableList().also { it.removeAt(index) }
        }
    }

    // ── Session lifecycle ──────────────────────────────────────────────────────

    /** Discard all pages and reset to an empty batch. */
    fun clearBatch() {
        _pages.value = emptyList()
    }

    /**
     * Return the current pages in order and clear the batch.
     * Called after the caller has persisted the pages to disk.
     */
    fun consumePages(): List<BatchPage> {
        val result = _pages.value
        _pages.value = emptyList()
        return result
    }
}
