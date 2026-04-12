package com.scanvault.feature.scanner.batch

import android.graphics.Bitmap

/**
 * A single captured page within a batch multi-page scan session.
 *
 * [id] is a UUID assigned at capture time, stable across reorders.
 * [bitmap] holds the perspective-corrected, filter-applied image in memory
 * until the session is saved to disk.
 */
data class BatchPage(
    val id: String,
    val bitmap: Bitmap,
)
