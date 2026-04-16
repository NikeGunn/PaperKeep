package captcha_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/nikhil/scanvault-api/internal/captcha"
)

// newTestVerifier creates a Verifier that points at a mock hCaptcha server.
func newTestVerifier(t *testing.T, success bool) (*captcha.Verifier, *httptest.Server) {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]bool{"success": success})
	}))
	t.Cleanup(srv.Close)
	v := captcha.NewVerifierWithURL("test-secret", srv.URL)
	return v, srv
}

func TestVerify_NoKey_SkipsVerification(t *testing.T) {
	v := captcha.NewVerifier("")
	err := v.Verify("")
	assert.NoError(t, err, "no key means dev mode — always pass")

	err = v.Verify("any-token")
	assert.NoError(t, err, "no key means dev mode — always pass")
}

func TestVerify_MissingToken_WithKey(t *testing.T) {
	v := captcha.NewVerifier("real-secret-key")
	err := v.Verify("")
	require.Error(t, err)
	assert.ErrorIs(t, err, captcha.ErrMissingToken)
}

func TestVerify_ValidToken(t *testing.T) {
	v, _ := newTestVerifier(t, true)
	err := v.Verify("valid-token")
	assert.NoError(t, err)
}

func TestVerify_InvalidToken(t *testing.T) {
	v, _ := newTestVerifier(t, false)
	err := v.Verify("bad-token")
	require.Error(t, err)
	assert.ErrorIs(t, err, captcha.ErrInvalidToken)
}
