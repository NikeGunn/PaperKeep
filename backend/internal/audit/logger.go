// Package audit provides structured audit event logging for ScanVault.
package audit

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"

	"github.com/jackc/pgx/v5/pgxpool"
)

// Event types used across the application.
const (
	EventLoginSuccess    = "login_success"
	EventLoginFailure    = "login_failure"
	EventPasswordChanged = "password_changed"
	EventSessionRevoked  = "session_revoked"
	EventAccountDeleted  = "account_deleted"
	EventAbuseSignal     = "abuse_signal"
)

// AuditEvent is a single audit record.
type AuditEvent struct {
	ID        int64  `json:"id"`
	AccountID int64  `json:"account_id"`
	EventType string `json:"event_type"`
	Metadata  any    `json:"metadata,omitempty"`
	IPHash    string `json:"ip_hash,omitempty"`
	CreatedAt string `json:"created_at"`
}

// Logger writes audit events to the audit_events table.
type Logger struct {
	pool   *pgxpool.Pool
	slogger *slog.Logger
}

// NewLogger constructs an audit Logger.
func NewLogger(pool *pgxpool.Pool, slogger *slog.Logger) *Logger {
	return &Logger{pool: pool, slogger: slogger}
}

// LogEvent persists an audit event. metadata may be nil or any JSON-serialisable
// value. ipHash should already be HMAC-hashed before calling (never raw IPs).
// Errors are logged but not propagated — audit logging must never block the
// happy path.
func (l *Logger) LogEvent(ctx context.Context, accountID int64, eventType string, metadata any, ipHash string) {
	metaJSON, err := marshalMetadata(metadata)
	if err != nil {
		l.slogger.Error("audit: marshal metadata",
			slog.String("event_type", eventType),
			slog.String("error", err.Error()),
		)
		return
	}

	const q = `
		INSERT INTO audit_events (account_id, event_type, metadata, ip_hash)
		VALUES ($1, $2, $3, $4)
	`
	if _, err := l.pool.Exec(ctx, q, accountID, eventType, metaJSON, ipHash); err != nil {
		l.slogger.Error("audit: insert event",
			slog.String("event_type", eventType),
			slog.Int64("account_id", accountID),
			slog.String("error", err.Error()),
		)
	}
}

// ListRecentEvents returns the last n audit events for an account, ordered by
// created_at descending.
func (l *Logger) ListRecentEvents(ctx context.Context, accountID int64, limit int) ([]AuditEvent, error) {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	const q = `
		SELECT id, account_id, event_type,
		       COALESCE(metadata::text, ''), COALESCE(ip_hash, ''), created_at
		FROM audit_events
		WHERE account_id = $1
		ORDER BY created_at DESC
		LIMIT $2
	`
	rows, err := l.pool.Query(ctx, q, accountID, limit)
	if err != nil {
		return nil, fmt.Errorf("list audit events: %w", err)
	}
	defer rows.Close()

	var events []AuditEvent
	for rows.Next() {
		var e AuditEvent
		var metaStr string
		var createdAt interface{}
		if err := rows.Scan(&e.ID, &e.AccountID, &e.EventType, &metaStr, &e.IPHash, &createdAt); err != nil {
			return nil, fmt.Errorf("scan audit event: %w", err)
		}
		if metaStr != "" {
			var raw json.RawMessage
			if err := json.Unmarshal([]byte(metaStr), &raw); err == nil {
				e.Metadata = raw
			}
		}
		switch v := createdAt.(type) {
		case string:
			e.CreatedAt = v
		case fmt.Stringer:
			e.CreatedAt = v.String()
		default:
			e.CreatedAt = fmt.Sprint(v)
		}
		events = append(events, e)
	}
	return events, rows.Err()
}

// marshalMetadata encodes metadata to JSON bytes, returning nil for nil input.
func marshalMetadata(metadata any) ([]byte, error) {
	if metadata == nil {
		return nil, nil
	}
	b, err := json.Marshal(metadata)
	if err != nil {
		return nil, err
	}
	return b, nil
}
