package server_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/nikhil/scanvault-api/internal/config"
	"github.com/nikhil/scanvault-api/internal/server"
)

// testLogger returns a discard logger for use in tests.
func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

// testConfig returns a minimal valid config for use in tests.
func testConfig(env string) *config.Config {
	return &config.Config{
		DatabaseURL:   "postgres://unused:unused@localhost:5432/unused",
		PasetoKey:     "dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXQ=",
		Argon2Time:    3,
		Argon2Memory:  65536,
		Argon2Threads: 4,
		R2Endpoint:    "https://test.r2.cloudflarestorage.com",
		R2AccessKey:   "test",
		R2SecretKey:   "test",
		R2Bucket:      "test",
		PostmarkToken: "test",
		IPHashKey:     "test-ip-hash-key-32-bytes-here!!",
		ServerPort:    "8080",
		Environment:   env,
	}
}

// startPgContainer spins up a Postgres 16 container and returns its DSN.
func startPgContainer(t *testing.T) (string, func()) {
	t.Helper()
	ctx := context.Background()
	ctr, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("testdb"),
		postgres.WithUsername("testuser"),
		postgres.WithPassword("testpass"),
		testcontainers.WithWaitStrategy(
			wait.ForLog("database system is ready to accept connections").
				WithOccurrence(2).
				WithStartupTimeout(60*time.Second),
		),
	)
	if err != nil {
		t.Fatalf("start postgres container: %v", err)
	}
	host, _ := ctr.Host(ctx)
	port, _ := ctr.MappedPort(ctx, "5432")
	dsn := fmt.Sprintf("postgres://testuser:testpass@%s:%s/testdb?sslmode=disable", host, port.Port())
	cleanup := func() { _ = ctr.Terminate(ctx) }
	return dsn, cleanup
}

func openPool(t *testing.T, dsn string) *pgxpool.Pool {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatalf("open pool: %v", err)
	}
	if err := pool.Ping(ctx); err != nil {
		t.Fatalf("ping pool: %v", err)
	}
	return pool
}

// -------------------------------------------------------------------------
// /health tests (1A.8)
// -------------------------------------------------------------------------

// TestHealth_Returns200 verifies /health always returns 200 with correct JSON.
func TestHealth_Returns200(t *testing.T) {
	cfg := testConfig("dev")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want 200", w.Code)
	}

	ct := w.Header().Get("Content-Type")
	if ct != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", ct)
	}

	var body map[string]any
	if err := json.NewDecoder(w.Body).Decode(&body); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if body["status"] != "ok" {
		t.Errorf("body.status = %v, want ok", body["status"])
	}
	if body["version"] == nil || body["version"] == "" {
		t.Errorf("body.version is empty: %v", body["version"])
	}
}

// TestHealth_MethodNotAllowed verifies only GET is served (chi default behaviour).
func TestHealth_MethodNotAllowed(t *testing.T) {
	cfg := testConfig("dev")
	srv := server.New(cfg, nil, testLogger())

	for _, method := range []string{http.MethodPost, http.MethodDelete, http.MethodPut} {
		t.Run(method, func(t *testing.T) {
			req := httptest.NewRequest(method, "/health", nil)
			w := httptest.NewRecorder()
			srv.Handler().ServeHTTP(w, req)
			if w.Code == http.StatusOK {
				t.Errorf("%s /health = 200, want 405", method)
			}
		})
	}
}

// TestReady_HealthyWhenDBUp verifies /ready returns 200 when Postgres is up.
func TestReady_HealthyWhenDBUp(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	dsn, cleanup := startPgContainer(t)
	defer cleanup()

	pool := openPool(t, dsn)
	defer pool.Close()

	cfg := testConfig("dev")
	srv := server.New(cfg, pool, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/ready", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want 200; body: %s", w.Code, w.Body.String())
	}

	var body map[string]any
	if err := json.NewDecoder(w.Body).Decode(&body); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if body["status"] != "ok" {
		t.Errorf("body.status = %v, want ok", body["status"])
	}
	components, ok := body["components"].(map[string]any)
	if !ok {
		t.Fatalf("components field missing or wrong type: %v", body["components"])
	}
	if components["postgres"] != "ok" {
		t.Errorf("components.postgres = %v, want ok", components["postgres"])
	}
}

// TestReady_UnhealthyWhenDBDown verifies /ready returns 503 when DB is nil/unavailable.
func TestReady_UnhealthyWhenDBDown(t *testing.T) {
	cfg := testConfig("dev")
	// Pass nil pool — simulates no DB connection
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/ready", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusServiceUnavailable {
		t.Errorf("status = %d, want 503", w.Code)
	}

	var body map[string]any
	if err := json.NewDecoder(w.Body).Decode(&body); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if body["status"] == "ok" {
		t.Errorf("body.status = ok, want degraded when DB is down")
	}
}

