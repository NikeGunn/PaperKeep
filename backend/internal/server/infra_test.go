package server_test

// infra_test.go validates the docker-compose.yml and Makefile for 1A.21 and 1A.22.

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"gopkg.in/yaml.v3"
)

// backendRoot returns the absolute path to the backend/ directory.
func backendRoot() string {
	_, file, _, _ := runtime.Caller(0)
	return filepath.Join(filepath.Dir(file), "..", "..")
}

// -------------------------------------------------------------------------
// 1A.21 — docker-compose.yml
// -------------------------------------------------------------------------

// TestDockerCompose_ValidYAML verifies docker-compose.yml parses as valid YAML.
func TestDockerCompose_ValidYAML(t *testing.T) {
	path := filepath.Join(backendRoot(), "docker-compose.yml")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("docker-compose.yml not found at %s: %v", path, err)
	}

	var obj map[string]any
	if err := yaml.Unmarshal(data, &obj); err != nil {
		t.Fatalf("docker-compose.yml is not valid YAML: %v", err)
	}
}

// TestDockerCompose_HasPostgresService verifies a postgres service is defined.
func TestDockerCompose_HasPostgresService(t *testing.T) {
	path := filepath.Join(backendRoot(), "docker-compose.yml")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read docker-compose.yml: %v", err)
	}

	var compose map[string]any
	if err := yaml.Unmarshal(data, &compose); err != nil {
		t.Fatalf("parse docker-compose.yml: %v", err)
	}

	services, ok := compose["services"].(map[string]any)
	if !ok || len(services) == 0 {
		t.Fatal("docker-compose.yml has no 'services' section")
	}

	if _, ok := services["postgres"]; !ok {
		t.Errorf("docker-compose.yml missing 'postgres' service; found: %v", serviceNames(services))
	}
}

// TestDockerCompose_PostgresUsesCorrectImage verifies postgres:16-alpine image.
func TestDockerCompose_PostgresUsesCorrectImage(t *testing.T) {
	path := filepath.Join(backendRoot(), "docker-compose.yml")
	data, _ := os.ReadFile(path)

	var compose map[string]any
	_ = yaml.Unmarshal(data, &compose)

	services, _ := compose["services"].(map[string]any)
	pg, _ := services["postgres"].(map[string]any)

	image, _ := pg["image"].(string)
	if !strings.HasPrefix(image, "postgres:16") {
		t.Errorf("postgres image = %q, want postgres:16-alpine", image)
	}
}

// TestDockerCompose_PostgresExposesPort5432 verifies port 5432 is published.
func TestDockerCompose_PostgresExposesPort5432(t *testing.T) {
	path := filepath.Join(backendRoot(), "docker-compose.yml")
	data, _ := os.ReadFile(path)

	content := string(data)
	if !strings.Contains(content, "5432") {
		t.Error("docker-compose.yml does not expose port 5432")
	}
}

// TestDockerCompose_PostgresHasHealthcheck verifies a healthcheck is configured.
func TestDockerCompose_PostgresHasHealthcheck(t *testing.T) {
	path := filepath.Join(backendRoot(), "docker-compose.yml")
	data, _ := os.ReadFile(path)

	content := string(data)
	if !strings.Contains(content, "healthcheck") {
		t.Error("docker-compose.yml postgres service is missing a healthcheck")
	}
}

func serviceNames(m map[string]any) []string {
	names := make([]string, 0, len(m))
	for k := range m {
		names = append(names, k)
	}
	return names
}

// -------------------------------------------------------------------------
// 1A.22 — Makefile targets
// -------------------------------------------------------------------------

// TestMakefile_HasRequiredTargets verifies all mandated targets exist.
func TestMakefile_HasRequiredTargets(t *testing.T) {
	path := filepath.Join(backendRoot(), "Makefile")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("Makefile not found at %s: %v", path, err)
	}

	content := string(data)

	required := []string{"run", "test", "lint", "migrate-up", "migrate-down", "sqlc", "build"}
	for _, target := range required {
		// A Makefile target line is "<target>:" at the start of a line.
		if !strings.Contains(content, target+":") {
			t.Errorf("Makefile missing target %q", target)
		}
	}
}

// TestMakefile_HasLDFLAGSForBuildMetadata verifies ldflags inject version/commit/build_date.
func TestMakefile_HasLDFLAGSForBuildMetadata(t *testing.T) {
	path := filepath.Join(backendRoot(), "Makefile")
	data, _ := os.ReadFile(path)
	content := string(data)

	for _, flag := range []string{"VERSION", "COMMIT", "BUILD_DATE"} {
		if !strings.Contains(content, flag) {
			t.Errorf("Makefile missing build metadata variable %q", flag)
		}
	}
}

// -------------------------------------------------------------------------
// 1A.23 — deploy/Caddyfile
// -------------------------------------------------------------------------

// repoRoot returns the absolute path to the repo root (three levels above the test file).
// backend/internal/server/infra_test.go → up 3 → ScanVault/
func repoRoot() string {
	_, file, _, _ := runtime.Caller(0)
	return filepath.Join(filepath.Dir(file), "..", "..", "..")
}

// TestCaddyfile_Exists verifies deploy/Caddyfile is present.
func TestCaddyfile_Exists(t *testing.T) {
	path := filepath.Join(repoRoot(), "deploy", "Caddyfile")
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("deploy/Caddyfile not found at %s: %v", path, err)
	}
}

