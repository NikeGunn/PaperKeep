package app.paperkeep.core.pdf

import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Production implementation of [PdfDocumentWrapper] backed by
 * [android.graphics.pdf.PdfDocument].
 */
class AndroidPdfDocumentWrapper : PdfDocumentWrapper {

    private val document = PdfDocument()
    private var currentPage: PdfDocument.Page? = null

    override fun startPage(pageNumber: Int, widthPt: Int, heightPt: Int): Canvas {
        val pageInfo = PdfDocument.PageInfo.Builder(widthPt, heightPt, pageNumber).create()
        val page = document.startPage(pageInfo)
        currentPage = page
        return page.canvas
    }

    override fun finishPage() {
        currentPage?.let { document.finishPage(it) }
        currentPage = null
    }

    override fun writeAndClose(out: OutputStream) {
        document.writeTo(out)
        document.close()
    }
}
