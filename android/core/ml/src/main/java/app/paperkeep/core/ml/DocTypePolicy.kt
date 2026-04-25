package app.paperkeep.core.ml

import app.paperkeep.core.imaging.ImageFilter

/**
 * Maps a [DocumentType] to the recommended imaging defaults (P3.1).
 *
 * These recommendations are applied automatically after classification and can
 * be overridden by the user on the crop screen.
 *
 * [recommendedFilter] — which [ImageFilter] produces the best scan quality.
 * [aspectRatioHint]   — target aspect ratio (height/width) for crop presentation.
 *                       Null = keep original aspect ratio.
 */
data class DocTypePolicy(
    val documentType: DocumentType,
    val recommendedFilter: ImageFilter,
    val aspectRatioHint: Float?,
    val recommendationLabel: String,
)

object DocTypePolicies {

    private val policies = mapOf(
        DocumentType.DOCUMENT to DocTypePolicy(
            documentType = DocumentType.DOCUMENT,
            recommendedFilter = ImageFilter.AUTO,
            aspectRatioHint = A4_ASPECT,
            recommendationLabel = "Auto-enhance + A4 crop",
        ),
        DocumentType.A4_DOCUMENT to DocTypePolicy(
            documentType = DocumentType.A4_DOCUMENT,
            recommendedFilter = ImageFilter.AUTO,
            aspectRatioHint = A4_ASPECT,
            recommendationLabel = "Auto-enhance + A4 crop",
        ),
        DocumentType.RECEIPT to DocTypePolicy(
            documentType = DocumentType.RECEIPT,
            recommendedFilter = ImageFilter.BLACK_AND_WHITE,
            aspectRatioHint = RECEIPT_ASPECT,
            recommendationLabel = "B&W + tall crop",
        ),
        DocumentType.ID_CARD to DocTypePolicy(
            documentType = DocumentType.ID_CARD,
            recommendedFilter = ImageFilter.MAGIC_COLOR,
            aspectRatioHint = ID_CARD_ASPECT,
            recommendationLabel = "Magic Color + ID crop",
        ),
        DocumentType.WHITEBOARD to DocTypePolicy(
            documentType = DocumentType.WHITEBOARD,
            recommendedFilter = ImageFilter.AUTO,
            aspectRatioHint = WHITEBOARD_ASPECT,
            recommendationLabel = "Auto-enhance + wide crop",
        ),
        DocumentType.BOOK_PAGE to DocTypePolicy(
            documentType = DocumentType.BOOK_PAGE,
            recommendedFilter = ImageFilter.GRAYSCALE,
            aspectRatioHint = BOOK_ASPECT,
            recommendationLabel = "Grayscale + A5 crop",
        ),
        DocumentType.UNKNOWN to DocTypePolicy(
            documentType = DocumentType.UNKNOWN,
            recommendedFilter = ImageFilter.ORIGINAL,
            aspectRatioHint = null,
            recommendationLabel = "No recommendation",
        ),
    )

    /** Look up the [DocTypePolicy] for [type], falling back to the UNKNOWN policy. */
    fun forType(type: DocumentType): DocTypePolicy =
        policies[type] ?: policies.getValue(DocumentType.UNKNOWN)

    /** Convenience: recommended filter for [type]. */
    fun recommendedFilter(type: DocumentType): ImageFilter =
        forType(type).recommendedFilter

    // ── Standard aspect ratios ─────────────────────────────────────────────────

    /** A4: 297 mm / 210 mm ≈ 1.414 */
    const val A4_ASPECT = 297f / 210f

    /** Receipt: tall register roll, approximate 3:1 */
    const val RECEIPT_ASPECT = 3.0f

    /** ID / credit card: ISO 7810 ID-1 = 85.6 × 54 mm → landscape 0.631 */
    const val ID_CARD_ASPECT = 54f / 85.6f

    /** Whiteboard: typical 4:3 landscape */
    const val WHITEBOARD_ASPECT = 3f / 4f

    /** Book A5: 210 × 148 mm → portrait ≈ 1.419 */
    const val BOOK_ASPECT = 210f / 148f
}
