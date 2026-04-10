-- +goose Up
-- +goose StatementBegin

-- Enable citext extension for case-insensitive email comparison
CREATE EXTENSION IF NOT EXISTS citext;

-- accounts: one row per registered user
-- Internal BIGSERIAL id, externally exposed UUID (gen_random_uuid).
-- Zero-knowledge design: server stores wrapped_key and kdf_salt (opaque blobs
-- the server cannot decrypt or use). auth_hash is Argon2id of the password.
CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    email           CITEXT NOT NULL UNIQUE,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    auth_hash       TEXT NOT NULL,              -- Argon2id(password)
    auth_params     JSONB NOT NULL,             -- Argon2id params (for future rotation)
    wrapped_key     BYTEA NOT NULL,             -- Client's K_master wrapped with K_encrypt
    kdf_salt        BYTEA NOT NULL,             -- Salt for client-side KDF
    kdf_params      JSONB NOT NULL,             -- Client-side Argon2id params
    status          TEXT NOT NULL DEFAULT 'active'
                      CHECK (status IN ('active', 'suspended', 'pending_deletion')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_email  ON accounts (email);
CREATE INDEX idx_accounts_status ON accounts (status);

-- refresh_tokens: opaque long-lived tokens, hashed before storage.
-- token_hash = SHA-256(raw_token). The raw token is never stored.
CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash      BYTEA NOT NULL UNIQUE,      -- SHA-256 of the opaque token
    device_label    TEXT,                        -- "Pixel 6a", user-editable
    last_used_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,                -- NULL = active
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_account ON refresh_tokens (account_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- email_verification_tokens: single-use tokens sent in verification links.
-- token_hash = SHA-256(raw_token).
CREATE TABLE email_verification_tokens (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash      BYTEA NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,                -- NULL = not yet used
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_ver_tokens_account ON email_verification_tokens (account_id);

-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin

DROP TABLE IF EXISTS email_verification_tokens;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS accounts;
DROP EXTENSION IF EXISTS citext;

-- +goose StatementEnd
