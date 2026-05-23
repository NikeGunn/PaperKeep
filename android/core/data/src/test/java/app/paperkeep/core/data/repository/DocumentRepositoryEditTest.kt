package app.paperkeep.core.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import app.paperkeep.core.data.db.DocumentEntity
import app.paperkeep.core.data.db.PageEntity
import app.paperkeep.core.data.db.PaperkeepDatabase
import app.paperkeep.core.data.fts.OcrFtsIndex
import app.paperkeep.core.data.autorule.AutoRuleEngine
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the Edit toolbar's new repository surface: per-page title,
 * per-page filter, and reorderPages. Uses a real Room in-memory DB so the
 * two-phase reorder write actually exercises the SQL.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DocumentRepositoryEditTest {

    private lateinit var db: PaperkeepDatabase
    private lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PaperkeepDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        val fts: OcrFtsIndex = mockk(relaxed = true)
        val rules: AutoRuleEngine = mockk(relaxed = true)
        repository = DocumentRepository(
            dao = db.documentDao(),
            ocrFtsIndex = fts,
            autoRuleEngine = rules,
        )
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun seedDoc(docId: String, pageIds: List<String>) {
        db.documentDao().insertDocument(
            DocumentEntity(
                id = docId, title = "T", createdAt = 0L, updatedAt = 0L,
                folderId = null, pageCount = pageIds.size, colorTag = null,
            )
        )
        pageIds.forEachIndexed { i, id ->
            db.documentDao().insertPage(
                PageEntity(
                    id = id,
                    documentId = docId,
                    pageIndex = i,
                    encryptedImagePath = "/$id.enc",
                    encryptedThumbPath = "/$id.thumb.enc",
                    width = 100,
                    height = 100,
                )
            )
        }
    }

    @Test
    fun setPageTitle_writesNonBlank_clearsBlank() = runTest {
        seedDoc("d1", listOf("p1"))
        repository.setPageTitle("p1", "  Cover  ")
        assertEquals("Cover", db.documentDao().getPageById("p1")!!.title)

        repository.setPageTitle("p1", "   ")
        assertNull(db.documentDao().getPageById("p1")!!.title)

        repository.setPageTitle("p1", null)
        assertNull(db.documentDao().getPageById("p1")!!.title)
    }

    @Test
    fun setPageFilter_persists() = runTest {
        seedDoc("d1", listOf("p1"))
        repository.setPageFilter("p1", "magic_color")
        assertEquals("magic_color", db.documentDao().getPageById("p1")!!.filter)
    }

    @Test
    fun reorderPages_persistsNewOrder() = runTest {
        seedDoc("d1", listOf("a", "b", "c"))
        repository.reorderPages("d1", listOf("c", "a", "b"))
        val pages = db.documentDao().getPagesForDocument("d1")
        assertEquals(listOf("c", "a", "b"), pages.map { it.id })
        assertEquals(listOf(0, 1, 2), pages.map { it.pageIndex })
    }

    @Test
    fun reorderPages_singlePageNoOp_keepsIndexZero() = runTest {
        seedDoc("d1", listOf("only"))
        repository.reorderPages("d1", listOf("only"))
        val pages = db.documentDao().getPagesForDocument("d1")
        assertEquals(listOf("only"), pages.map { it.id })
        assertEquals(0, pages.first().pageIndex)
    }
}
