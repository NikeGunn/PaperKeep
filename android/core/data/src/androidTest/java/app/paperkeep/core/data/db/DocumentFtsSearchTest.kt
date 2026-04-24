package app.paperkeep.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Tests for 2B.4: FTS4 table creation, FTS row insertion, and basic search.
 *
 * NOTE: Robolectric's sqlite4java does not support FTS4 MATCH queries, so the
 * actual MATCH-based search is tested at the repository level (with mocks) and
 * in [DocumentRepositorySearchTest]. These tests verify:
 *  - The FTS4 virtual table is created correctly in the migration
 *  - FTS rows can be inserted without error
 *  - The DAO's raw query methods accept valid SimpleSQLiteQuery objects
 *  - Documents can be inserted and found by simple non-FTS queries (as
 *    a sanity check for the in-memory DB setup used by search integration)
 *
 * Full FTS MATCH tests are run as instrumented tests (see androidTest/).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DocumentFtsSearchTest {

    private lateinit var db: PaperkeepDatabase
    private lateinit var dao: DocumentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PaperkeepDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // For a fresh in-memory DB at version 3, the FTS table must be
                    // created manually (MIGRATION_2_3 only runs on upgrade from v2).
                    db.execSQL(
                        "CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts " +
                            "USING fts4(docId, title, ocrText)"
                    )
                }
            })
            .build()
        dao = db.documentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── FTS table creation and row insertion ──────────────────────────────────

    @Test
    fun ftsTable_isCreated_withoutError() = runTest {
        // Inserting a FTS row must not throw
        val doc = buildDoc("d1", "Invoice April 2024")
        dao.insertDocument(doc)

        // Insert FTS row using the raw query helper
        insertFtsRow("d1", "Invoice April 2024", "")
        // If we got here without exception, FTS table creation succeeded
    }

    @Test
    fun updateFtsRowRaw_insertsFtsRow_withoutError() = runTest {
        val doc = buildDoc("d2", "Contract 2024")
        dao.insertDocument(doc)

        val page = buildPage("pg-d2", "d2", 0, "signed by John Doe")
        dao.insertPage(page)

        // The raw query upsert must not throw
        val sql = SimpleSQLiteQuery(
            """
            INSERT OR REPLACE INTO documents_fts(docId, title, ocrText)
            SELECT d.id,
                   d.title,
                   COALESCE((SELECT GROUP_CONCAT(p.ocrText, ' ')
                             FROM pages p
                             WHERE p.documentId = d.id
                               AND p.ocrText IS NOT NULL), '')
            FROM documents d
            WHERE d.id = ?
            """.trimIndent(),
            arrayOf<Any>("d2"),
        )
        dao.updateFtsRowRaw(sql)
    }

    @Test
    fun rebuildFtsIndexRaw_executesWithoutError() = runTest {
        val doc = buildDoc("d3", "Test Document")
        dao.insertDocument(doc)
        insertFtsRow("d3", "Test Document", "")

        val sql = SimpleSQLiteQuery("INSERT INTO documents_fts(documents_fts) VALUES('rebuild')")
        dao.rebuildFtsIndexRaw(sql)
    }

    // ── Search returns empty for no matching documents ─────────────────────────

    @Test
    fun searchDocumentsRaw_returnsEmpty_whenNoDocumentsInTable() = runTest {
        // Build query that should match nothing
        val sql = searchQuery("ZZZNOMATCH*")
        val results = dao.searchDocumentsRaw(sql)
        assertTrue(results.isEmpty())
    }

    // ── FTS insert + search round-trip (title only) ───────────────────────────

    @Test
    fun searchDocumentsRaw_findsByTitle_afterFtsInsert() = runTest {
        val doc = buildDoc("search-doc-1", "Invoice April 2024")
        dao.insertDocument(doc)
        insertFtsRow("search-doc-1", "Invoice April 2024", "")

        val results = dao.searchDocumentsRaw(searchQuery("Invoice*"))

        assertEquals(1, results.size)
        assertEquals("search-doc-1", results[0].document.id)
    }

    @Test
    fun searchDocumentsRaw_findsByOcrText_afterFtsInsert() = runTest {
        val doc = buildDoc("search-doc-2", "Scanned Page")
        dao.insertDocument(doc)
        val page = buildPage("pg2", "search-doc-2", 0, "Total Amount Due 1234")
        dao.insertPage(page)
        // Insert FTS row with OCR text included
        insertFtsRow("search-doc-2", "Scanned Page", "Total Amount Due 1234")

        val results = dao.searchDocumentsRaw(searchQuery("Amount*"))

        assertEquals(1, results.size)
        assertEquals("search-doc-2", results[0].document.id)
    }

    @Test
    fun searchDocumentsRaw_returnsEmpty_forNonMatchingQuery() = runTest {
        val doc = buildDoc("search-doc-3", "Receipt 2024")
        dao.insertDocument(doc)
        insertFtsRow("search-doc-3", "Receipt 2024", "")

        val results = dao.searchDocumentsRaw(searchQuery("ZZZNOMATCH*"))

        assertTrue(results.isEmpty())
    }

    @Test
    fun searchDocumentsRaw_findsMultipleDocs_whenBothTitlesMatch() = runTest {
        val doc1 = buildDoc("d-inv-1", "Tax Invoice 2023")
        val doc2 = buildDoc("d-inv-2", "Invoice April 2024")
        val doc3 = buildDoc("d-receipt", "Receipt 2024")
        dao.insertDocument(doc1)
        dao.insertDocument(doc2)
        dao.insertDocument(doc3)
        insertFtsRow("d-inv-1", "Tax Invoice 2023", "")
        insertFtsRow("d-inv-2", "Invoice April 2024", "")
        insertFtsRow("d-receipt", "Receipt 2024", "")

        val results = dao.searchDocumentsRaw(searchQuery("Invoice*"))

        assertEquals(2, results.size)
        val ids = results.map { it.document.id }.toSet()
        assertTrue("d-inv-1" in ids)
        assertTrue("d-inv-2" in ids)
    }

    @Test
    fun searchDocumentsRaw_handlesEmptyOcrText_gracefully() = runTest {
        val doc = buildDoc("d-no-ocr", "Important Contract")
        dao.insertDocument(doc)
        insertFtsRow("d-no-ocr", "Important Contract", "")

        val results = dao.searchDocumentsRaw(searchQuery("Contract*"))

        assertEquals(1, results.size)
        assertEquals("d-no-ocr", results[0].document.id)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun searchQuery(ftsExpr: String): SimpleSQLiteQuery = SimpleSQLiteQuery(
        """
        SELECT d.* FROM documents d
        INNER JOIN documents_fts ON documents_fts.docId = d.id
        WHERE documents_fts MATCH ?
        ORDER BY d.createdAt DESC
        """.trimIndent(),
        arrayOf<Any>(ftsExpr),
    )

    /** Insert directly into documents_fts (bypassing the GROUP_CONCAT subquery). */
    private suspend fun insertFtsRow(docId: String, title: String, ocrText: String) {
        dao.updateFtsRowRaw(
            SimpleSQLiteQuery(
                "INSERT OR REPLACE INTO documents_fts(docId, title, ocrText) VALUES(?, ?, ?)",
                arrayOf<Any>(docId, title, ocrText),
            )
        )
    }

    private fun buildDoc(
        id: String,
        title: String = "Document $id",
    ) = DocumentEntity(
        id = id,
        title = title,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        folderId = null,
        pageCount = 1,
        colorTag = null,
    )

    private fun buildPage(
        id: String,
        documentId: String,
        pageIndex: Int,
        ocrText: String? = null,
    ) = PageEntity(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        encryptedImagePath = "/enc/$id.enc",
        encryptedThumbPath = "/enc/$id.thumb.enc",
        ocrText = ocrText,
        width = 2560,
        height = 1920,
        filter = "original",
    )
}
