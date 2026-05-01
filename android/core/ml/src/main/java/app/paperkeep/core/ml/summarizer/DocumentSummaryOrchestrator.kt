package app.paperkeep.core.ml.summarizer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level orchestrator that summarizes a document given its OCR text.
 *
 * Runs on [Dispatchers.Default].
 * All results are sealed [SummarizeResult] — never throws to callers.
 */
@Singleton
class DocumentSummaryOrchestrator @Inject constructor(
    private val summarizer: TextSummarizer,
) {
    suspend fun summarizeDocument(ocrText: String, isPro: Boolean): SummarizeResult =
        withContext(Dispatchers.Default) {
            summarizer.summarize(ocrText, isPro)
        }
}
