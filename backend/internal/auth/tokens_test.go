package auth_test

import (
	"strings"
	"testing"
	"time"

	"github.com/nikhil/scanvault-api/internal/auth"
)

// -------------------------------------------------------------------------
// Argon2id hashing
// -------------------------------------------------------------------------

func TestHashPassword_ProducesEncodedString(t *testing.T) {
	h, err := auth.HashPassword("mysecret", 1, 8192, 1)
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}
	if !strings.HasPrefix(h, "$argon2id$") {
		t.Errorf("hash %q does not start with $argon2id$", h)
	}
}

func TestHashPassword_UniquePerCall(t *testing.T) {
	h1, _ := auth.HashPassword("same", 1, 8192, 1)
	h2, _ := auth.HashPassword("same", 1, 8192, 1)
	if h1 == h2 {
		t.Error("two hashes of the same value should differ (different salts)")
	}
}

func TestVerifyPassword_CorrectInput(t *testing.T) {
	raw := "correct-password"
	h, _ := auth.HashPassword(raw, 1, 8192, 1)
	ok, err := auth.VerifyPassword(raw, h)
	if err != nil {
		t.Fatalf("VerifyPassword: %v", err)
	}
	if !ok {
		t.Error("expected verification to succeed")
	}
}

func TestVerifyPassword_WrongInput(t *testing.T) {
	h, _ := auth.HashPassword("correct", 1, 8192, 1)
	ok, err := auth.VerifyPassword("wrong", h)
	if err != nil {
		t.Fatalf("VerifyPassword: %v", err)
	}
	if ok {
		t.Error("expected verification to fail for wrong input")
	}
}

func TestVerifyPassword_InvalidHash(t *testing.T) {
	_, err := auth.VerifyPassword("anything", "notavalidhash")
	if err == nil {
		t.Error("expected error for invalid hash format")
	}
}

func TestDummyVerify_DoesNotPanic(t *testing.T) {
	// Just ensure it runs without panicking
	auth.DummyVerify()
}

// -------------------------------------------------------------------------
// Paseto token generation and verification
// -------------------------------------------------------------------------

func newMaker(t *testing.T) *auth.PasetoMaker {
	t.Helper()
	// 32-byte base64 key
	m, err := auth.NewPasetoMaker("dGhpcy1pcy1hLTMyYnl0ZS10ZXN0LWtleS1oZXJlISE=")
	if err != nil {
		t.Fatalf("NewPasetoMaker: %v", err)
	}
	return m
}

func TestNewPasetoMaker_ValidKey(t *testing.T) {
	if m := newMaker(t); m == nil {
		t.Error("expected non-nil maker")
	}
}

func TestNewPasetoMaker_TooShortKey(t *testing.T) {
	_, err := auth.NewPasetoMaker("dGVzdA==") // 4 bytes — too short
	if err == nil {
		t.Error("expected error for key shorter than 32 bytes")
	}
}

func TestNewPasetoMaker_InvalidBase64(t *testing.T) {
	_, err := auth.NewPasetoMaker("not!!!valid---base64")
	if err == nil {
		t.Error("expected error for invalid base64")
	}
}

func TestMakeAccessToken_ReturnsNonEmptyString(t *testing.T) {
	m := newMaker(t)
	tok, err := m.MakeAccessToken(auth.Claims{
		AccountID:   42,
		AccountUUID: "uuid-42",
		Email:       "test@example.com",
	})
	if err != nil {
		t.Fatalf("MakeAccessToken: %v", err)
	}
	if tok == "" {
		t.Error("expected non-empty token")
	}
}

func TestVerifyAccessToken_ValidToken(t *testing.T) {
	m := newMaker(t)
	claims := auth.Claims{
		AccountID:   7,
		AccountUUID: "uuid-7",
		Email:       "hello@world.com",
	}
	tok, _ := m.MakeAccessToken(claims)

	got, err := m.VerifyAccessToken(tok)
	if err != nil {
		t.Fatalf("VerifyAccessToken: %v", err)
	}
	if got.AccountID != claims.AccountID {
		t.Errorf("AccountID = %d, want %d", got.AccountID, claims.AccountID)
	}
	if got.AccountUUID != claims.AccountUUID {
		t.Errorf("AccountUUID = %q, want %q", got.AccountUUID, claims.AccountUUID)
	}
	if got.Email != claims.Email {
		t.Errorf("Email = %q, want %q", got.Email, claims.Email)
	}
}

