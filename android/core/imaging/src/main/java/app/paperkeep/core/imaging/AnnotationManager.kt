package app.paperkeep.core.imaging

import android.graphics.RectF

// ── Annotation data classes ────────────────────────────────────────────────

/** A text annotation placed at a specific [position] on the page. */
data class TextAnnotation(
    val id: Long = System.nanoTime(),
    val text: String,
    val position: Pair<Float, Float>,
)

/** A highlight annotation over a [region] on the page. */
data class HighlightAnnotation(
    val id: Long = System.nanoTime(),
    val region: RectF,
    val color: Int = 0xFFFFFF00.toInt(),
)

/** A redaction annotation that covers [region] on the page. */
data class RedactionAnnotation(
    val id: Long = System.nanoTime(),
    val region: RectF,
)

// ── Sealed type for the undo/redo stack ────────────────────────────────────

/** Union of all annotation types managed by [AnnotationManager]. */
sealed class Annotation {
    data class Text(val value: TextAnnotation) : Annotation()
    data class Highlight(val value: HighlightAnnotation) : Annotation()
    data class Redaction(val value: RedactionAnnotation) : Annotation()
}

// ── Undo/Redo stack ────────────────────────────────────────────────────────

/**
 * Generic bounded undo/redo stack (3B.7).
 *
 * Holds at most [maxSteps] items. When the limit is reached, the oldest
 * entry is silently dropped before adding the new one.
 */
class UndoRedoStack<T>(val maxSteps: Int = 30) {

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    /**
     * Push [item] onto the undo stack.
     *
     * Clears the redo stack (as with any editor). If [undoStack.size] would
     * exceed [maxSteps] the oldest item is dropped.
     */
    fun push(item: T) {
        redoStack.clear()
        if (undoStack.size >= maxSteps) {
            undoStack.removeFirst() // drop oldest
        }
        undoStack.addLast(item)
    }

    /**
     * Undo the last action.
     *
     * @return The item that was undone, or `null` if the stack is empty.
     */
    fun undo(): T? {
        val item = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(item)
        return item
    }

    /**
     * Redo the last undone action.
     *
     * @return The restored item, or `null` if there is nothing to redo.
     */
    fun redo(): T? {
        val item = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(item)
        return item
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun undoSize(): Int = undoStack.size
    fun redoSize(): Int = redoStack.size
}

// ── Annotation manager ────────────────────────────────────────────────────

/**
 * Manages annotations for a single document page (3B.7).
 *
 * Supports up to [UndoRedoStack.maxSteps] undo steps.
 * Thread-safety: not thread-safe — call from a single coroutine / main thread.
 */
class AnnotationManager {

    private val _annotations = mutableListOf<Annotation>()
    private val history = UndoRedoStack<Annotation>(maxSteps = 30)

    /** All current annotations in insertion order. */
    val annotations: List<Annotation> get() = _annotations.toList()

    /**
     * Add [annotation] to the list and push it to the undo stack.
     */
    fun addAnnotation(annotation: Annotation) {
        _annotations.add(annotation)
        history.push(annotation)
    }

    /**
     * Undo the last [addAnnotation] call.
     *
     * @return The annotation that was removed, or `null` if nothing to undo.
     */
    fun undo(): Annotation? {
        val removed = history.undo() ?: return null
        _annotations.remove(removed)
        return removed
    }

    /**
     * Redo the last undone annotation.
     *
     * @return The annotation that was re-added, or `null` if nothing to redo.
     */
    fun redo(): Annotation? {
        val restored = history.redo() ?: return null
        _annotations.add(restored)
        return restored
    }

    fun canUndo(): Boolean = history.canUndo()
    fun canRedo(): Boolean = history.canRedo()
}
