// Package backup implements pg_dump → gzip → S3 backup strategy.
//
// When BACKUP_DRY_RUN=true (default in dev) BackupNow() logs what it would do
// but makes no network calls and performs no S3 uploads.
//
// In production (BACKUP_DRY_RUN=false):
//  1. Shell out to pg_dump with the DATABASE_URL.
//  2. Pipe the output through gzip.
//  3. Upload the compressed dump to S3 under the "backups/" prefix with a
//     timestamp-based key.
package backup

import (
	"bytes"
	"compress/gzip"
	"context"
	"fmt"
	"io"
	"log/slog"
	"os/exec"
	"time"
)

// Uploader uploads a reader to object storage.
// Injected so tests can use a no-op implementation.
type Uploader interface {
	Upload(ctx context.Context, key string, r io.Reader, sizeHint int64) error
}

// Config holds the parameters required to run a backup.
type Config struct {
	DatabaseURL string
	DryRun      bool
	S3Prefix    string // e.g. "backups/"
	Uploader    Uploader
	Logger      *slog.Logger
}

// BackupNow performs a single backup cycle.
// In dry-run mode it returns immediately without connecting to anything.
func BackupNow(ctx context.Context, cfg Config) error {
	log := cfg.Logger
	if log == nil {
		log = slog.Default()
	}

	key := fmt.Sprintf("%s%s.sql.gz", cfg.S3Prefix, time.Now().UTC().Format("2006-01-02T15-04-05Z"))

	if cfg.DryRun {
		log.Info("backup: dry-run mode — skipping pg_dump and S3 upload",
			slog.String("would_upload_to", key))
		return nil
	}

	log.Info("backup: starting pg_dump", slog.String("key", key))

	// Run pg_dump and capture output.
	cmd := exec.CommandContext(ctx, "pg_dump", "--no-password", cfg.DatabaseURL)
	var stderr bytes.Buffer
	cmd.Stderr = &stderr

	dumpOutput, err := cmd.Output()
	if err != nil {
		return fmt.Errorf("pg_dump failed: %w (stderr: %s)", err, stderr.String())
	}

	log.Info("backup: pg_dump complete", slog.Int("raw_bytes", len(dumpOutput)))

	// Compress with gzip.
	compressed, err := gzipBytes(dumpOutput)
	if err != nil {
		return fmt.Errorf("gzip compress: %w", err)
	}

	log.Info("backup: compressed",
		slog.Int("compressed_bytes", len(compressed)),
		slog.String("key", key))

	// Upload to S3.
	if err := cfg.Uploader.Upload(ctx, key, bytes.NewReader(compressed), int64(len(compressed))); err != nil {
		return fmt.Errorf("s3 upload: %w", err)
	}

	log.Info("backup: uploaded successfully", slog.String("key", key))
	return nil
}

// gzipBytes compresses src and returns the gzip-encoded bytes.
func gzipBytes(src []byte) ([]byte, error) {
	var buf bytes.Buffer
	w := gzip.NewWriter(&buf)
	if _, err := w.Write(src); err != nil {
		return nil, err
	}
	if err := w.Close(); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

// GzipBytes exports gzipBytes for use in tests.
// Tests use this to verify the gzip header independently.
func GzipBytes(src []byte) ([]byte, error) {
	return gzipBytes(src)
}
