// service_internal_test.go uses package accounts (not accounts_test) to access
// unexported helper functions that cannot be covered by external tests.
package accounts

import (
	"fmt"
	"io"
	"log/slog"
	"testing"

	"github.com/jackc/pgx/v5/pgtype"

	"github.com/nikhil/scanvault-api/internal/config"
)

// TestIsUniqueViolation covers the private helper.
func TestIsUniqueViolation(t *testing.T) {
	cases := []struct {
		errMsg string
		want   bool
	}{
		{"ERROR: duplicate key value violates unique constraint (SQLSTATE 23505)", true},
		{"pq: duplicate key value violates unique constraint \"accounts_email_key\" (SQLSTATE 23505)", true},
		{"unique constraint violated", true},
		{"some other error", false},
		{"connection reset", false},
	}
	for _, tc := range cases {
		got := isUniqueViolation(fmt.Errorf("%s", tc.errMsg))
		if got != tc.want {
			t.Errorf("isUniqueViolation(%q) = %v, want %v", tc.errMsg, got, tc.want)
		}
	}
	// nil error
	if isUniqueViolation(nil) {
		t.Error("isUniqueViolation(nil) should return false")
	}
}

// TestContains covers the private string helper.
func TestContainsHelper(t *testing.T) {
	cases := []struct {
		s, sub string
		want   bool
	}{
		{"hello world", "world", true},
		{"hello world", "hello", true},
		{"hello world", "xyz", false},
		{"hello", "hello", true},
		{"", "x", false},
		{"abc", "", true}, // empty sub is always found
	}
	for _, tc := range cases {
		got := contains(tc.s, tc.sub)
		if got != tc.want {
			t.Errorf("contains(%q, %q) = %v, want %v", tc.s, tc.sub, got, tc.want)
		}
	}
}

// TestUUIDToStringInvalidUUID covers the invalid UUID branch in uuidToString.
func TestUUIDToStringInvalidUUID(t *testing.T) {
	_, err := uuidToString(pgtype.UUID{Valid: false})
	if err == nil {
		t.Error("expected error for invalid UUID")
	}
}

// TestValidateEmail_TooLong covers the length check in validateEmail.
func TestValidateEmail_TooLong(t *testing.T) {
	// 255-char email (over the 254-byte limit)
	local := make([]byte, 244)
	for i := range local {
		local[i] = 'a'
	}
	long := string(local) + "@example.com" // 244 + 12 = 256 chars
	err := validateEmail(long)
	if err == nil {
		t.Error("expected error for email longer than 254 bytes")
	}
}

// TestValidateEmail_ValidEmail covers the happy path.
func TestValidateEmail_ValidEmail(t *testing.T) {
	err := validateEmail("user@example.com")
	if err != nil {
		t.Errorf("expected no error for valid email, got: %v", err)
	}
}

// TestValidateEmail_InvalidFormat covers the ParseAddress error path.
func TestValidateEmail_InvalidFormat(t *testing.T) {
	err := validateEmail("notanemail")
	if err == nil {
		t.Error("expected error for invalid email format")
	}
}

// TestHashToken covers the hashToken helper.
func TestHashTokenHelper(t *testing.T) {
	h1 := hashToken("token-value")
	h2 := hashToken("token-value")
	if string(h1) != string(h2) {
		t.Error("hashToken should be deterministic")
	}
	h3 := hashToken("different-value")
	if string(h1) == string(h3) {
		t.Error("hashToken with different inputs should differ")
	}
}

// TestEncodeBase64 covers the encodeBase64 helper.
func TestEncodeBase64Helper(t *testing.T) {
	s := encodeBase64([]byte{0xDE, 0xAD, 0xBE, 0xEF})
	if s == "" {
		t.Error("encodeBase64 returned empty string")
	}
	// Should be hex-encoded
	if s != "deadbeef" {
		t.Errorf("encodeBase64 = %q, want deadbeef", s)
	}
}

// TestNew_InvalidPasetoKey covers the error path in New when the PasetoMaker fails.
func TestNew_InvalidPasetoKey(t *testing.T) {
	cfg := &config.Config{
		PasetoKey: "tooshort", // invalid base64 / too short
	}
	_, err := New(nil, cfg, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err == nil {
		t.Error("expected error for invalid PasetoKey")
	}
}
