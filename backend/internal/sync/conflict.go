// Package sync implements conflict detection and resolution for vault documents.
package sync

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5/pgxpool"
)

// ConflictDetector is the interface for detecting version conflicts.
type ConflictDetector interface {
	// DetectConflict returns true if serverVersion and clientVersion diverge.
	DetectConflict(serverVersion, clientVersion int64) bool

	// BackupConflict stores the client's conflicting data before server-wins resolution.
	BackupConflict(ctx context.Context, accountID int64, docID string, clientData []byte) error

	// ResolveConflict applies server-wins policy: returns the server version while
	// backing up clientData. Returns the server version that wins.
	ResolveConflict(ctx context.Context, accountID int64, docID string, serverVersion int64, clientData []byte) (int64, error)
}

// DefaultConflictDetector implements ConflictDetector backed by Postgres.
type DefaultConflictDetector struct {
	pool *pgxpool.Pool
}

// NewConflictDetector creates a DefaultConflictDetector.
func NewConflictDetector(pool *pgxpool.Pool) *DefaultConflictDetector {
	return &DefaultConflictDetector{pool: pool}
}

// DetectConflict returns true when serverVersion != clientVersion, indicating
// the client has diverged from the canonical server state.
func (d *DefaultConflictDetector) DetectConflict(serverVersion, clientVersion int64) bool {
	return serverVersion != clientVersion
}

// BackupConflict persists the client's rejected data in the conflict_backups table.
// This preserves the client's work in case they need it later.
func (d *DefaultConflictDetector) BackupConflict(ctx context.Context, accountID int64, docID string, clientData []byte) error {
	const q = `
		INSERT INTO conflict_backups (account_id, doc_id, client_data)
		VALUES ($1, $2, $3)
	`
	if _, err := d.pool.Exec(ctx, q, accountID, docID, clientData); err != nil {
		return fmt.Errorf("backup conflict: %w", err)
	}
	return nil
}

// ResolveConflict applies server-wins policy:
//  1. Backs up clientData.
//  2. Returns the serverVersion as the winning version.
//
// The caller is responsible for returning serverVersion to the client so they
// can re-apply their changes on top.
func (d *DefaultConflictDetector) ResolveConflict(ctx context.Context, accountID int64, docID string, serverVersion int64, clientData []byte) (int64, error) {
	if err := d.BackupConflict(ctx, accountID, docID, clientData); err != nil {
		return 0, fmt.Errorf("resolve conflict: %w", err)
	}
	return serverVersion, nil
}
