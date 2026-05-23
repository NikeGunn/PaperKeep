package app.paperkeep.core.imaging

/**
 * The 5 image filters available in the scan pipeline (2B.6).
 *
 * Each value has a stable [key] that is persisted in [PageEntity.filter].
 * Display [label] is used in the filter strip UI.
 */
enum class ImageFilter(val key: String, val label: String) {
    /** Unprocessed perspective-corrected image. */
    ORIGINAL("original", "Original"),
    /**
     * Auto-enhance: auto-levels white balance + slight contrast boost.
     * Best for standard documents under neutral lighting.
     */
    AUTO("auto", "Auto"),
    /**
     * Magic Color: preserve colours, enhance contrast and saturation.
     * Best for coloured diagrams, forms with colour coding.
     */
    MAGIC_COLOR("magic_color", "Magic Color"),
    /**
     * Grayscale: convert to grey, auto-levels.
     * Best for printed text, reducing file size.
     */
    GRAYSCALE("grayscale", "Grayscale"),
    /**
     * Black & White: adaptive threshold binarisation.
     * Best for high-contrast text, removing background noise.
     */
    BLACK_AND_WHITE("bw", "B&W"),

    /**
     * Document: CamScanner-style "Enhanced Document" — pushes paper to clean
     * white, ink to crisp black, slight desaturation. The most-used filter
     * in mainstream scanner apps; pick this for routine receipts/letters.
     */
    DOCUMENT("document", "Document"),

    /**
     * Lighten: brightness boost without crushing whites. Best for faded ink,
     * carbon-copy receipts, and dim indoor captures.
     */
    LIGHTEN("lighten", "Lighten"),

    /**
     * Vivid: high saturation + contrast. Best for coloured diagrams, art
     * scans, sticky-notes, and forms with colour-coded fields.
     */
    VIVID("vivid", "Vivid"),

    /**
     * Whiteboard: glare-suppressing white-balance push. Best for marker-on-
     * whiteboard photos where you want the background to read as pure white.
     */
    WHITEBOARD("whiteboard", "Whiteboard"),

    /**
     * Sepia: warm brown tint over a desaturated base. Vintage / archival
     * aesthetic for letters, historical documents, journaling.
     */
    SEPIA("sepia", "Sepia");

    companion object {
        /** Look up a filter by its persisted [key], defaulting to [ORIGINAL]. */
        fun fromKey(key: String): ImageFilter =
            entries.firstOrNull { it.key == key } ?: ORIGINAL
    }
}
