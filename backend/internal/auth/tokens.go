// Package auth provides Argon2id password hashing and Paseto v4 token operations.
package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"aidanwoods.dev/go-paseto"
	"golang.org/x/crypto/argon2"
)

// -------------------------------------------------------------------------
// Constants
// -------------------------------------------------------------------------

const (
	// AccessTokenTTL is the lifetime of a Paseto access token.
	AccessTokenTTL = 15 * time.Minute
	// RefreshTokenTTL is how long a refresh token is valid.
	RefreshTokenTTL = 30 * 24 * time.Hour
	// RefreshTokenBytes is the number of random bytes in a raw refresh token.
	RefreshTokenBytes = 32
	// VerificationTokenBytes is the number of random bytes in a verification token.
	VerificationTokenBytes = 32
	// VerificationTokenTTL is the expiry for email verification tokens.
	VerificationTokenTTL = 24 * time.Hour

	// argon2KeyLen is the output key length from Argon2id (in bytes).
	argon2KeyLen = 32
	// argon2SaltLen is the number of random salt bytes generated per hash.
	argon2SaltLen = 16
)

// -------------------------------------------------------------------------
// Argon2id hashing
// -------------------------------------------------------------------------

// HashPassword hashes raw (already client-side hashed) auth material using
// the server-side Argon2id parameters and a fresh random salt.
// Returns the full encoded hash string: $argon2id$v=19$m=...,t=...,p=...$<salt>$<hash>
func HashPassword(raw string, timeCost, memory uint32, threads uint8) (string, error) {
	salt := make([]byte, argon2SaltLen)
	if _, err := rand.Read(salt); err != nil {
		return "", fmt.Errorf("generating salt: %w", err)
	}
	hash := argon2.IDKey([]byte(raw), salt, timeCost, memory, threads, argon2KeyLen)
	encoded := fmt.Sprintf(
		"$argon2id$v=%d$m=%d,t=%d,p=%d$%s$%s",
		argon2.Version,
		memory, timeCost, threads,
		base64.RawStdEncoding.EncodeToString(salt),
		base64.RawStdEncoding.EncodeToString(hash),
	)
	return encoded, nil
}

// VerifyPassword checks raw against an encoded Argon2id hash string.
// Uses constant-time comparison to prevent timing attacks.
func VerifyPassword(raw, encoded string) (bool, error) {
	timeCost, memory, threads, salt, storedHash, err := decodeArgon2Hash(encoded)
	if err != nil {
		return false, fmt.Errorf("decoding hash: %w", err)
	}
	candidate := argon2.IDKey([]byte(raw), salt, timeCost, memory, threads, argon2KeyLen)
	match := subtle.ConstantTimeCompare(candidate, storedHash) == 1
	return match, nil
}

// DummyVerify runs Argon2id against a fixed dummy hash to consume the same
// time as a real verification. Call this when the user does not exist to
// prevent user-enumeration via timing differences.
var dummyHash, _ = HashPassword("__dummy__", 3, 65536, 4)

func DummyVerify() {
	_, _ = VerifyPassword("__dummy__", dummyHash)
}

// decodeArgon2Hash parses the encoded Argon2id string produced by HashPassword.
func decodeArgon2Hash(encoded string) (timeCost, memory uint32, threads uint8, salt, hash []byte, err error) {
	var version int
	var saltB64, hashB64 string

	n, parseErr := fmt.Sscanf(encoded,
		"$argon2id$v=%d$m=%d,t=%d,p=%d$%s",
		&version, &memory, &timeCost, &threads, &saltB64)
	if parseErr != nil || n < 5 {
		err = errors.New("invalid argon2id hash format")
		return
	}

	// The hash field is after the last '$'
	for i := len(encoded) - 1; i >= 0; i-- {
		if encoded[i] == '$' {
			hashB64 = encoded[i+1:]
			saltB64 = encoded[:i]
			for j := len(saltB64) - 1; j >= 0; j-- {
				if saltB64[j] == '$' {
					saltB64 = saltB64[j+1:]
					break
				}
			}
			break
		}
	}

	salt, err = base64.RawStdEncoding.DecodeString(saltB64)
	if err != nil {
		err = fmt.Errorf("decoding salt: %w", err)
		return
	}
	hash, err = base64.RawStdEncoding.DecodeString(hashB64)
	if err != nil {
		err = fmt.Errorf("decoding hash: %w", err)
		return
	}
	return
}

// -------------------------------------------------------------------------
// Paseto v4 local tokens
// -------------------------------------------------------------------------