func TestVerifyAccessToken_TamperedToken(t *testing.T) {
	m := newMaker(t)
	tok, _ := m.MakeAccessToken(auth.Claims{AccountID: 1, AccountUUID: "u", Email: "e@e.com"})
	// Flip the last character
	tampered := tok[:len(tok)-1] + "X"
	_, err := m.VerifyAccessToken(tampered)
	if err == nil {
		t.Error("expected error for tampered token")
	}
}

func TestVerifyAccessToken_RandomGarbage(t *testing.T) {
	m := newMaker(t)
	_, err := m.VerifyAccessToken("v4.local.thisisjustrandomgarbage")
	if err == nil {
		t.Error("expected error for random garbage token")
	}
}

func TestVerifyAccessToken_WrongKey(t *testing.T) {
	m1 := newMaker(t)
	// Different 32-byte key: "anotherkeystring32bytes!!here!!!" (32 bytes)
	m2, err := auth.NewPasetoMaker("YW5vdGhlcmtleXN0cmluZzMyYnl0ZXMhIWhlcmUhISE=")
	if err != nil {
		t.Fatalf("NewPasetoMaker (m2): %v", err)
	}
	tok, _ := m1.MakeAccessToken(auth.Claims{AccountID: 1, AccountUUID: "u", Email: "e@e.com"})
	_, err = m2.VerifyAccessToken(tok)
	if err == nil {
		t.Error("expected error when verifying token with wrong key")
	}
}

// TestAccessTokenTTL verifies the token has a 15-minute lifetime.
// We just check it's valid immediately and the constant is correct.
func TestAccessTokenTTL_Is15Minutes(t *testing.T) {
	if auth.AccessTokenTTL != 15*time.Minute {
		t.Errorf("AccessTokenTTL = %v, want 15m", auth.AccessTokenTTL)
	}
}

// -------------------------------------------------------------------------
// Refresh token helpers
// -------------------------------------------------------------------------

func TestGenerateRefreshToken_UniquePerCall(t *testing.T) {
	raw1, hash1, err1 := auth.GenerateRefreshToken()
	raw2, hash2, err2 := auth.GenerateRefreshToken()
	if err1 != nil || err2 != nil {
		t.Fatalf("GenerateRefreshToken errors: %v %v", err1, err2)
	}
	if raw1 == raw2 {
		t.Error("two tokens should be unique")
	}
	if string(hash1) == string(hash2) {
		t.Error("two token hashes should differ")
	}
}

func TestGenerateRefreshToken_HashConsistency(t *testing.T) {
	raw, hash, err := auth.GenerateRefreshToken()
	if err != nil {
		t.Fatalf("GenerateRefreshToken: %v", err)
	}
	// Re-hash the raw token and compare
	rehash := auth.HashRefreshToken(raw)
	if string(hash) != string(rehash) {
		t.Error("GenerateRefreshToken hash does not match HashRefreshToken(raw)")
	}
}

func TestHashRefreshToken_Deterministic(t *testing.T) {
	h1 := auth.HashRefreshToken("sametoken")
	h2 := auth.HashRefreshToken("sametoken")
	if string(h1) != string(h2) {
		t.Error("HashRefreshToken should be deterministic")
	}
}

func TestHashRefreshToken_DifferentInputsDifferentHashes(t *testing.T) {
	h1 := auth.HashRefreshToken("token-a")
	h2 := auth.HashRefreshToken("token-b")
	if string(h1) == string(h2) {
		t.Error("different inputs should produce different hashes")
	}
}

func TestGenerateVerificationToken_UniquePerCall(t *testing.T) {
	raw1, _, err1 := auth.GenerateVerificationToken()
	raw2, _, err2 := auth.GenerateVerificationToken()
	if err1 != nil || err2 != nil {
		t.Fatalf("GenerateVerificationToken errors: %v %v", err1, err2)
	}
	if raw1 == raw2 {
		t.Error("two verification tokens should be unique")
	}
}

func TestRefreshTokenTTL_Is30Days(t *testing.T) {
	if auth.RefreshTokenTTL != 30*24*time.Hour {
		t.Errorf("RefreshTokenTTL = %v, want 720h", auth.RefreshTokenTTL)
	}
}
