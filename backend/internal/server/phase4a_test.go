package server_test

// phase4a_test.go — tests for Phase 4A tasks:
//   4A.1 GET /v1/metrics returns JSON with request_count, avg_duration_ms
//   4A.4 GET /v1/health/deep component status
//   4A.8 load-test.sh exists and references api_base_url.properties
//   4A.9 RUNBOOK.md exists with all required sections

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/nikhil/scanvault-api/internal/server"
)

// ─── helpers ─────────────────────────────────────────────────────────────────

func statPath(path string) (os.FileInfo, error) { return os.Stat(path) }

func readFilePath(path string) (string, error) {
	b, err := os.ReadFile(path)
	return string(b), err
}

func containsString(s, substr string) bool { return strings.Contains(s, substr) }

// ─── 4A.1 GET /v1/metrics ────────────────────────────────────────────────────

func TestHandleMetrics_ReturnsJSON(t *testing.T) {
	cfg := testConfig("test")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/v1/metrics", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
	}
	if ct := w.Header().Get("Content-Type"); ct != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", ct)
	}
	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("response is not valid JSON: %v", err)
	}
}

func TestHandleMetrics_HasRequestCountField(t *testing.T) {
	cfg := testConfig("test")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/v1/metrics", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if _, ok := body["request_count"]; !ok {
		t.Errorf("metrics response missing 'request_count' field, got: %v", body)
	}
}

func TestHandleMetrics_HasDurationField(t *testing.T) {
	cfg := testConfig("test")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/v1/metrics", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if _, ok := body["avg_request_duration_ms"]; !ok {
		t.Errorf("metrics response missing 'avg_request_duration_ms' field, got: %v", body)
	}
}

// ─── 4A.4 GET /v1/health/deep ────────────────────────────────────────────────

func TestDeepHealth_ReturnsJSON(t *testing.T) {
	cfg := testConfig("test")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/v1/health/deep", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("response is not valid JSON: %v", err)
	}
}

func TestDeepHealth_HasStatusField(t *testing.T) {
	cfg := testConfig("test")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/v1/health/deep", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if _, ok := body["status"]; !ok {
		t.Errorf("deep health response missing 'status' field, got: %v", body)
	}
}

func TestDeepHealth_HasComponentsField(t *testing.T) {
	cfg := testConfig("test")
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/v1/health/deep", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	components, ok := body["components"].(map[string]any)
	if !ok {
		t.Fatalf("deep health response missing or invalid 'components' field")
	}
	// Must have at least db, redis, s3
	for _, key := range []string{"db", "redis", "s3"} {
		if _, exists := components[key]; !exists {
			t.Errorf("components missing %q key, got: %v", key, components)
		}
	}
}

// TestDeepHealth_NilDBStillResponds verifies graceful handling when pool is nil.
func TestDeepHealth_NilDBStillResponds(t *testing.T) {
	cfg := testConfig("test")
	cfg.RedisURL = "" // skip Redis check
	srv := server.New(cfg, nil, testLogger())

	req := httptest.NewRequest(http.MethodGet, "/v1/health/deep", nil)
	w := httptest.NewRecorder()
	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 even with nil DB, got %d", w.Code)
	}
	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	// With nil DB the checker skips DB check — status should be "ok"
	if body["status"] != "ok" {
		t.Errorf("expected status 'ok' with nil deps, got %q", body["status"])
	}
}

// ─── 4A.2 Grafana dashboard JSON valid ───────────────────────────────────────

func TestGrafanaDashboard_ValidJSON(t *testing.T) {
	dashDir := filepath.Join(repoRoot(), "infra", "grafana", "dashboards")
	entries, err := os.ReadDir(dashDir)
	if err != nil {
		t.Fatalf("grafana dashboards dir not found at %s: %v", dashDir, err)
	}
	if len(entries) == 0 {
		t.Fatal("no Grafana dashboard JSON files found")
	}
	for _, e := range entries {
		if !strings.HasSuffix(e.Name(), ".json") {
			continue
		}
		path := filepath.Join(dashDir, e.Name())
		data, err := os.ReadFile(path)
		if err != nil {
			t.Errorf("read %s: %v", e.Name(), err)
			continue
		}
		var obj map[string]any
		if err := json.Unmarshal(data, &obj); err != nil {
			t.Errorf("%s is not valid JSON: %v", e.Name(), err)
		}
	}
}

func TestGrafanaDashboard_ReferencesCloudWatchNamespace(t *testing.T) {
	dashDir := filepath.Join(repoRoot(), "infra", "grafana", "dashboards")
	entries, _ := os.ReadDir(dashDir)
	found := false
	for _, e := range entries {
		if !strings.HasSuffix(e.Name(), ".json") {
			continue
		}
		data, _ := os.ReadFile(filepath.Join(dashDir, e.Name()))
		if strings.Contains(string(data), "cloudwatch") {
			found = true
			break
		}
	}
	if !found {
		t.Error("no Grafana dashboard references CloudWatch datasource")
	}
}

// ─── 4A.8 load-test.sh ───────────────────────────────────────────────────────

func TestLoadTestScript_Exists(t *testing.T) {
	path := filepath.Join(repoRoot(), "scripts", "load-test.sh")
	if _, err := statPath(path); err != nil {
		t.Fatalf("scripts/load-test.sh not found: %v", err)
	}
}

func TestLoadTestScript_ReadsFromPropertiesFile(t *testing.T) {
	path := filepath.Join(repoRoot(), "scripts", "load-test.sh")
	content, err := readFilePath(path)
	if err != nil {
		t.Fatalf("read load-test.sh: %v", err)
	}
	if !containsString(content, "api_base_url.properties") {
		t.Error("load-test.sh does not reference api_base_url.properties")
	}
}

// ─── 4A.9 RUNBOOK.md ─────────────────────────────────────────────────────────

func TestRunbook_Exists(t *testing.T) {
	path := filepath.Join(repoRoot(), "docs", "RUNBOOK.md")
	if _, err := statPath(path); err != nil {
		t.Fatalf("docs/RUNBOOK.md not found: %v", err)
	}
}

func TestRunbook_AllRequiredSections(t *testing.T) {
	path := filepath.Join(repoRoot(), "docs", "RUNBOOK.md")
	content, err := readFilePath(path)
	if err != nil {
		t.Fatalf("read RUNBOOK.md: %v", err)
	}
	sections := []string{
		"Lambda cold start",
		"Aurora",
		"ElastiCache",
		"S3",
		"Rollback",
		"Daily Monitoring",
		"Crash Rate",
	}
	for _, s := range sections {
		if !containsString(content, s) {
			t.Errorf("RUNBOOK.md missing required section: %q", s)
		}
	}
}
