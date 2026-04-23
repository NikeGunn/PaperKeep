package app.paperkeep.core.imaging

import android.graphics.Bitmap

/**
 * Book scan (two-page spread) processor (3B.5).
 *
 * - Splits a two-page spread bitmap at the exact horizontal centre.
 * - Applies a simple perspective-correction stub to each half (Phase 3B:
 *   no native OpenCV — each half is returned as-is; Phase 4C will swap in
 *   real DewarpNet).
 * - Always returns a list of exactly 2 bitmaps: [left page, right page].
 */
object BookScanProcessor {

    /**
     * Split [spread] at centre-x, dewarp each half, and return both pages.
     *
     * @param spread The two-page scan bitmap. Width must be > 1.
     * @return A list of exactly 2 bitmaps — `[leftPage, rightPage]`.
     */
    fun split(spread: Bitmap): List<Bitmap> {
        val centreX = spread.width / 2

        val leftPage = Bitmap.createBitmap(spread, 0, 0, centreX, spread.height)
        val rightPage = Bitmap.createBitmap(spread, centreX, 0, spread.width - centreX, spread.height)

        return listOf(dewarp(leftPage), dewarp(rightPage))
    }

    /**
     * Perspective-correction stub.
     *
     * For Phase 3B this is a no-op: the bitmap is returned unchanged.
     * A real DewarpNet model will replace this in Phase 4C.
     *
     * The output always has positive width and height.
     */
    internal fun dewarp(page: Bitmap): Bitmap {
        // Stub: return a fresh copy (ensures output is not the same object)
        return page.copy(page.config ?: Bitmap.Config.ARGB_8888, false)
    }
}
