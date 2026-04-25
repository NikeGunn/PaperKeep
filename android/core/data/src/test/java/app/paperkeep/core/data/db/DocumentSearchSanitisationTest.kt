package app.paperkeep.core.data.db

import app.paperkeep.core.data.fts.OcrFtsIndex
import app.paperkeep.core.data.fts.OcrFtsKeyProvider
import app.paperkeep.core.data.repository.DocumentRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

/**
 * Unit tests for the FTS query sanitisation logic in [DocumentRepository] (2B.4).
 *
 * These tests run on the JVM without a real SQLite database (no FTS4 needed).
 * They verify that [DocumentRepository.sanitiseFtsQuery] produces safe FTS4
 * MATCH tokens from raw user input.
 *
 * Full FTS4 integration tests (CREATE VIRTUAL TABLE, MATCH queries) are in
 * the instrumented [DocumentFtsSearchTest] which runs on a real Android device.
 */
class DocumentSearchSanitisationTest {

    private val dao = mockk<DocumentDao>(relaxed = true)
    private val ocrFtsIndex = run {
        val key = KeyGenerator.getInstance("HmacSHA256").generateKey()
        val provider = mockk<OcrFtsKeyProvider>()
        every { provider.getKey() } returns key
        OcrFtsIndex(provider)
    }
    private val repo = DocumentRepository(dao, ocrFtsIndex)

    // ── Empty / blank input ───────────────────────────────────────────────────

    @Test
    fun sanitise_blankInput_returnsBlank() {
        assertTrue(repo.sanitiseFtsQuery("   ").isBlank())
    }

    @Test
    fun sanitise_emptyString_returnsBlank() {
        assertTrue(repo.sanitiseFtsQuery("").isBlank())
    }

    // ── Normal words ──────────────────────────────────────────────────────────

    @Test
    fun sanitise_singleWord_appendsPrefixStar() {
        assertEquals("invoice*", repo.sanitiseFtsQuery("invoice"))
    }

    @Test
    fun sanitise_multipleWords_eachGetsPrefix() {
        assertEquals("invoice* april*", repo.sanitiseFtsQuery("invoice april"))
    }

    @Test
    fun sanitise_extraWhitespace_isNormalised() {
        assertEquals("hello* world*", repo.sanitiseFtsQuery("  hello   world  "))
    }

    @Test
    fun sanitise_tabSeparated_isNormalised() {
        assertEquals("foo* bar*", repo.sanitiseFtsQuery("foo\tbar"))
    }

    // ── Special / injection characters ────────────────────────────────────────

    @Test
    fun sanitise_singleQuote_isStripped() {
        // ' stripped; short tokens (< 3 chars: 'OR'=2, '1'=1) also dropped
        val result = repo.sanitiseFtsQuery("' OR invoice 1")
        assertEquals("invoice*", result)
    }

    @Test
    fun sanitise_parenAndSemicolon_stripped() {
        assertEquals("helloworld*", repo.sanitiseFtsQuery("hello(world);"))
    }

    @Test
    fun sanitise_doubleQuote_stripped() {
        assertEquals("term*", repo.sanitiseFtsQuery("\"term\""))
    }

    @Test
    fun sanitise_ftsCaretOperator_stripped() {
        // ^ would be an FTS near operator — strip it
        assertEquals("helloworld*", repo.sanitiseFtsQuery("hello^world"))
    }

    // ── Preserved characters ──────────────────────────────────────────────────

    @Test
    fun sanitise_hyphenatedWord_kept() {
        assertEquals("self-employed*", repo.sanitiseFtsQuery("self-employed"))
    }

    @Test
    fun sanitise_numbers_kept() {
        assertEquals("2024*", repo.sanitiseFtsQuery("2024"))
    }

    @Test
    fun sanitise_alphanumericMix_kept() {
        assertEquals("invoice42*", repo.sanitiseFtsQuery("invoice42"))
    }
}
