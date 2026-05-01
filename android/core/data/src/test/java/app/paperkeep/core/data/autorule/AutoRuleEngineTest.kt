package app.paperkeep.core.data.autorule

import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Tests for [AutoRuleEngine] — folder auto-rule evaluation logic.
 */
class AutoRuleEngineTest {

    private lateinit var engine: AutoRuleEngine

    private val baseDoc = Document(
        id = "doc1",
        title = "Test",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        folderId = null,
        pageCount = 1,
        colorTag = null,
        docType = null,
        isFavorite = false,
        isArchived = false,
        pages = emptyList(),
    )

    private fun folder(id: String, name: String, autoRule: String?) = Folder(
        id = id,
        name = name,
        icon = "folder",
        autoRule = autoRule,
        createdAt = Instant.ofEpochMilli(id.hashCode().toLong()),
        updatedAt = Instant.EPOCH,
    )

    @Before
    fun setUp() {
        engine = AutoRuleEngine()
    }

    @Test
    fun `evaluate returns true for exact docType match`() {
        val doc = baseDoc.copy(docType = "receipt")
        assert(engine.evaluate("docType=receipt", doc))
    }

    @Test
    fun `evaluate returns false for docType mismatch`() {
        val doc = baseDoc.copy(docType = "a4")
        assert(!engine.evaluate("docType=receipt", doc))
    }

    @Test
    fun `evaluate returns false when docType is null`() {
        val doc = baseDoc.copy(docType = null)
        assert(!engine.evaluate("docType=receipt", doc))
    }

    @Test
    fun `evaluate returns false for unknown rule prefix`() {
        val doc = baseDoc.copy(docType = "receipt")
        assert(!engine.evaluate("someOtherRule=receipt", doc))
    }

    @Test
    fun `evaluate trims whitespace around rule`() {
        val doc = baseDoc.copy(docType = "receipt")
        assert(engine.evaluate("  docType=receipt  ", doc))
    }

    @Test
    fun `findMatchingFolder returns null when no folders`() {
        val doc = baseDoc.copy(docType = "receipt")
        assertNull(engine.findMatchingFolder(doc, emptyList()))
    }

    @Test
    fun `findMatchingFolder returns null when no rule matches`() {
        val doc = baseDoc.copy(docType = "a4")
        val folders = listOf(folder("f1", "Receipts", "docType=receipt"))
        assertNull(engine.findMatchingFolder(doc, folders))
    }

    @Test
    fun `findMatchingFolder returns folder id for matching rule`() {
        val doc = baseDoc.copy(docType = "receipt")
        val folders = listOf(folder("f1", "Receipts", "docType=receipt"))
        assertEquals("f1", engine.findMatchingFolder(doc, folders))
    }

    @Test
    fun `findMatchingFolder returns null when folder has no rule`() {
        val doc = baseDoc.copy(docType = "receipt")
        val folders = listOf(folder("f1", "Misc", autoRule = null))
        assertNull(engine.findMatchingFolder(doc, folders))
    }

    @Test
    fun `findMatchingFolder returns first matching folder in creation order`() {
        val doc = baseDoc.copy(docType = "receipt")
        // f1 created earlier (epoch 1) should win over f2 (epoch 2)
        val folders = listOf(
            Folder("f2", "Receipts 2", "folder", "docType=receipt", Instant.ofEpochMilli(2), Instant.EPOCH),
            Folder("f1", "Receipts 1", "folder", "docType=receipt", Instant.ofEpochMilli(1), Instant.EPOCH),
        )
        assertEquals("f1", engine.findMatchingFolder(doc, folders))
    }

    @Test
    fun `findMatchingFolder skips non-matching folders and returns correct one`() {
        val doc = baseDoc.copy(docType = "id")
        val folders = listOf(
            folder("f1", "Receipts", "docType=receipt"),
            folder("f2", "IDs", "docType=id"),
        )
        assertEquals("f2", engine.findMatchingFolder(doc, folders))
    }

    @Test
    fun `evaluate id card rule`() {
        val doc = baseDoc.copy(docType = "id")
        assert(engine.evaluate("docType=id", doc))
    }

    @Test
    fun `evaluate whiteboard rule`() {
        val doc = baseDoc.copy(docType = "whiteboard")
        assert(engine.evaluate("docType=whiteboard", doc))
    }

    @Test
    fun `evaluate business card rule`() {
        val doc = baseDoc.copy(docType = "business_card")
        assert(engine.evaluate("docType=business_card", doc))
    }

    @Test
    fun `evaluate empty value returns false`() {
        val doc = baseDoc.copy(docType = "receipt")
        assert(!engine.evaluate("docType=", doc))
    }
}
