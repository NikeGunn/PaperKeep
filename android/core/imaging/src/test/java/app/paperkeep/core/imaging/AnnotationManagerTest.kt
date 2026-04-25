package app.paperkeep.core.imaging

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AnnotationManagerTest {

    private lateinit var manager: AnnotationManager

    @Before
    fun setUp() {
        manager = AnnotationManager()
    }

    private fun textAnnotation(text: String = "Hello") =
        Annotation.Text(TextAnnotation(text = text, position = 0f to 0f))

    private fun highlightAnnotation() =
        Annotation.Highlight(HighlightAnnotation(region = RectF(0f, 0f, 100f, 20f)))

    // ── addAnnotation ─────────────────────────────────────────────────────────

    @Test
    fun `addAnnotation adds to list`() {
        manager.addAnnotation(textAnnotation("First"))
        assertEquals(1, manager.annotations.size)
    }

    @Test
    fun `addAnnotation adds multiple annotations`() {
        manager.addAnnotation(textAnnotation("A"))
        manager.addAnnotation(textAnnotation("B"))
        manager.addAnnotation(highlightAnnotation())
        assertEquals(3, manager.annotations.size)
    }

    // ── undo ──────────────────────────────────────────────────────────────────

    @Test
    fun `undo removes last annotation`() {
        manager.addAnnotation(textAnnotation("Keep"))
        manager.addAnnotation(textAnnotation("Remove"))
        manager.undo()
        assertEquals(1, manager.annotations.size)
        assertEquals(
            "Keep",
            (manager.annotations.first() as Annotation.Text).value.text,
        )
    }

    @Test
    fun `undo returns the removed annotation`() {
        val ann = textAnnotation("Hi")
        manager.addAnnotation(ann)
        val undone = manager.undo()
        assertNotNull(undone)
        assertEquals(ann, undone)
    }

    @Test
    fun `undo returns null when nothing to undo`() {
        val result = manager.undo()
        assertNull(result)
    }

    // ── redo ──────────────────────────────────────────────────────────────────

    @Test
    fun `redo restores undone annotation`() {
        val ann = textAnnotation("Restore")
        manager.addAnnotation(ann)
        manager.undo()
        assertEquals(0, manager.annotations.size)

        manager.redo()
        assertEquals(1, manager.annotations.size)
        assertEquals(ann, manager.annotations.first())
    }

    @Test
    fun `redo returns null when nothing to redo`() {
        assertNull(manager.redo())
    }

    // ── UndoRedoStack max steps ───────────────────────────────────────────────

    @Test
    fun `undo stack is capped at 30 — 31st oldest is dropped`() {
        val stack = UndoRedoStack<Int>(maxSteps = 30)
        // Push 31 items
        for (i in 1..31) stack.push(i)
        // Stack should hold exactly 30
        assertEquals(30, stack.undoSize())
        // The oldest (item 1) should have been dropped. Undo 30 times and
        // verify the first-undone item is 31, last is 2 (not 1).
        var last: Int? = null
        repeat(30) { last = stack.undo() }
        assertEquals("oldest surviving item should be 2 (item 1 was dropped)", 2, last)
    }

    @Test
    fun `UndoRedoStack redo stack cleared on new push`() {
        val stack = UndoRedoStack<String>()
        stack.push("a")
        stack.push("b")
        stack.undo()
        assertTrue(stack.canRedo())
        stack.push("c")
        assertTrue("redo stack must be cleared after new push", !stack.canRedo())
    }

    // ── erase (eraser tool) ───────────────────────────────────────────────────

    @Test
    fun `erase removes target annotation from list`() {
        val ann = textAnnotation("Erase me")
        manager.addAnnotation(ann)
        val removed = manager.erase(ann)
        assertTrue("erase must return true when found", removed)
        assertEquals(0, manager.annotations.size)
    }

    @Test
    fun `erase returns false when annotation not found`() {
        val other = textAnnotation("Ghost")
        assertFalse("erase on absent annotation must return false", manager.erase(other))
    }

    @Test
    fun `erase does not affect other annotations`() {
        val keep = textAnnotation("Keep")
        val remove = textAnnotation("Remove")
        manager.addAnnotation(keep)
        manager.addAnnotation(remove)
        manager.erase(remove)
        assertEquals(1, manager.annotations.size)
        assertEquals(keep, manager.annotations.first())
    }

    @Test
    fun `erase followed by undo restores the annotation`() {
        val ann = textAnnotation("Restore after erase")
        manager.addAnnotation(ann)
        manager.erase(ann)
        assertEquals(0, manager.annotations.size)
        manager.undo()
        assertEquals(1, manager.annotations.size)
        assertEquals(ann, manager.annotations.first())
    }

    // ── size ──────────────────────────────────────────────────────────────────

    @Test
    fun `size returns correct count`() {
        assertEquals(0, manager.size)
        manager.addAnnotation(textAnnotation())
        assertEquals(1, manager.size)
        manager.addAnnotation(highlightAnnotation())
        assertEquals(2, manager.size)
        manager.undo()
        assertEquals(1, manager.size)
    }

    // ── All three annotation types ────────────────────────────────────────────

    @Test
    fun `redaction annotation added and undone correctly`() {
        val redaction = Annotation.Redaction(RedactionAnnotation(region = RectF(0f, 0f, 50f, 50f)))
        manager.addAnnotation(redaction)
        assertEquals(1, manager.annotations.size)
        manager.undo()
        assertEquals(0, manager.annotations.size)
    }

    @Test
    fun `mixed annotation types coexist`() {
        manager.addAnnotation(textAnnotation("Note"))
        manager.addAnnotation(highlightAnnotation())
        manager.addAnnotation(Annotation.Redaction(RedactionAnnotation(region = RectF(0f, 0f, 10f, 10f))))
        assertEquals(3, manager.annotations.size)
        assertTrue(manager.annotations.any { it is Annotation.Text })
        assertTrue(manager.annotations.any { it is Annotation.Highlight })
        assertTrue(manager.annotations.any { it is Annotation.Redaction })
    }
}
