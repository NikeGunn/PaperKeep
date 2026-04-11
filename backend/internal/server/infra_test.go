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
