package com.scanvault.feature.reader.signature

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Overlays a signature bitmap onto a PDF page bitmap (3B.6).
 *
 * Both source bitmaps are composited in-memory. The result is a new ARGB_8888
 * bitmap of the same dimensions as [page].
 */
object PlaceSignature {

    /**
     * Overlay [signaturePng] PNG bytes onto [page] at [destRect].
     *
     * @param page         The base page bitmap. Returned size is identical.
     * @param signaturePng Raw PNG bytes of the signature.
     * @param destRect     Where to place the signature on [page].
     *                     Defaults to the bottom-right corner (25% wide, 10% tall).
     * @return A new bitmap with the signature composited over the page.
     */
    fun overlay(
        page: Bitmap,
        signaturePng: ByteArray,
        destRect: RectF = defaultRect(page),
    ): Bitmap {
        val sigBitmap = BitmapFactory.decodeByteArray(signaturePng, 0, signaturePng.size)
            ?: return page.copy(page.config ?: Bitmap.Config.ARGB_8888, false)

        val output = page.copy(page.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        canvas.drawBitmap(sigBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun defaultRect(page: Bitmap): RectF {
        val left = page.width * 0.70f
        val top = page.height * 0.85f
        val right = page.width.toFloat()
        val bottom = page.height.toFloat()
        return RectF(left, top, right, bottom)
    }
}
