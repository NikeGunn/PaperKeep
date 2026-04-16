package audit_test

import (
	"context"
	"log/slog"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/nikhil/scanvault-api/internal/audit"
	"github.com/nikhil/scanvault-api/internal/testhelper"
)

func newLogger(t *testing.T) (*audit.Logger, int64) {
	t.Helper()
	pool := testhelper.SetupTestDB(t)
	slogger := slog.New(slog.NewTextHandler(os.Stderr, nil))
	l := audit.NewLogger(pool, slogger)

	// Create an account
	var accountID int64
	err := pool.QueryRow(context.Background(), `
		INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params)
		VALUES ($1, 'hash', '{}', '\x01', '\x02', '{}')
		RETURNING id
	`, "audit@example.com").Scan(&accountID)
	require.NoError(t, err)

	return l, accountID
}

func TestLogEvent_StoresEvent(t *testing.T) {
	l, accountID := newLogger(t)
	ctx := context.Background()

	l.LogEvent(ctx, accountID, audit.EventLoginSuccess, map[string]string{"email": "audit@example.com"}, "hashABC")

	events, err := l.ListRecentEvents(ctx, accountID, 10)
	require.NoError(t, err)
	require.Len(t, events, 1)
	assert.Equal(t, audit.EventLoginSuccess, events[0].EventType)
	assert.Equal(t, accountID, events[0].AccountID)
	assert.Equal(t, "hashABC", events[0].IPHash)
}

func TestLogEvent_NilMetadata(t *testing.T) {
	l, accountID := newLogger(t)
	ctx := context.Background()

	l.LogEvent(ctx, accountID, audit.EventLoginFailure, nil, "")

	events, err := l.ListRecentEvents(ctx, accountID, 10)
	require.NoError(t, err)
	require.Len(t, events, 1)
	assert.Equal(t, audit.EventLoginFailure, events[0].EventType)
}

func TestListRecentEvents_ReturnsLast50(t *testing.T) {
	l, accountID := newLogger(t)
	ctx := context.Background()

	for i := 0; i < 60; i++ {
		l.LogEvent(ctx, accountID, audit.EventLoginSuccess, nil, "")
	}

	events, err := l.ListRecentEvents(ctx, accountID, 50)
	require.NoError(t, err)
	assert.Len(t, events, 50)
}

func TestListRecentEvents_IsolatedByAccount(t *testing.T) {
	pool := testhelper.SetupTestDB(t)
	slogger := slog.New(slog.NewTextHandler(os.Stderr, nil))
	l := audit.NewLogger(pool, slogger)
	ctx := context.Background()

	var acc1, acc2 int64
	require.NoError(t, pool.QueryRow(ctx, `INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params) VALUES ('a1@test.com','h','{}','\x01','\x02','{}') RETURNING id`).Scan(&acc1))
	require.NoError(t, pool.QueryRow(ctx, `INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params) VALUES ('a2@test.com','h','{}','\x01','\x02','{}') RETURNING id`).Scan(&acc2))

	l.LogEvent(ctx, acc1, audit.EventLoginSuccess, nil, "")
	l.LogEvent(ctx, acc2, audit.EventLoginFailure, nil, "")

	events1, err := l.ListRecentEvents(ctx, acc1, 50)
	require.NoError(t, err)
	assert.Len(t, events1, 1)
	assert.Equal(t, audit.EventLoginSuccess, events1[0].EventType)

	events2, err := l.ListRecentEvents(ctx, acc2, 50)
	require.NoError(t, err)
	assert.Len(t, events2, 1)
	assert.Equal(t, audit.EventLoginFailure, events2[0].EventType)
}
