// Package logger provides the application-wide slog JSON logger factory.
// It embeds build metadata (version, commit, build_date) as base attributes
// and ensures no secret fields can leak through a sanitising handler.
package logger

import (
	"context"
	"log/slog"
	"os"
	"strings"
)

// Build-time variables injected via -ldflags.
// Defaults are safe fallbacks for local dev.
var (
	Version   = "dev"
	Commit    = "unknown"
	BuildDate = "unknown"
)

// secretFields is the set of field keys that must never appear in logs.
// Any attribute whose key matches one of these is replaced with "[REDACTED]".
var secretFields = map[string]bool{
	"auth_hash":   true,
	"wrapped_key": true,
	"kdf_salt":    true,
	"kdf_params":  true,
	"auth_params": true,
	"password":    true,
	"token":       true,
	"secret":      true,
}

// New creates a JSON slog.Logger that:
//   - writes to stdout
//   - includes version/commit/build_date as base attributes on every log line
//   - redacts any attribute whose key is in secretFields
func New(level slog.Level) *slog.Logger {
	base := slog.New(newSanitiser(
		slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: level}),
	))
	return base.With(
		slog.String("version", Version),
		slog.String("commit", Commit),
		slog.String("build_date", BuildDate),
	)
}

// sanitiser wraps another Handler and redacts secret attributes.
type sanitiser struct {
	inner slog.Handler
}

func newSanitiser(inner slog.Handler) *sanitiser {
	return &sanitiser{inner: inner}
}

func (s *sanitiser) Enabled(ctx context.Context, level slog.Level) bool {
	return s.inner.Enabled(ctx, level)
}

func (s *sanitiser) Handle(ctx context.Context, r slog.Record) error {
	safe := slog.NewRecord(r.Time, r.Level, r.Message, r.PC)
	r.Attrs(func(a slog.Attr) bool {
		safe.AddAttrs(redactAttr(a))
		return true
	})
	return s.inner.Handle(ctx, safe)
}

func (s *sanitiser) WithAttrs(attrs []slog.Attr) slog.Handler {
	safe := make([]slog.Attr, len(attrs))
	for i, a := range attrs {
		safe[i] = redactAttr(a)
	}
	return &sanitiser{inner: s.inner.WithAttrs(safe)}
}

func (s *sanitiser) WithGroup(name string) slog.Handler {
	return &sanitiser{inner: s.inner.WithGroup(name)}
}

// redactAttr returns the attribute unchanged unless its key is a secret field,
// in which case the value is replaced with the string "[REDACTED]".
func redactAttr(a slog.Attr) slog.Attr {
	key := strings.ToLower(a.Key)
	if secretFields[key] {
		return slog.String(a.Key, "[REDACTED]")
	}
	return a
}
