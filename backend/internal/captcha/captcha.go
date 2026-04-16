// Package captcha verifies hCaptcha tokens for signup protection.
package captcha

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const hcaptchaVerifyURL = "https://api.hcaptcha.com/siteverify"

// ErrMissingToken is returned when captcha_token is absent and a key is configured.
var ErrMissingToken = fmt.Errorf("captcha_token is required")

// ErrInvalidToken is returned when the hCaptcha API rejects the token.
var ErrInvalidToken = fmt.Errorf("captcha verification failed")

// Verifier checks hCaptcha tokens.
type Verifier struct {
	secretKey  string
	verifyURL  string
	httpClient *http.Client
}

// NewVerifier creates a Verifier. If secretKey is empty, Verify always returns nil
// (dev mode — no CAPTCHA required).
func NewVerifier(secretKey string) *Verifier {
	return NewVerifierWithURL(secretKey, hcaptchaVerifyURL)
}

// NewVerifierWithURL creates a Verifier with a custom verify endpoint URL.
// Used in tests to point at a mock hCaptcha server.
func NewVerifierWithURL(secretKey, verifyURL string) *Verifier {
	return &Verifier{
		secretKey: secretKey,
		verifyURL: verifyURL,
		httpClient: &http.Client{
			Timeout: 5 * time.Second,
		},
	}
}

// Verify validates the captcha token. Returns nil if:
//   - secretKey is not configured (dev mode)
//   - token passes hCaptcha verification
//
// Returns ErrMissingToken or ErrInvalidToken on failure.
func (v *Verifier) Verify(token string) error {
	if v.secretKey == "" {
		// Dev mode: no key configured, skip verification
		return nil
	}
	if token == "" {
		return ErrMissingToken
	}
	return v.callHcaptcha(token)
}

// hcaptchaResponse is the JSON body from the hCaptcha verify endpoint.
type hcaptchaResponse struct {
	Success bool `json:"success"`
}

func (v *Verifier) callHcaptcha(token string) error {
	form := url.Values{
		"secret":   {v.secretKey},
		"response": {token},
	}
	resp, err := v.httpClient.Post(
		v.verifyURL,
		"application/x-www-form-urlencoded",
		strings.NewReader(form.Encode()),
	)
	if err != nil {
		return fmt.Errorf("hcaptcha verify request: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if err != nil {
		return fmt.Errorf("read hcaptcha response: %w", err)
	}

	var result hcaptchaResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return fmt.Errorf("decode hcaptcha response: %w", err)
	}
	if !result.Success {
		return ErrInvalidToken
	}
	return nil
}
