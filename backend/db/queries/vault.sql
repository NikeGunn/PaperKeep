-- =========================================================================
-- vault_documents queries
-- =========================================================================

-- name: CreateDocument :one
INSERT INTO vault_documents (uuid, account_id, encrypted_metadata, metadata_nonce, page_count)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: GetDocumentByUUID :one
SELECT * FROM vault_documents
WHERE uuid = $1 AND account_id = $2 AND deleted_at IS NULL;

-- name: GetDocumentByUUIDIncludeDeleted :one
SELECT * FROM vault_documents
WHERE uuid = $1 AND account_id = $2;

-- name: ListDocuments :many
SELECT * FROM vault_documents
WHERE account_id = $1
  AND deleted_at IS NULL
  AND (sqlc.narg(after_uuid)::uuid IS NULL OR uuid > sqlc.narg(after_uuid)::uuid)
ORDER BY uuid ASC
LIMIT $2;

-- name: UpdateDocumentMetadata :one
UPDATE vault_documents
SET encrypted_metadata = $3,
    metadata_nonce      = $4,
    version             = version + 1,
    updated_at          = NOW()
WHERE uuid = $1
  AND account_id = $2
  AND version = $5
  AND deleted_at IS NULL
RETURNING *;

-- name: SoftDeleteDocument :one
UPDATE vault_documents
SET deleted_at = NOW(),
    updated_at  = NOW()
WHERE uuid = $1
  AND account_id = $2
  AND deleted_at IS NULL
RETURNING *;

-- name: IncrementDocumentPageCount :one
UPDATE vault_documents
SET page_count        = page_count + 1,
    total_size_bytes  = total_size_bytes + $3,
    updated_at        = NOW()
WHERE id = $1
  AND account_id = $2
RETURNING *;

-- name: GetExpiredSoftDeletedDocuments :many
SELECT * FROM vault_documents
WHERE deleted_at IS NOT NULL
  AND deleted_at < NOW() - INTERVAL '30 days'
LIMIT $1;

-- name: HardDeleteDocument :exec
DELETE FROM vault_documents WHERE id = $1;

-- =========================================================================
-- vault_pages queries
-- =========================================================================

-- name: CreatePage :one
INSERT INTO vault_pages (uuid, document_id, page_index, r2_key, encrypted_size, checksum)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: GetPageByUUID :one
SELECT p.*, d.account_id
FROM vault_pages p
JOIN vault_documents d ON d.id = p.document_id
WHERE p.uuid = $1
  AND p.deleted_at IS NULL;

-- name: GetPagesByDocument :many
SELECT * FROM vault_pages
WHERE document_id = $1 AND deleted_at IS NULL
ORDER BY page_index ASC;

-- name: ConfirmPage :one
UPDATE vault_pages
SET status = 'confirmed',
    version = version + 1
WHERE uuid = $1 AND deleted_at IS NULL
RETURNING *;

-- name: SoftDeletePage :one
UPDATE vault_pages
SET deleted_at = NOW()
WHERE uuid = $1 AND deleted_at IS NULL
RETURNING *;

-- name: GetExpiredSoftDeletedPages :many
SELECT p.*, d.account_id
FROM vault_pages p
JOIN vault_documents d ON d.id = p.document_id
WHERE p.deleted_at IS NOT NULL
  AND p.deleted_at < NOW() - INTERVAL '30 days'
LIMIT $1;

-- name: HardDeletePage :exec
DELETE FROM vault_pages WHERE id = $1;

-- name: PageIndexExists :one
SELECT EXISTS (
    SELECT 1 FROM vault_pages
    WHERE document_id = $1
      AND page_index = $2
      AND deleted_at IS NULL
) AS exists;

-- =========================================================================
-- account_quotas queries
-- =========================================================================

-- name: GetOrCreateQuota :one
INSERT INTO account_quotas (account_id)
VALUES ($1)
ON CONFLICT (account_id) DO UPDATE
    SET updated_at = account_quotas.updated_at  -- no-op update to get the row back
RETURNING *;

-- name: GetQuota :one
SELECT * FROM account_quotas WHERE account_id = $1;

-- name: IncrementQuotaUsage :one
UPDATE account_quotas
SET storage_bytes_used = storage_bytes_used + $2,
    documents_count    = documents_count + $3,
    updated_at         = NOW()
WHERE account_id = $1
RETURNING *;

-- name: DecrementQuotaUsage :one
UPDATE account_quotas
SET storage_bytes_used = GREATEST(0, storage_bytes_used - $2),
    documents_count    = GREATEST(0, documents_count - $3),
    updated_at         = NOW()
WHERE account_id = $1
RETURNING *;

-- =========================================================================
-- Sync manifest query
-- =========================================================================

-- name: GetManifestSince :many
SELECT uuid, version, updated_at, deleted_at
FROM vault_documents
WHERE account_id = $1
  AND updated_at > $2
  AND (sqlc.narg(after_uuid)::uuid IS NULL OR uuid > sqlc.narg(after_uuid)::uuid)
ORDER BY uuid ASC
LIMIT $3;
