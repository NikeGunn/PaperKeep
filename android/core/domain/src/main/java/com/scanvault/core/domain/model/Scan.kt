package com.scanvault.core.domain.model

import java.time.Instant

/**
 * Domain entity representing a scanned document.
 * Room entity (ScanEntity) is in :core:data and maps to this.
 */
data class Scan(
    val id: String,
    val title: String,
    val pageCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
