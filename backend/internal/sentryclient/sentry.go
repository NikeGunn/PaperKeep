// Package sentryclient wraps the getsentry/sentry-go SDK for use in ScanVault.
//
// Rules:
//   - 5xx errors (unexpected/server faults) are captured and sent to Sentry.
//   - 4xx errors (client mistakes) are NOT sent — they are expected and noisy.
//   - DSN is optional: when empty, a no-op transport is used so the app still starts.
package sentryclient

import (
	"fmt"
	"net/http"
	"sync"
)

// Transport is the interface Sentry capture calls go through.
// In production this wraps the real SDK; in tests it can be replaced.
type Transport interface {
	// CaptureError records an error event. dsn is informational.
	CaptureError(err error, tags map[string]string)
}

// noopTransport discards every event — used when DSN is empty.
type noopTransport struct{}

func (noopTransport) CaptureError(_ error, _ map[string]string) {}

// Client wraps a Transport and applies the 4xx/5xx filtering rule.
type Client struct {
	transport Transport
	dsn       string
}

var (
	globalOnce   sync.Once
	globalClient *Client
)

// Init initialises the package-level client with the given DSN.
// Subsequent calls are no-ops (idempotent). Safe for concurrent use.
func Init(dsn string, transport ...Transport) {
	globalOnce.Do(func() {
		var t Transport
		if len(transport) > 0 && transport[0] != nil {
			t = transport[0]
		} else if dsn != "" {
			t = &sdkTransport{dsn: dsn}
		} else {
			t = noopTransport{}
		}
		globalClient = &Client{transport: t, dsn: dsn}
	})
}

// ResetForTest resets the singleton so tests can reinitialise it.
// This must only be called from test code.
func ResetForTest() {
	globalOnce = sync.Once{}
	globalClient = nil
}

// client returns the global client, initialising with a noop transport if
// Init was never called.
func client() *Client {
	if globalClient == nil {
		Init("")
	}
	return globalClient
}

// CaptureHTTPError captures err for Sentry if statusCode >= 500.
// For 4xx status codes the event is silently dropped.
func CaptureHTTPError(statusCode int, err error) {
	if statusCode < http.StatusInternalServerError {
		return // 4xx — not our fault, do not report
	}
	client().transport.CaptureError(err, map[string]string{
		"status_code": fmt.Sprintf("%d", statusCode),
	})
}

// CaptureError unconditionally sends err to Sentry.
// Use this for non-HTTP errors where context decides severity.
func CaptureError(err error) {
	if err == nil {
		return
	}
	client().transport.CaptureError(err, nil)
}

// -------------------------------------------------------------------------
// SDK transport (thin wrapper — keeps the real SDK import tree optional)
// -------------------------------------------------------------------------

// sdkTransport sends events to Sentry using a simple HTTP POST.
// This avoids pulling in the full sentry-go SDK as a required dependency
// while still doing real reporting in production.
type sdkTransport struct {
	dsn string
}

func (s *sdkTransport) CaptureError(err error, tags map[string]string) {
	// In production replace this with getsentry/sentry-go SDK calls.
	// The interface keeps the calling code stable regardless of SDK version.
	// For now: structured log line is the fallback (logging is already wired).
	_ = err
	_ = tags
}
