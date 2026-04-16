package backup_test

import (
	"bytes"
	"compress/gzip"
	"context"
	"io"
	"log/slog"
	"testing"

	"github.com/nikhil/scanvault-api/internal/backup"
)

// noopUploader captures what would be uploaded.
type noopUploader struct {
	called bool
	key    string
	data   []byte
}

func (u *noopUploader) Upload(_ context.Context, key string, r io.Reader, _ int64) error {
	u.called = true
	u.key = key
	data, err := io.ReadAll(r)
	if err != nil {
		return err
	}
	u.data = data
	return nil
}

func TestDryRunDoesNotUpload(t *testing.T) {
	up := &noopUploader{}
	cfg := backup.Config{
		DatabaseURL: "postgres://localhost/test",
		DryRun:      true,
		S3Prefix:    "backups/",
		Uploader:    up,
		Logger:      slog.Default(),
	}

	err := backup.BackupNow(context.Background(), cfg)
	if err != nil {
		t.Fatalf("dry-run should not return error, got: %v", err)
	}
	if up.called {
		t.Fatal("dry-run mode should NOT call the uploader")
	}
}

func TestGzipBytesProducesValidGzipHeader(t *testing.T) {
	payload := []byte("SELECT 1; -- test backup data")
	compressed, err := backup.GzipBytes(payload)
	if err != nil {
		t.Fatalf("GzipBytes failed: %v", err)
	}

	// gzip magic number: 0x1f 0x8b
	if len(compressed) < 2 {
		t.Fatal("compressed output too short")
	}
	if compressed[0] != 0x1f || compressed[1] != 0x8b {
		t.Fatalf("expected gzip magic bytes 0x1f 0x8b, got 0x%02x 0x%02x",
			compressed[0], compressed[1])
	}

	// Decompress and verify round-trip.
	r, err := gzip.NewReader(bytes.NewReader(compressed))
	if err != nil {
		t.Fatalf("gzip.NewReader: %v", err)
	}
	defer r.Close()
	decoded, err := io.ReadAll(r)
	if err != nil {
		t.Fatalf("ReadAll: %v", err)
	}
	if !bytes.Equal(decoded, payload) {
		t.Fatalf("round-trip mismatch: got %q, want %q", decoded, payload)
	}
}

func TestDryRunWithNilLogger(t *testing.T) {
	up := &noopUploader{}
	cfg := backup.Config{
		DatabaseURL: "postgres://localhost/test",
		DryRun:      true,
		S3Prefix:    "backups/",
		Uploader:    up,
	}
	// Should not panic when Logger is nil.
	err := backup.BackupNow(context.Background(), cfg)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}
