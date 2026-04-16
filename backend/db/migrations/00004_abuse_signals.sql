-- +goose Up
-- +goose StatementBegin

-- =========================================================================
-- 3A.11 — Abuse signals (stored in audit_events with event_type='abuse_signal')
-- No new table needed; uses audit_events. This migration is a placeholder.
-- =========================================================================

-- =========================================================================
-- 4A.5 — Performance indexes
-- =========================================================================
CREATE INDEX IF NOT EXISTS idx_accounts_email ON accounts(email);
CREATE INDEX IF NOT EXISTS idx_vault_documents_account_deleted ON vault_documents(account_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_vault_pages_document_id ON vault_pages(document_id);

-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin

DROP INDEX IF EXISTS idx_vault_pages_document_id;
DROP INDEX IF EXISTS idx_vault_documents_account_deleted;
DROP INDEX IF EXISTS idx_accounts_email;

-- +goose StatementEnd