// TestCaddyfile_HasCorrectDomainAndPort verifies the reverse-proxy target.
func TestCaddyfile_HasCorrectDomainAndPort(t *testing.T) {
	path := filepath.Join(repoRoot(), "deploy", "Caddyfile")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read Caddyfile: %v", err)
	}
	content := string(data)

	if !strings.Contains(content, "api.scanvault.app") {
		t.Error("Caddyfile missing domain api.scanvault.app")
	}
	if !strings.Contains(content, "localhost:8080") {
		t.Error("Caddyfile missing reverse_proxy target localhost:8080")
	}
	if !strings.Contains(content, "reverse_proxy") {
		t.Error("Caddyfile missing reverse_proxy directive")
	}
}

// TestCaddyfile_HasTLS13Minimum verifies TLS 1.3 is enforced.
func TestCaddyfile_HasTLS13Minimum(t *testing.T) {
	path := filepath.Join(repoRoot(), "deploy", "Caddyfile")
	data, _ := os.ReadFile(path)
	content := string(data)

	if !strings.Contains(content, "tls1.3") {
		t.Error("Caddyfile missing TLS 1.3 minimum (tls1.3)")
	}
}

// TestCaddyfile_HasHSTSWithPreload verifies HSTS header with preload is configured.
func TestCaddyfile_HasHSTSWithPreload(t *testing.T) {
	path := filepath.Join(repoRoot(), "deploy", "Caddyfile")
	data, _ := os.ReadFile(path)
	content := string(data)

	if !strings.Contains(content, "Strict-Transport-Security") {
		t.Error("Caddyfile missing Strict-Transport-Security header")
	}
	if !strings.Contains(content, "preload") {
		t.Error("Caddyfile HSTS missing preload directive")
	}
	if !strings.Contains(content, "includeSubDomains") {
		t.Error("Caddyfile HSTS missing includeSubDomains")
	}
}

// -------------------------------------------------------------------------
// 1A.23 — deploy/scanvault.service
// -------------------------------------------------------------------------

// TestSystemdUnit_Exists verifies deploy/scanvault.service is present.
func TestSystemdUnit_Exists(t *testing.T) {
	path := filepath.Join(repoRoot(), "deploy", "scanvault.service")
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("deploy/scanvault.service not found at %s: %v", path, err)
	}
}

// TestSystemdUnit_HasRequiredSections verifies [Unit], [Service], [Install] exist.
func TestSystemdUnit_HasRequiredSections(t *testing.T) {
	path := filepath.Join(repoRoot(), "deploy", "scanvault.service")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read scanvault.service: %v", err)
	}
	content := string(data)

	for _, section := range []string{"[Unit]", "[Service]", "[Install]"} {
		if !strings.Contains(content, section) {
			t.Errorf("scanvault.service missing section %q", section)
		}
	}
}

// TestSystemdUnit_HasRestartOnFailure verifies Restart=on-failure is set.
func TestSystemdUnit_HasRestartOnFailure(t *testing.T) {
	path := filepath.Join(repoRoot(), "deploy", "scanvault.service")
	data, _ := os.ReadFile(path)
	content := string(data)

	if !strings.Contains(content, "Restart=on-failure") {
		t.Error("scanvault.service missing Restart=on-failure")
	}
}

// TestSystemdUnit_HasEnvironmentFile verifies EnvironmentFile is used (not hardcoded secrets).
func TestSystemdUnit_HasEnvironmentFile(t *testing.T) {
	path := filepath.Join(repoRoot(), "deploy", "scanvault.service")
	data, _ := os.ReadFile(path)
	content := string(data)

	if !strings.Contains(content, "EnvironmentFile=") {
		t.Error("scanvault.service missing EnvironmentFile directive")
	}
	// Must not hardcode common secret-looking env var names directly
	for _, secret := range []string{"DATABASE_URL=postgres://", "PASETO_KEY="} {
		if strings.Contains(content, secret) {
			t.Errorf("scanvault.service appears to hardcode secret %q — use EnvironmentFile instead", secret)
		}
	}
}

// TestSystemdUnit_HasWantedBy verifies WantedBy=multi-user.target for auto-start.
func TestSystemdUnit_HasWantedBy(t *testing.T) {
	path := filepath.Join(repoRoot(), "deploy", "scanvault.service")
	data, _ := os.ReadFile(path)
	content := string(data)

	if !strings.Contains(content, "WantedBy=multi-user.target") {
		t.Error("scanvault.service missing WantedBy=multi-user.target")
	}
}

// -------------------------------------------------------------------------
// 1A.23 — docs/DEPLOY.md
// -------------------------------------------------------------------------

// TestDeployDoc_Exists verifies docs/DEPLOY.md is present.
func TestDeployDoc_Exists(t *testing.T) {
	path := filepath.Join(repoRoot(), "docs", "DEPLOY.md")
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("docs/DEPLOY.md not found at %s: %v", path, err)
	}
}

// TestDeployDoc_HasRequiredSections verifies DEPLOY.md covers key operational areas.
func TestDeployDoc_HasRequiredSections(t *testing.T) {
	path := filepath.Join(repoRoot(), "docs", "DEPLOY.md")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read DEPLOY.md: %v", err)
	}
	content := string(data)

	required := []string{
		"Caddy",
		"systemd",
		"EnvironmentFile",
		"chmod",
		"0600",
		"rollback",
	}
	for _, keyword := range required {
		if !strings.Contains(content, keyword) {
			t.Errorf("DEPLOY.md missing expected content %q", keyword)
		}
	}
}
