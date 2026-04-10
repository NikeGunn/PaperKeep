-- name: CreateAccount :one
INSERT INTO accounts (
    email, auth_hash, auth_params,
    wrapped_key, kdf_salt, kdf_params
)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: GetAccountByEmail :one
SELECT * FROM accounts
WHERE email = $1
LIMIT 1;

-- name: GetAccountByUUID :one
SELECT * FROM accounts
WHERE uuid = $1
LIMIT 1;

-- name: GetAccountByID :one
SELECT * FROM accounts
WHERE id = $1
LIMIT 1;

-- name: UpdateAccountEmailVerified :one
UPDATE accounts
SET email_verified = TRUE,
    updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: UpdateAccountAuthHash :one
UPDATE accounts
SET auth_hash = $2,
    auth_params = $3,
    updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: UpdateAccountStatus :one
UPDATE accounts
SET status = $2,
    updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: DeleteAccount :exec
DELETE FROM accounts WHERE id = $1;
