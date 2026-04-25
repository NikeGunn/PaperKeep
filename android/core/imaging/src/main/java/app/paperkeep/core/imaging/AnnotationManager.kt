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

// ── Command record for undo/redo ──────────────────────────────────────────

/** Represents a reversible edit to the annotation list. */
sealed class AnnotationCommand {
    data class Add(val annotation: Annotation)    : AnnotationCommand()
    data class Erase(val annotation: Annotation)  : AnnotationCommand()
}

// ── Annotation manager ────────────────────────────────────────────────────

/**
 * Manages annotations for a single document page (P3.7).
 *
 * Supports text boxes, highlights, redactions, eraser, and a 30-step undo/redo
 * stack. Thread-safety: not thread-safe — call from a single coroutine / main thread.
 */
class AnnotationManager {

    private val _annotations = mutableListOf<Annotation>()
    private val history = UndoRedoStack<AnnotationCommand>(maxSteps = 30)

    /** All current annotations in insertion order. */
    val annotations: List<Annotation> get() = _annotations.toList()

    /** Number of annotations currently on the page. */
    val size: Int get() = _annotations.size

    /** Add [annotation] to the list and push an Add command to the undo stack. */
    fun addAnnotation(annotation: Annotation) {
        _annotations.add(annotation)
        history.push(AnnotationCommand.Add(annotation))
    }

    /**
     * Remove [annotation] (eraser tool).
     * Pushes an Erase command so the action can be undone.
     * @return `true` if [annotation] was found and removed.
     */
    fun erase(annotation: Annotation): Boolean {
        val removed = _annotations.remove(annotation)
        if (removed) history.push(AnnotationCommand.Erase(annotation))
        return removed
    }

    /**
     * Undo the last add or erase.
     * @return The annotation affected, or `null` if nothing to undo.
     */
    fun undo(): Annotation? {
        return when (val cmd = history.undo() ?: return null) {
            is AnnotationCommand.Add   -> { _annotations.remove(cmd.annotation); cmd.annotation }
            is AnnotationCommand.Erase -> { _annotations.add(cmd.annotation);    cmd.annotation }
        }
    }

    /**
     * Redo the last undone add or erase.
     * @return The annotation affected, or `null` if nothing to redo.
     */
    fun redo(): Annotation? {
        return when (val cmd = history.redo() ?: return null) {
            is AnnotationCommand.Add   -> { _annotations.add(cmd.annotation);    cmd.annotation }
            is AnnotationCommand.Erase -> { _annotations.remove(cmd.annotation); cmd.annotation }
        }
    }

    fun canUndo(): Boolean = history.canUndo()
    fun canRedo(): Boolean = history.canRedo()
}
