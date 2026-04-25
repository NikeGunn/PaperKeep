package app.paperkeep.core.data.fts

import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the HMAC-SHA256 FTS token scheme from §6.4.
 *
 * Instead of storing plaintext OCR terms in the FTS4 virtual table, we store
 * HMAC(K_search, normalize(token)) in hex. This prevents an attacker who can
 * read the database from learning document content from FTS tokens alone.
 *
 * Search flow:
 *   1. User types query → tokenize & normalize → HMAC each token
 *   2. MATCH against page_ocr_fts using the hex tokens
 *   3. Row contains pageId → join back to documents
 *
 * Normalization:
 *   - Unicode NFC fold → lowercase → strip non-alphanumeric
 *   - Drop tokens shorter than 3 chars (noise / prefix fragments)
 *
 * The K_search key lives in Keystore and is injected via [OcrFtsKeyProvider].
 * In tests a software key is injected via [TestOcrFtsKeyProvider].
 */
@Singleton
class OcrFtsIndex @Inject constructor(
    private val keyProvider: OcrFtsKeyProvider,
) {

    /** Compute the HMAC-hex token for [rawToken]. Returns null if the token is too short. */
    fun hmacToken(rawToken: String): String? {
        val normalized = normalize(rawToken)
        if (normalized.length < MIN_TOKEN_LEN) return null
        return hmac(keyProvider.getKey(), normalized)
    }

    /**
     * Convert [ocrText] into a space-joined string of HMAC-hex tokens, ready for
     * INSERT into the `page_ocr_fts` virtual table.
     */
    fun indexText(ocrText: String): String =
        tokenize(ocrText)
            .mapNotNull { hmacToken(it) }
            .distinct()
            .joinToString(" ")

    /**
     * Convert a user search query into an FTS4 MATCH expression.
     *
     * Each token becomes `<hmac>*` (prefix search so partial typing works).
     * Returns blank string if no valid tokens remain (caller should skip query).
     */
    fun queryExpression(rawQuery: String): String =
        tokenize(rawQuery)
            .mapNotNull { hmacToken(it) }
            .distinct()
            .joinToString(" ") { "$it*" }

    // ── Internal helpers ───────────────────────────────────────────────────────

    internal fun normalize(token: String): String =
        token.lowercase()
            .replace(Regex("[^a-z0-9]"), "")

    internal fun tokenize(text: String): List<String> =
        text.split(Regex("\\s+"))
            .map { it.replace(Regex("[^a-zA-Z0-9]"), "") }
            .filter { it.length >= MIN_TOKEN_LEN }

    private fun hmac(key: SecretKey, data: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(key)
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    companion object {
        const val MIN_TOKEN_LEN = 3
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