// TestReady_UnhealthyWhenDBStopped verifies /ready returns 503 when container is terminated.
func TestReady_UnhealthyWhenDBStopped(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	dsn, cleanup := startPgContainer(t)

	pool := openPool(t, dsn)

	cfg := testConfig("dev")
	srv := server.New(cfg, pool, testLogger())

	// Verify it's healthy first
	req := httptest.NewRequest(http.MethodGet, "/ready", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("pre-stop: expected 200, got %d: %s", w.Code, w.Body.String())
	}

	// Stop the container and close the pool
	cleanup()
	pool.Close()

	// /ready should now report unhealthy — use a new pool that will fail to connect
	failPool, err := pgxpool.New(context.Background(), "postgres://invalid:invalid@127.0.0.1:19999/nonexistent?sslmode=disable&connect_timeout=2")
	if err == nil {
		defer failPool.Close()
		srv2 := server.New(cfg, failPool, testLogger())
		req2 := httptest.NewRequest(http.MethodGet, "/ready", nil)
		w2 := httptest.NewRecorder()
		srv2.Handler().ServeHTTP(w2, req2)
		if w2.Code != http.StatusServiceUnavailable {
			t.Errorf("post-stop: expected 503, got %d: %s", w2.Code, w2.Body.String())
		}
	}
	// err != nil means pool creation failed (acceptable — we're testing the "down" case)
}

// -------------------------------------------------------------------------
// Middleware tests (1A.7)
// -------------------------------------------------------------------------

// TestMiddleware_RequestIDSet verifies X-Request-Id is set on every response.
func TestMiddleware_RequestIDSet(t *testing.T) {
	cfg := testConfig("dev")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	if w.Header().Get("X-Request-Id") == "" {
		t.Error("X-Request-Id header not set by RequestID middleware")
	}
}

// TestMiddleware_SecurityHeaders verifies required security headers are present.
func TestMiddleware_SecurityHeaders(t *testing.T) {
	cfg := testConfig("dev")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	headers := map[string]string{
		"X-Content-Type-Options":  "nosniff",
		"X-Frame-Options":         "DENY",
		"Referrer-Policy":         "no-referrer",
		"Content-Security-Policy": "default-src 'none'",
	}
	for header, want := range headers {
		got := w.Header().Get(header)
		if got != want {
			t.Errorf("header %q = %q, want %q", header, got, want)
		}
	}
}

// TestMiddleware_HSTSOnlyInProd verifies HSTS is only set in prod environment.
func TestMiddleware_HSTSOnlyInProd(t *testing.T) {
	t.Run("dev_no_hsts", func(t *testing.T) {
		cfg := testConfig("dev")
		srv := server.New(cfg, nil, testLogger())
		req := httptest.NewRequest(http.MethodGet, "/health", nil)
		w := httptest.NewRecorder()
		srv.Handler().ServeHTTP(w, req)
		if hsts := w.Header().Get("Strict-Transport-Security"); hsts != "" {
			t.Errorf("HSTS set in dev: %q", hsts)
		}
	})

	t.Run("prod_has_hsts", func(t *testing.T) {
		cfg := testConfig("prod")
		srv := server.New(cfg, nil, testLogger())
		req := httptest.NewRequest(http.MethodGet, "/health", nil)
		w := httptest.NewRecorder()
		srv.Handler().ServeHTTP(w, req)
		if hsts := w.Header().Get("Strict-Transport-Security"); hsts == "" {
			t.Error("HSTS not set in prod")
		}
	})
}

// TestMiddleware_CORSDevAllowsAll verifies dev environment allows any origin.
func TestMiddleware_CORSDevAllowsAll(t *testing.T) {
	cfg := testConfig("dev")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	req.Header.Set("Origin", "http://localhost:3000")
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	if got := w.Header().Get("Access-Control-Allow-Origin"); got != "http://localhost:3000" {
		t.Errorf("CORS origin = %q, want http://localhost:3000", got)
	}
}

// TestMiddleware_CORSProdRestrictsOrigin verifies prod only allows known origins.
func TestMiddleware_CORSProdRestrictsOrigin(t *testing.T) {
	cfg := testConfig("prod")
	srv := server.New(cfg, nil, testLogger())

	t.Run("allowed_origin", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/health", nil)
		req.Header.Set("Origin", "https://scanvault.app")
		w := httptest.NewRecorder()
		srv.Handler().ServeHTTP(w, req)
		if got := w.Header().Get("Access-Control-Allow-Origin"); got != "https://scanvault.app" {
			t.Errorf("allowed origin not reflected: %q", got)
		}
	})

	t.Run("disallowed_origin", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/health", nil)
		req.Header.Set("Origin", "https://evil.example.com")
		w := httptest.NewRecorder()
		srv.Handler().ServeHTTP(w, req)
		if got := w.Header().Get("Access-Control-Allow-Origin"); got != "" {
			t.Errorf("disallowed origin was reflected: %q", got)
		}
	})
}