// Claims is the set of claims embedded in a Paseto access token.
type Claims struct {
	AccountID   int64  `json:"account_id"`
	AccountUUID string `json:"account_uuid"`
	Email       string `json:"email"`
}

// TokenPair holds an access token and its associated refresh token.
type TokenPair struct {
	AccessToken  string
	RefreshToken string // raw (not hashed) — send to client, hash before DB
}

// PasetoMaker handles token creation and verification.
type PasetoMaker struct {
	secretKey paseto.V4SymmetricKey
}

// NewPasetoMaker creates a PasetoMaker from a base64-encoded 32-byte key.
func NewPasetoMaker(keyBase64 string) (*PasetoMaker, error) {
	raw, err := base64.StdEncoding.DecodeString(keyBase64)
	if err != nil {
		// Try raw standard (no padding)
		raw, err = base64.RawStdEncoding.DecodeString(keyBase64)
		if err != nil {
			return nil, fmt.Errorf("decoding PASETO_KEY: %w", err)
		}
	}
	if len(raw) != 32 {
		return nil, fmt.Errorf("PASETO_KEY must be exactly 32 bytes, got %d", len(raw))
	}
	key, err := paseto.V4SymmetricKeyFromBytes(raw)
	if err != nil {
		return nil, fmt.Errorf("building symmetric key: %w", err)
	}
	return &PasetoMaker{secretKey: key}, nil
}

// MakeAccessToken mints a 15-minute Paseto v4 local access token.
func (m *PasetoMaker) MakeAccessToken(c Claims) (string, error) {
	token := paseto.NewToken()
	token.SetIssuedAt(time.Now())
	token.SetNotBefore(time.Now())
	token.SetExpiration(time.Now().Add(AccessTokenTTL))
	if err := token.Set("account_id", c.AccountID); err != nil {
		return "", err
	}
	if err := token.Set("account_uuid", c.AccountUUID); err != nil {
		return "", err
	}
	if err := token.Set("email", c.Email); err != nil {
		return "", err
	}
	encrypted := token.V4Encrypt(m.secretKey, nil)
	return encrypted, nil
}

// VerifyAccessToken decrypts and validates a Paseto v4 local token.
// Returns the Claims on success, error on any failure.
func (m *PasetoMaker) VerifyAccessToken(tokenStr string) (*Claims, error) {
	parser := paseto.NewParser()
	parser.AddRule(paseto.NotExpired())
	parser.AddRule(paseto.ValidAt(time.Now()))

	token, err := parser.ParseV4Local(m.secretKey, tokenStr, nil)
	if err != nil {
		return nil, fmt.Errorf("invalid token: %w", err)
	}

	var accountID int64
	if err := token.Get("account_id", &accountID); err != nil {
		return nil, fmt.Errorf("missing account_id claim: %w", err)
	}
	var accountUUID string
	if err := token.Get("account_uuid", &accountUUID); err != nil {
		return nil, fmt.Errorf("missing account_uuid claim: %w", err)
	}
	var email string
	if err := token.Get("email", &email); err != nil {
		return nil, fmt.Errorf("missing email claim: %w", err)
	}

	return &Claims{
		AccountID:   accountID,
		AccountUUID: accountUUID,
		Email:       email,
	}, nil
}

// -------------------------------------------------------------------------
// Refresh token helpers
// -------------------------------------------------------------------------

// GenerateRefreshToken returns a cryptographically random refresh token
// and its SHA-256 hash (for storing in the DB).
func GenerateRefreshToken() (raw string, hash []byte, err error) {
	b := make([]byte, RefreshTokenBytes)
	if _, err = rand.Read(b); err != nil {
		return "", nil, fmt.Errorf("generating refresh token: %w", err)
	}
	raw = hex.EncodeToString(b)
	sum := sha256.Sum256([]byte(raw))
	return raw, sum[:], nil
}

// HashRefreshToken returns the SHA-256 hash of a raw refresh token string.
func HashRefreshToken(raw string) []byte {
	sum := sha256.Sum256([]byte(raw))
	return sum[:]
}

// GenerateVerificationToken returns a cryptographically random verification
// token string and its SHA-256 hash.
func GenerateVerificationToken() (raw string, hash []byte, err error) {
	b := make([]byte, VerificationTokenBytes)
	if _, err = rand.Read(b); err != nil {
		return "", nil, fmt.Errorf("generating verification token: %w", err)
	}
	raw = hex.EncodeToString(b)
	sum := sha256.Sum256([]byte(raw))
	return raw, sum[:], nil
}
