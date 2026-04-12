package com.scanvault.core.pdf

import android.graphics.Canvas

/**
 * Thin abstraction over [android.graphics.pdf.PdfDocument] so that
 * [PdfExporter] can be tested without needing a real Robolectric PDF renderer.
 *
 * The real implementation delegates to [android.graphics.pdf.PdfDocument].
 * Test fakes can write raw bytes (e.g. a valid PDF header) instead.
 */
interface PdfDocumentWrapper {

    /** Add a new page of [widthPt] × [heightPt] points and return its [Canvas]. */
    fun startPage(pageNumber: Int, widthPt: Int, heightPt: Int): Canvas

    /** Finalise the current page (must be called before [startPage] again). */
    fun finishPage()

    /** Write the completed document to [out] and close all resources. */
    fun writeAndClose(out: java.io.OutputStream)
}
