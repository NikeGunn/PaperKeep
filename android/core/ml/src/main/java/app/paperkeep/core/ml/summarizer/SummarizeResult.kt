package app.paperkeep.core.ml.summarizer

/**
 * Result of an on-device summarization run.
 *
 * The summarizer produces a 1-sentence summary and up to 5 key phrases.
 * On failure or timeout, [NotAvailable] is returned — never throws.
 */
sealed interface SummarizeResult {
    data class Success(
        val summary: String,
        val keyPhrases: List<String>,
    ) : SummarizeResult

    /** Returned on timeout, model unavailable, or insufficient text. */
    data object NotAvailable : SummarizeResult
}
