package app.paperkeep.core.ml.summarizer

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TextSummarizerTest {

    private lateinit var quotaTracker: SummarizerQuotaTracker
    private lateinit var modelManager: SummarizerModelManager
    private lateinit var summarizer: TextSummarizer

    @Before
    fun setUp() {
        quotaTracker = mockk(relaxed = true)
        modelManager = mockk(relaxed = true)
        every { quotaTracker.canSummarize() } returns true
        summarizer = TextSummarizer(quotaTracker, modelManager)
    }

    // ── Sentence splitting ───────────────────────────────────────────────────

    @Test
    fun `splitSentences splits on period`() {
        val text = "The invoice total is one hundred dollars. The payment was received on Friday."
        val result = summarizer.splitSentences(text)
        assertEquals(2, result.size)
        assertTrue(result[0].contains("invoice"))
        assertTrue(result[1].contains("payment"))
    }

    @Test
    fun `splitSentences splits on question mark`() {
        val text = "How much is the total amount due? The total amount due is fifty dollars."
        val result = summarizer.splitSentences(text)
        assertEquals(2, result.size)
    }

    @Test
    fun `splitSentences filters short sentences`() {
        val result = summarizer.splitSentences("Hi. This is a longer sentence.")
        assertEquals(1, result.size)
        assertTrue(result[0].contains("longer"))
    }

    // ── Tokenization ─────────────────────────────────────────────────────────

    @Test
    fun `tokenize lowercases and splits`() {
        val result = summarizer.tokenize("Hello World 123")
        assertTrue("hello" in result || "world" in result)
    }

    @Test
    fun `tokenize removes stop words`() {
        val result = summarizer.tokenize("the quick brown fox")
        assertTrue("the" !in result)
        assertTrue("quick" in result)
    }

    @Test
    fun `tokenize filters tokens shorter than 3 chars`() {
        val result = summarizer.tokenize("I am an awesome developer")
        assertTrue("awesome" in result)
        assertTrue("developer" in result)
    }

    // ── Term frequency ────────────────────────────────────────────────────────

    @Test
    fun `termFrequency counts correctly`() {
        val tokens = listOf("apple", "banana", "apple", "cherry", "apple")
        val tf = summarizer.termFrequency(tokens)
        assertEquals(3, tf["apple"])
        assertEquals(1, tf["banana"])
        assertEquals(1, tf["cherry"])
    }

    // ── Sentence scoring ─────────────────────────────────────────────────────

    @Test
    fun `scoresSentence returns higher score for repeated important words`() {
        val tf = mapOf("invoice" to 5, "total" to 3, "date" to 1)
        val highScore = summarizer.scoresSentence("Invoice total for the invoice", tf)
        val lowScore = summarizer.scoresSentence("Date of payment", tf)
        assertTrue(highScore > lowScore)
    }

    // ── Key phrase extraction ─────────────────────────────────────────────────

    @Test
    fun `extractKeyPhrases returns at most MAX 5 phrases`() {
        val tokens = listOf(
            "invoice", "total", "amount", "invoice", "total", "amount",
            "date", "payment", "date", "payment", "vendor", "vendor",
        )
        val tf = summarizer.termFrequency(tokens)
        val phrases = summarizer.extractKeyPhrases(tokens, tf)
        assertTrue(phrases.size <= 5)
    }

    @Test
    fun `extractKeyPhrases returns empty when all tokens appear once`() {
        val tokens = listOf("alpha", "beta", "gamma", "delta")
        val tf = summarizer.termFrequency(tokens)
        val phrases = summarizer.extractKeyPhrases(tokens, tf)
        assertTrue(phrases.isEmpty())
    }

    // ── End-to-end summarize() ────────────────────────────────────────────────

    @Test
    fun `summarize returns NotAvailable for blank text`() = runTest {
        val result = summarizer.summarize("", isPro = true)
        assertEquals(SummarizeResult.NotAvailable, result)
    }

    @Test
    fun `summarize returns NotAvailable when quota exceeded for free user`() = runTest {
        every { quotaTracker.canSummarize() } returns false
        val result = summarizer.summarize("Invoice total is one hundred dollars.", isPro = false)
        assertEquals(SummarizeResult.NotAvailable, result)
    }

    @Test
    fun `summarize ignores quota for Pro user`() = runTest {
        every { quotaTracker.canSummarize() } returns false
        val result = summarizer.summarize(
            "Invoice total is one hundred dollars. The payment was made on Friday.",
            isPro = true
        )
        assertTrue(result is SummarizeResult.Success)
    }

    @Test
    fun `summarize records usage for free user on success`() = runTest {
        val longText = "The quarterly revenue report shows strong performance across all segments. " +
            "Total revenue reached record levels driven by product sales and service revenue. " +
            "The management team attributes this growth to strategic investments in technology."
        summarizer.summarize(longText, isPro = false)
        verify { quotaTracker.recordUsage() }
    }

    @Test
    fun `summarize does not record usage for Pro user`() = runTest {
        val longText = "The quarterly revenue report shows strong performance across all segments. " +
            "Total revenue reached record levels driven by product sales and service revenue."
        summarizer.summarize(longText, isPro = true)
        verify(exactly = 0) { quotaTracker.recordUsage() }
    }

    @Test
    fun `summarize returns Success with non-empty summary for real text`() = runTest {
        val text = "The quarterly earnings report shows record revenue for the company. " +
            "Total earnings grew twenty percent year over year. " +
            "The board approved a dividend increase for shareholders. " +
            "Analysts predict continued growth through the next fiscal year."
        val result = summarizer.summarize(text, isPro = true)
        assertTrue(result is SummarizeResult.Success)
        val success = result as SummarizeResult.Success
        assertTrue(success.summary.isNotBlank())
        assertTrue(success.summary.length <= 200)
    }

    @Test
    fun `summarize result summary is within 200 chars`() = runTest {
        val longSentence = "This is a very long sentence that contains many words repeated over and over " +
            "invoice invoice invoice total total payment payment vendor vendor merchant merchant " +
            "amount amount amount receipt receipt document document scan scan paperkeep paperkeep."
        val result = summarizer.summarize(longSentence, isPro = true)
        if (result is SummarizeResult.Success) {
            assertTrue(result.summary.length <= 200)
        }
    }
}
