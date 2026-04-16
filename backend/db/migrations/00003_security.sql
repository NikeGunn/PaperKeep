-- +goose Up
-- +goose StatementBegin

-- =========================================================================
-- 3A.1 — Conflict backups
-- =========================================================================
CREATE TABLE IF NOT EXISTS conflict_backups (
    id          BIGSERIAL PRIMARY KEY,
    account_id  BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    doc_id      TEXT   NOT NULL,
    client_data BYTEA  NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conflict_backups_account_id ON conflict_backups(account_id);

-- =========================================================================
-- 3A.5 — Account lockout fields
-- =========================================================================
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

-- =========================================================================
-- 3A.6 — Password reset tokens
-- =========================================================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    account_id  BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash  BYTEA  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_account_id ON password_reset_tokens(account_id);

-- =========================================================================
-- 3A.7 — Audit events
-- =========================================================================
CREATE TABLE IF NOT EXISTS audit_events (
    id          BIGSERIAL PRIMARY KEY,
    account_id  BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    event_type  TEXT   NOT NULL,
    metadata    JSONB,
    ip_hash     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_events_account_created ON audit_events(account_id, created_at DESC);

-- =========================================================================
-- 3A.9 — Session listing needs device_label already present in refresh_tokens
-- (column already exists from migration 0001)
-- =========================================================================

-- =========================================================================
-- 3A.10 — Soft account deletion (grace period)
-- =========================================================================
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin

ALTER TABLE accounts DROP COLUMN IF EXISTS deleted_at;
DROP INDEX IF EXISTS idx_refresh_tokens_uuid;
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS uuid;
ALTER TABLE accounts DROP COLUMN IF EXISTS locked_until;
ALTER TABLE accounts DROP COLUMN IF EXISTS failed_login_attempts;

DROP TABLE IF EXISTS audit_events;
DROP TABLE IF EXISTS password_reset_tokens;
DROP TABLE IF EXISTS conflict_backups;

-- +goose StatementEnd