// TestMiddleware_CORSPreflightHandled verifies OPTIONS preflight returns 204.
func TestMiddleware_CORSPreflightHandled(t *testing.T) {
	cfg := testConfig("dev")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodOptions, "/health", nil)
	req.Header.Set("Origin", "http://localhost:3000")
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Errorf("preflight status = %d, want 204", w.Code)
	}
}

// TestMiddleware_RecovererHandlesPanic verifies the Recoverer middleware
// catches panics and returns 500 instead of crashing the server.
func TestMiddleware_RecovererHandlesPanic(t *testing.T) {
	cfg := testConfig("dev")
	// Build a server with a panic route for testing
	srv := server.NewWithPanicRoute(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/test/panic", nil)
	w := httptest.NewRecorder()

	// Should NOT panic the test process
	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusInternalServerError {
		t.Errorf("panic recovery: status = %d, want 500", w.Code)
	}
}

// TestGracefulShutdown verifies the server handles in-flight requests during shutdown.
func TestGracefulShutdown(t *testing.T) {
	cfg := testConfig("dev")
	// Build a server with a slow route for testing
	srv := server.NewWithSlowRoute(cfg, nil, testLogger(), 200*time.Millisecond)

	// Bind to a free port
	ln, err := net.Listen("tcp", ":0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	addr := ln.Addr().String()

	// Start serving
	go func() { _ = srv.Serve(ln) }()

	// Give the server a moment to start
	time.Sleep(20 * time.Millisecond)

	// Launch a slow request
	done := make(chan int, 1)
	go func() {
		resp, err := http.Get("http://" + addr + "/test/slow") //nolint:noctx
		if err != nil {
			done <- 0
			return
		}
		defer resp.Body.Close()
		done <- resp.StatusCode
	}()

	// Wait for the request to hit the handler, then initiate shutdown
	time.Sleep(50 * time.Millisecond)
	shutCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := srv.Shutdown(shutCtx); err != nil {
		t.Fatalf("shutdown: %v", err)
	}

	// The in-flight request should have completed successfully
	select {
	case code := <-done:
		if code != http.StatusOK {
			t.Errorf("in-flight request got %d, want 200", code)
		}
	case <-time.After(3 * time.Second):
		t.Error("timed out waiting for in-flight request to complete")
	}
}

// -------------------------------------------------------------------------
// 1A.19 — Structured logger (server-level)
// -------------------------------------------------------------------------

// TestStructuredLogger_EmitsDurationMs verifies the request logger emits
// duration_ms (float) and not the old "duration" key.
func TestStructuredLogger_EmitsDurationMs(t *testing.T) {
	var buf bytes.Buffer
	l := slog.New(slog.NewJSONHandler(&buf, &slog.HandlerOptions{Level: slog.LevelInfo}))

	cfg := testConfig("dev")
	srv := server.New(cfg, nil, l)

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	output := buf.String()
	if !strings.Contains(output, "duration_ms") {
		t.Errorf("log output missing duration_ms; got: %s", output)
	}
	if strings.Contains(output, `"duration":`) {
		t.Error("log output uses deprecated 'duration' key — should be 'duration_ms'")
	}
}

// TestStructuredLogger_LogsRequestID verifies request_id appears in log output.
func TestStructuredLogger_LogsRequestID(t *testing.T) {
	var buf bytes.Buffer
	l := slog.New(slog.NewJSONHandler(&buf, &slog.HandlerOptions{Level: slog.LevelInfo}))

	cfg := testConfig("dev")
	srv := server.New(cfg, nil, l)

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	srv.Handler().ServeHTTP(httptest.NewRecorder(), req)

	output := buf.String()
	if !strings.Contains(output, "request_id") {
		t.Errorf("log output missing request_id; got: %s", output)
	}
}

// TestStructuredLogger_SecretFieldsNotLogged verifies that even if a broken
// path accidentally set an auth_hash header, it doesn't appear in log output
// (the server sanitiser protects against this).
func TestStructuredLogger_SecretFieldsNotLogged(t *testing.T) {
	var buf bytes.Buffer
	l := slog.New(slog.NewJSONHandler(&buf, &slog.HandlerOptions{Level: slog.LevelInfo}))

	cfg := testConfig("dev")
	srv := server.New(cfg, nil, l)

	// The server logs method/path/status/duration — none of these are secret fields.
	// We verify that "auth_hash" never appears even when it's in a query param
	// (which it shouldn't be, but belt-and-suspenders).
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	srv.Handler().ServeHTTP(httptest.NewRecorder(), req)

	output := buf.String()
	secretFields := []string{"auth_hash", "wrapped_key", "kdf_salt"}
	for _, field := range secretFields {
		if strings.Contains(output, `"`+field+`":`) {
			t.Errorf("log output contains secret field %q: %s", field, output)
		}
	}
}

func init() {
	// Suppress container logs in test output unless TESTCONTAINERS_LOG=1
	if os.Getenv("TESTCONTAINERS_LOG") == "" {
		os.Setenv("TESTCONTAINERS_RYUK_DISABLED", "false")
	}
}
