// Package config loads and validates application configuration from environment variables.
package config

import (
	"errors"
	"fmt"

	"github.com/caarlos0/env/v10"
)

// Config holds all application configuration loaded from environment variables.
// Required fields will cause Load to return an error if not set.
// Optional fields have documented defaults.
type Config struct {
	// Database
	DatabaseURL string `env:"DATABASE_URL,required"`

	// Authentication — PASETO v4 symmetric key, base64-encoded 32 bytes
	PasetoKey string `env:"PASETO_KEY,required"`

	// Argon2id params (all required — no silent defaults for security params)
	Argon2Time    uint32 `env:"ARGON2_TIME,required"`
	Argon2Memory  uint32 `env:"ARGON2_MEMORY,required"`
	Argon2Threads uint8  `env:"ARGON2_THREADS,required"`

	// AWS S3 object storage (replaces R2 — standard AWS, no custom endpoint)
	S3BucketName string `env:"S3_BUCKET_NAME,required"`

	// Redis — used by rate limiter and background queues
	RedisURL string `env:"REDIS_URL,required"`

	// Transactional email (Postmark)
	PostmarkToken string `env:"POSTMARK_TOKEN,required"`

	// hCaptcha secret for signup protection (optional in dev)
	CaptchaSecretKey string `env:"CAPTCHA_SECRET_KEY"`

	// AWS Secrets Manager ARNs (used in Lambda to resolve DB credentials at runtime)
	DBSecretARN      string `env:"DB_SECRET_ARN"`
	PasetoSecretARN  string `env:"PASETO_SECRET_ARN"`
	CaptchaSecretARN string `env:"CAPTCHA_SECRET_ARN"`

	// Error reporting (optional — not required to start)
	SentryDSN string `env:"SENTRY_DSN"`

	// Security — HMAC key for IP hashing in logs (required)
	IPHashKey string `env:"IP_HASH_KEY,required"`

	// Backup
	BackupDryRun bool `env:"BACKUP_DRY_RUN" envDefault:"false"`

	// Server
	ServerPort  string `env:"SERVER_PORT" envDefault:"8080"`
	Environment string `env:"ENVIRONMENT" envDefault:"dev"`
}

// Load reads all environment variables into a Config struct.
// Returns an error if any required variable is missing or if Environment is invalid.
func Load() (*Config, error) {
	cfg := &Config{}
	if err := env.Parse(cfg); err != nil {
		return nil, fmt.Errorf("loading config: %w", err)
	}

	if err := cfg.validate(); err != nil {
		return nil, fmt.Errorf("invalid config: %w", err)
	}

	return cfg, nil
}

// MustLoad calls Load and panics with a clear message on failure.
// Use in main() for fail-fast startup.
func MustLoad() *Config {
	cfg, err := Load()
	if err != nil {
		panic("ScanVault API cannot start: " + err.Error())
	}
	return cfg
}

// validate applies business-logic constraints beyond what the env tag provides.
func (c *Config) validate() error {
	switch c.Environment {
	case "dev", "staging", "prod":
		// valid
	default:
		return fmt.Errorf("ENVIRONMENT must be one of dev/staging/prod, got %q", c.Environment)
	}

	if c.Argon2Time == 0 {
		return errors.New("ARGON2_TIME must be > 0")
	}
	if c.Argon2Memory == 0 {
		return errors.New("ARGON2_MEMORY must be > 0")
	}
	if c.Argon2Threads == 0 {
		return errors.New("ARGON2_THREADS must be > 0")
	}

	return nil
}
