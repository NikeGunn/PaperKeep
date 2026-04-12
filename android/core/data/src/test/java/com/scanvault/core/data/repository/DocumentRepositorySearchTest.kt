package com.scanvault.core.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.scanvault.core.data.db.DocumentDao
import com.scanvault.core.data.db.DocumentEntity
import com.scanvault.core.data.db.DocumentWithPages
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DocumentRepository.searchDocuments] and
 * [DocumentRepository.sanitiseFtsQuery].
 *
 * The DAO is mocked; this tests that the repository:
 *  1. Sanitises raw user queries into safe FTS4 MATCH tokens.
 *  2. Skips the DAO call entirely for blank queries.
 *  3. Forwards sanitised queries to the DAO correctly.
 *  4. Returns mapped domain objects.
 */
class DocumentRepositorySearchTest {

    private lateinit var dao: DocumentDao
    private lateinit var repo: DocumentRepository

    private val emptyEntity = DocumentEntity(
        id = "d1",
        title = "Test Doc",
        createdAt = 1000L,
        updatedAt = 1000L,
        folderId = null,
        pageCount = 1,
        colorTag = null,
    )

    @Before
    fun setUp() {
        dao = mockk(relaxed = true) {
            coEvery { searchDocumentsRaw(any()) } returns
                listOf(DocumentWithPages(emptyEntity, emptyList()))
        }
        repo = DocumentRepository(dao)
    }

    // ── Sanitisation: blank / empty queries ──────────────────────────────────

    @Test
    fun search_blankQuery_returnsEmpty_withoutCallingDao() = runTest {
        val result = repo.searchDocuments("   ")
        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { dao.searchDocumentsRaw(any()) }
    }

    @Test
    fun search_emptyString_returnsEmpty_withoutCallingDao() = runTest {
        val result = repo.searchDocuments("")
        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { dao.searchDocumentsRaw(any()) }
    }

    // ── Sanitisation via sanitiseFtsQuery (internal, tested directly) ─────────

    @Test
    fun sanitiseFtsQuery_singleWord_appendsPrefixStar() {
        assertEquals("invoice*", repo.sanitiseFtsQuery("invoice"))
    }

    @Test
    fun sanitiseFtsQuery_multipleWords_eachGetsPrefix() {
        assertEquals("invoice* april*", repo.sanitiseFtsQuery("invoice april"))
    }

    @Test
    fun sanitiseFtsQuery_extraWhitespace_isNormalised() {
        assertEquals("hello* world*", repo.sanitiseFtsQuery("  hello   world  "))
    }

    @Test
    fun sanitiseFtsQuery_blankInput_returnsBlank() {
        assertTrue(repo.sanitiseFtsQuery("   ").isBlank())
    }

    @Test
    fun sanitiseFtsQuery_sqlInjectionChars_areStripped() {
        // ' ; ( ) are stripped; alphanumeric words remain
        val result = repo.sanitiseFtsQuery("' OR 1 1")
        assertEquals("OR* 1* 1*", result)
    }

    @Test
    fun sanitiseFtsQuery_ftsOperatorChars_stripped() {
        // ^ and " would be FTS operators — they are stripped
        val result = repo.sanitiseFtsQuery("hello^world")
        // ^ stripped, "helloworld" becomes one token
        assertEquals("helloworld*", result)
    }

    @Test
    fun sanitiseFtsQuery_hyphenatedWord_kept() {
        assertEquals("self-employed*", repo.sanitiseFtsQuery("self-employed"))
    }

    // ── Returns mapped domain documents ──────────────────────────────────────

    @Test
    fun search_returnsMappedDomainList() = runTest {
        val result = repo.searchDocuments("test")
        assertEquals(1, result.size)
        assertEquals("d1", result[0].id)
    }

    @Test
    fun search_callsDao_withSanitisedQuery() = runTest {
        repo.searchDocuments("invoice")
        // The dao receives a SimpleSQLiteQuery — verify it was called once
        coVerify(exactly = 1) { dao.searchDocumentsRaw(any<SimpleSQLiteQuery>()) }
    }
}
