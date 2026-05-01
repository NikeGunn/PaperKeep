package app.paperkeep.core.data.autorule

import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.Folder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates folder auto-rules against a document and returns the first matching folder ID.
 *
 * Rule syntax: "docType=<value>" — e.g. "docType=receipt", "docType=id".
 * Future rules can extend the DSL without breaking existing rule strings.
 *
 * Evaluation is pure and synchronous — no I/O, no side effects.
 */
@Singleton
class AutoRuleEngine @Inject constructor() {

    /**
     * Given a [document] and the current list of [folders], return the ID of the
     * first folder whose auto-rule matches, or null if no folder matches.
     *
     * Folders are evaluated in insertion order (oldest-first by createdAt) so that
     * the user's intended primary folder wins when multiple folders share the same rule.
     */
    fun findMatchingFolder(document: Document, folders: List<Folder>): String? {
        val sortedFolders = folders.sortedBy { it.createdAt }
        for (folder in sortedFolders) {
            val rule = folder.autoRule ?: continue
            if (evaluate(rule, document)) return folder.id
        }
        return null
    }

    /**
     * Evaluate a single rule expression against a document.
     * Returns true if the document matches the rule.
     */
    internal fun evaluate(rule: String, document: Document): Boolean {
        val trimmed = rule.trim()

        // "docType=<value>" — exact match against Document.docType
        if (trimmed.startsWith("docType=")) {
            val expected = trimmed.removePrefix("docType=").trim()
            return expected.isNotEmpty() && document.docType == expected
        }

        // Unknown rule format — fail open (no match) so user documents don't get lost.
        return false
    }
}
