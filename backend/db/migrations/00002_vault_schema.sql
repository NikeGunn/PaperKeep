-- +goose Up
-- +goose StatementBegin

-- vault_documents: one row per encrypted document.
-- The server stores opaque encrypted blobs — it cannot read titles, folders, or contents.
CREATE TABLE vault_documents (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE,           -- Client-generated UUIDv7
    account_id          BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    encrypted_metadata  BYTEA NOT NULL,                 -- Title, folder, tags — all encrypted client-side
    metadata_nonce      BYTEA NOT NULL,                 -- AES-GCM nonce for metadata decryption
    version             BIGINT NOT NULL DEFAULT 1,      -- Monotonic counter for optimistic concurrency
    page_count          INT NOT NULL DEFAULT 0,
    total_size_bytes    BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ                     -- NULL = live; purged 30 days after soft-delete
);

CREATE INDEX idx_vault_documents_account
    ON vault_documents (account_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_vault_documents_account_updated
    ON vault_documents (account_id, updated_at DESC)
    WHERE deleted_at IS NULL;

-- vault_pages: one blob per scanned page.
-- r2_key is the canonical object path in R2: vault/<account>/<doc>/<page>.enc
CREATE TABLE vault_pages (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE,
    document_id     BIGINT NOT NULL REFERENCES vault_documents(id) ON DELETE CASCADE,
    page_index      INT NOT NULL,
    r2_key          TEXT NOT NULL,                  -- "vault/<account_uuid>/<doc_uuid>/<page_uuid>.enc"
    encrypted_size  BIGINT NOT NULL,
    checksum        BYTEA NOT NULL,                 -- SHA-256 of the ciphertext
    status          TEXT NOT NULL DEFAULT 'pending'
                      CHECK (status IN ('pending', 'confirmed')),
    version         BIGINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_vault_pages_document
    ON vault_pages (document_id)
    WHERE deleted_at IS NULL;

-- Unique active page index per document (allows reuse after soft-delete)
CREATE UNIQUE INDEX idx_vault_pages_document_index
    ON vault_pages (document_id, page_index)
    WHERE deleted_at IS NULL;

-- account_quotas: per-account storage limits.
-- Row is created with default free-tier limits when the account first uploads.
CREATE TABLE account_quotas (
    account_id            BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    storage_bytes_used    BIGINT NOT NULL DEFAULT 0,
    storage_bytes_limit   BIGINT NOT NULL DEFAULT 524288000,  -- 500 MB free tier
    documents_count       INT NOT NULL DEFAULT 0,
    documents_limit       INT NOT NULL DEFAULT 1000,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin

DROP TABLE IF EXISTS account_quotas;
DROP TABLE IF EXISTS vault_pages;
DROP TABLE IF EXISTS vault_documents;

-- +goose StatementEnd
