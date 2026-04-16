package config_test

import (
	"testing"

	"github.com/nikhil/scanvault-api/internal/config"
)

// allVars returns a map of all required env vars set to valid values.
func allVars() map[string]string {
	return map[string]string{
		"DATABASE_URL":   "postgres://user:pass@localhost:5432/scanvault?sslmode=disable",
		"PASETO_KEY":     "dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXQ=", // 32-byte base64
		"ARGON2_TIME":    "3",
		"ARGON2_MEMORY":  "65536",
		"ARGON2_THREADS": "4",
		"S3_BUCKET_NAME": "scanvault-staging-vault-203a9e83",
		"REDIS_URL":      "rediss://scanvault-staging-redis.serverless.aps1.cache.amazonaws.com:6379",
		"POSTMARK_TOKEN": "test-postmark-token",
		"IP_HASH_KEY":    "test-ip-hash-key-32-bytes-here!!",
		"ENVIRONMENT":    "dev",
	}
}

// setEnv sets all keys from the map as environment variables and returns a cleanup func.
func setEnv(t *testing.T, vars map[string]string) {
	t.Helper()
	for k, v := range vars {
		t.Setenv(k, v)
	}
}

// TestLoad_HappyPath verifies all env vars load correctly into the struct.
func TestLoad_HappyPath(t *testing.T) {
	setEnv(t, allVars())

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("expected no error, got: %v", err)
	}

	if cfg.DatabaseURL != "postgres://user:pass@localhost:5432/scanvault?sslmode=disable" {
		t.Errorf("DatabaseURL = %q", cfg.DatabaseURL)
	}
	if cfg.PasetoKey != "dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleXQ=" {
		t.Errorf("PasetoKey = %q", cfg.PasetoKey)
	}
	if cfg.Argon2Time != 3 {
		t.Errorf("Argon2Time = %d, want 3", cfg.Argon2Time)
	}
	if cfg.Argon2Memory != 65536 {
		t.Errorf("Argon2Memory = %d, want 65536", cfg.Argon2Memory)
	}
	if cfg.Argon2Threads != 4 {
		t.Errorf("Argon2Threads = %d, want 4", cfg.Argon2Threads)
	}
	if cfg.S3BucketName != "scanvault-staging-vault-203a9e83" {
		t.Errorf("S3BucketName = %q", cfg.S3BucketName)
	}
	if cfg.RedisURL != "rediss://scanvault-staging-redis.serverless.aps1.cache.amazonaws.com:6379" {
		t.Errorf("RedisURL = %q", cfg.RedisURL)
	}
	if cfg.PostmarkToken != "test-postmark-token" {
		t.Errorf("PostmarkToken = %q", cfg.PostmarkToken)
	}
	if cfg.IPHashKey != "test-ip-hash-key-32-bytes-here!!" {
		t.Errorf("IPHashKey = %q", cfg.IPHashKey)
	}
	if cfg.Environment != "dev" {
		t.Errorf("Environment = %q, want dev", cfg.Environment)
	}
}

// TestLoad_Defaults verifies optional vars have correct defaults when unset.
func TestLoad_Defaults(t *testing.T) {
	vars := allVars()
	// Remove optional vars so defaults kick in
	delete(vars, "SERVER_PORT")
	delete(vars, "ENVIRONMENT")
	delete(vars, "SENTRY_DSN")
	setEnv(t, vars)

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("expected no error, got: %v", err)
	}

	if cfg.ServerPort != "8080" {
		t.Errorf("ServerPort default = %q, want 8080", cfg.ServerPort)
	}
	if cfg.Environment != "dev" {
		t.Errorf("Environment default = %q, want dev", cfg.Environment)
	}
	if cfg.SentryDSN != "" {
		t.Errorf("SentryDSN default = %q, want empty string", cfg.SentryDSN)
	}
}

// TestLoad_MissingRequired verifies each required var causes a load error.
func TestLoad_MissingRequired(t *testing.T) {
	required := []string{
		"DATABASE_URL",
		"PASETO_KEY",
		"ARGON2_TIME",
		"ARGON2_MEMORY",
		"ARGON2_THREADS",
		"S3_BUCKET_NAME",
		"REDIS_URL",
		"POSTMARK_TOKEN",
		"IP_HASH_KEY",
	}

	for _, key := range required {
		t.Run("missing_"+key, func(t *testing.T) {
			vars := allVars()
			delete(vars, key)
			setEnv(t, vars)

			_, err := config.Load()
			if err == nil {
				t.Errorf("expected error when %s is missing, got nil", key)
			}
		})
	}
}

// TestLoad_InvalidEnvironment verifies that an unknown environment value is rejected.
func TestLoad_InvalidEnvironment(t *testing.T) {
	cases := []string{"production", "test", "local", "PROD", "Dev"}

	for _, env := range cases {
		t.Run("env_"+env, func(t *testing.T) {
			vars := allVars()
			vars["ENVIRONMENT"] = env
			setEnv(t, vars)

			_, err := config.Load()
			if err == nil {
				t.Errorf("expected error for ENVIRONMENT=%q, got nil", env)
			}
		})
	}
}

// TestLoad_ValidEnvironments verifies all three valid environments are accepted.
func TestLoad_ValidEnvironments(t *testing.T) {
	for _, env := range []string{"dev", "staging", "prod"} {
		t.Run("env_"+env, func(t *testing.T) {
			vars := allVars()
			vars["ENVIRONMENT"] = env
			setEnv(t, vars)

			cfg, err := config.Load()
			if err != nil {
				t.Fatalf("expected no error for ENVIRONMENT=%q, got: %v", env, err)
			}
			if cfg.Environment != env {
				t.Errorf("Environment = %q, want %q", cfg.Environment, env)
			}
		})
	}
}

// TestLoad_ZeroArgon2Values verifies Argon2 params of 0 are rejected.
func TestLoad_ZeroArgon2Values(t *testing.T) {
	cases := []struct {
		key string
		val string
	}{
		{"ARGON2_TIME", "0"},
		{"ARGON2_MEMORY", "0"},
		{"ARGON2_THREADS", "0"},
	}

	for _, tc := range cases {
		t.Run("zero_"+tc.key, func(t *testing.T) {
			vars := allVars()
			vars[tc.key] = tc.val
			setEnv(t, vars)

			_, err := config.Load()
			if err == nil {
				t.Errorf("expected error when %s=0, got nil", tc.key)
			}
		})
	}
}

// TestMustLoad_PanicsOnMissingVar verifies MustLoad panics when DATABASE_URL is missing.
func TestMustLoad_PanicsOnMissingVar(t *testing.T) {
	vars := allVars()
	delete(vars, "DATABASE_URL")
	setEnv(t, vars)

	defer func() {
		if r := recover(); r == nil {
			t.Error("expected MustLoad to panic with missing DATABASE_URL, but it did not")
		}
	}()

	config.MustLoad()
}

// TestMustLoad_SucceedsWithAllVars verifies MustLoad does not panic when all vars are set.
func TestMustLoad_SucceedsWithAllVars(t *testing.T) {
	setEnv(t, allVars())

	defer func() {
		if r := recover(); r != nil {
			t.Errorf("MustLoad panicked unexpectedly: %v", r)
		}
	}()

	cfg := config.MustLoad()
	if cfg == nil {
		t.Error("MustLoad returned nil config")
	}
}
