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
    BLACK_AND_WHITE("bw", "B&W");

    companion object {
        /** Look up a filter by its persisted [key], defaulting to [ORIGINAL]. */
        fun fromKey(key: String): ImageFilter =
            entries.firstOrNull { it.key == key } ?: ORIGINAL
    }
}
