package com.scanvault.core.data.db

/**
 * The FTS4 virtual table `documents_fts` is created manually in Migration 2→3
 * rather than via Room's @Fts4 annotation. This avoids compile-time schema
 * validation failures when Room's KSP processor tests the virtual table in an
 * environment without FTS4 support.
 *
 * Schema (created in [ScanVaultDatabase.MIGRATION_2_3]):
 * ```
 * CREATE VIRTUAL TABLE documents_fts USING fts4(docId, title, ocrText)
 * ```
 *
 * The table is populated and maintained via raw SQL queries in [DocumentDao]:
 *   - [DocumentDao.updateFtsRow] — sync one document's FTS row
 *   - [DocumentDao.rebuildFtsIndex] — rebuild entire index
 *   - [DocumentDao.searchDocuments] — MATCH query that joins back to documents
 *
 * There is no entity class for this table; it does NOT appear in
 * [ScanVaultDatabase.entities].
 */
@Suppress("unused")
object DocumentFts {
    const val TABLE = "documents_fts"
}
