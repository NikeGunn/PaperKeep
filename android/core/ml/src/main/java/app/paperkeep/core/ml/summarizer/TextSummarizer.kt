package app.paperkeep.core.ml.summarizer

import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device text summarizer for P5.1.
 *
 * Design constraints (§5 Phase 5):
 *  - 5-second hard timeout → silent [SummarizeResult.NotAvailable] on expiry
 *  - No network calls ever; model lives in filesDir/models/
 *  - Free tier: 3 summaries/day (enforced by [SummarizerQuotaTracker])
 *  - Pro unlocks unlimited summaries
 *
 * The TFLite model file (downloaded on first opt-in via [SummarizerModelManager])
 * is deliberately NOT bundled in the APK to avoid bloating the install size.
 * When the model is absent, the heuristic extractive summarizer runs instead —
 * it has no download requirement and never returns null.
 *
 * Heuristic algorithm (TextRank-lite):
 *  1. Split text into sentences.
 *  2. Score each sentence by TF of content words (stop-words excluded).
 *  3. Return the highest-scoring sentence as the summary.
 *  4. Extract top-3 to top-5 bigram/unigram key phrases by TF.
 */
@Singleton
class TextSummarizer @Inject constructor(
    private val quotaTracker: SummarizerQuotaTracker,
    private val modelManager: SummarizerModelManager,
) {

    /**
     * Summarize [text] and return a [SummarizeResult].
     *
     * Caller must be on [kotlinx.coroutines.Dispatchers.Default].
     * Never throws — degrades to [SummarizeResult.NotAvailable] on any error.
     *
     * @param isPro  True if the user owns Pro (bypasses daily quota).
     */
    suspend fun summarize(text: String, isPro: Boolean = false): SummarizeResult {
        if (text.isBlank()) return SummarizeResult.NotAvailable
        if (!isPro && !quotaTracker.canSummarize()) return SummarizeResult.NotAvailable

        return withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                val result = doSummarize(text)
                if (!isPro) quotaTracker.recordUsage()
                result
            }.getOrDefault(SummarizeResult.NotAvailable)
        } ?: SummarizeResult.NotAvailable
    }

    private fun doSummarize(text: String): SummarizeResult {
        val sentences = splitSentences(text)
        if (sentences.isEmpty()) return SummarizeResult.NotAvailable

        val tokens = tokenize(text)
        val tf = termFrequency(tokens)

        val summary = sentences
            .maxByOrNull { sentence -> scoresSentence(sentence, tf) }
            ?.trim()
            ?.take(MAX_SUMMARY_CHARS)
            ?: sentences.first().trim().take(MAX_SUMMARY_CHARS)

        val keyPhrases = extractKeyPhrases(tokens, tf)
        return SummarizeResult.Success(summary = summary, keyPhrases = keyPhrases)
    }

    // ── Sentence splitting ────────────────────────────────────────────────────

    internal fun splitSentences(text: String): List<String> =
        text.split(SENTENCE_SPLIT_REGEX)
            .map { it.trim().trimEnd('.', '!', '?') }
            .filter { it.length >= MIN_SENTENCE_CHARS }

    // ── Tokenization ──────────────────────────────────────────────────────────

    internal fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(TOKEN_SPLIT_REGEX)
            .filter { it.length >= 3 && it !in STOP_WORDS }

    // ── TF scoring ────────────────────────────────────────────────────────────

    internal fun termFrequency(tokens: List<String>): Map<String, Int> {
        val tf = mutableMapOf<String, Int>()
        for (token in tokens) tf[token] = (tf[token] ?: 0) + 1
        return tf
    }

    internal fun scoresSentence(sentence: String, tf: Map<String, Int>): Double {
        val words = sentence.lowercase().split(TOKEN_SPLIT_REGEX).filter { it.length >= 3 }
        if (words.isEmpty()) return 0.0
        return words.sumOf { (tf[it] ?: 0).toDouble() } / words.size
    }

    // ── Key phrase extraction ─────────────────────────────────────────────────

    internal fun extractKeyPhrases(tokens: List<String>, tf: Map<String, Int>): List<String> {
        val unigrams = tf.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }

        val bigrams = mutableMapOf<String, Int>()
        for (i in 0 until tokens.size - 1) {
            val bigram = "${tokens[i]} ${tokens[i + 1]}"
            bigrams[bigram] = (bigrams[bigram] ?: 0) + 1
        }
        val topBigrams = bigrams.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(4)
            .map { it.key }

        return (topBigrams + unigrams)
            .distinct()
            .take(MAX_KEY_PHRASES)
    }

    companion object {
        private const val TIMEOUT_MS = 5_000L
        private const val MAX_SUMMARY_CHARS = 200
        private const val MIN_SENTENCE_CHARS = 20
        private const val MAX_KEY_PHRASES = 5

        private val SENTENCE_SPLIT_REGEX = Regex("""[.!?]+\s+""")
        private val TOKEN_SPLIT_REGEX = Regex("""[^a-z0-9']+""")

        internal val STOP_WORDS = setOf(
            "the", "and", "for", "are", "but", "not", "you", "all", "any",
            "can", "had", "her", "was", "one", "our", "out", "day", "get",
            "has", "him", "his", "how", "its", "may", "new", "now", "old",
            "see", "two", "who", "did", "did", "let", "put", "say", "she",
            "too", "use", "that", "this", "with", "have", "from", "they",
            "will", "been", "each", "into", "more", "some", "than", "them",
            "then", "were", "when", "your", "also", "into", "over", "such",
            "just", "like", "much", "only", "same", "well", "back", "been",
            "come", "here", "made", "most", "said", "take", "than", "them",
            "very", "what", "which", "while", "would", "about", "after",
            "could", "other", "their", "there", "these", "those", "three",
            "under", "where", "which", "while", "whose", "between",
        )
    }
}
