package sync_test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	syncsvc "github.com/nikhil/scanvault-api/internal/sync"
	"github.com/nikhil/scanvault-api/internal/testhelper"
)

func TestDetectConflict(t *testing.T) {
	d := syncsvc.NewConflictDetector(nil) // pool not needed for pure logic

	tests := []struct {
		name          string
		serverVersion int64
		clientVersion int64
		wantConflict  bool
	}{
		{"same version — no conflict", 5, 5, false},
		{"client behind server — conflict", 3, 5, true},
		{"client ahead of server — conflict", 7, 5, true},
		{"both zero — no conflict", 0, 0, false},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got := d.DetectConflict(tc.serverVersion, tc.clientVersion)
			assert.Equal(t, tc.wantConflict, got)
		})
	}
}

func TestBackupConflict_Integration(t *testing.T) {
	pool := testhelper.SetupTestDB(t)
	d := syncsvc.NewConflictDetector(pool)
	ctx := context.Background()

	// Create an account to satisfy the foreign key
	var accountID int64
	err := pool.QueryRow(ctx, `
		INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params)
		VALUES ('conflict@example.com', 'hash', '{}', '\x01', '\x02', '{}')
		RETURNING id
	`).Scan(&accountID)
	require.NoError(t, err)

	clientData := []byte(`{"encrypted_metadata":"dGVzdA=="}`)
	err = d.BackupConflict(ctx, accountID, "doc-uuid-123", clientData)
	require.NoError(t, err)

	// Verify stored in DB
	var stored []byte
	err = pool.QueryRow(ctx,
		`SELECT client_data FROM conflict_backups WHERE account_id=$1 AND doc_id=$2`,
		accountID, "doc-uuid-123",
	).Scan(&stored)
	require.NoError(t, err)
	assert.Equal(t, clientData, stored)
}

func TestResolveConflict_ServerWins(t *testing.T) {
	pool := testhelper.SetupTestDB(t)
	d := syncsvc.NewConflictDetector(pool)
	ctx := context.Background()

	var accountID int64
	err := pool.QueryRow(ctx, `
		INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params)
		VALUES ('resolve@example.com', 'hash', '{}', '\x01', '\x02', '{}')
		RETURNING id
	`).Scan(&accountID)
	require.NoError(t, err)

	serverVersion := int64(7)
	clientData := []byte("client state")

	won, err := d.ResolveConflict(ctx, accountID, "doc-456", serverVersion, clientData)
	require.NoError(t, err)
	assert.Equal(t, serverVersion, won, "server version must win")

	// Confirm backup was stored
	var count int
	err = pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM conflict_backups WHERE account_id=$1 AND doc_id='doc-456'`,
		accountID,
	).Scan(&count)
	require.NoError(t, err)
	assert.Equal(t, 1, count)
}
