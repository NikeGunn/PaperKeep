-- name: CreateRefreshToken :one
INSERT INTO refresh_tokens (
    account_id, token_hash, device_label, expires_at
)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: GetRefreshTokenByHash :one
SELECT * FROM refresh_tokens
WHERE token_hash = $1
LIMIT 1;

-- name: UpdateRefreshTokenLastUsed :one
UPDATE refresh_tokens
SET last_used_at = NOW()
WHERE id = $1
RETURNING *;

-- name: RevokeRefreshToken :exec
UPDATE refresh_tokens
SET revoked_at = NOW()
WHERE id = $1;

-- name: RevokeAllRefreshTokensForAccount :exec
UPDATE refresh_tokens
SET revoked_at = NOW()
WHERE account_id = $1
  AND revoked_at IS NULL;

-- name: ListActiveRefreshTokensForAccount :many
SELECT * FROM refresh_tokens
WHERE account_id = $1
  AND revoked_at IS NULL
  AND expires_at > NOW()
ORDER BY last_used_at DESC;

-- name: DeleteExpiredRefreshTokens :exec
DELETE FROM refresh_tokens
WHERE expires_at < NOW() - INTERVAL '30 days';

-- name: CreateEmailVerificationToken :one
INSERT INTO email_verification_tokens (
    account_id, token_hash, expires_at
)
VALUES ($1, $2, $3)
RETURNING *;

-- name: GetEmailVerificationTokenByHash :one
SELECT * FROM email_verification_tokens
WHERE token_hash = $1
LIMIT 1;

-- name: MarkEmailVerificationTokenUsed :exec
UPDATE email_verification_tokens
SET used_at = NOW()
WHERE id = $1;
