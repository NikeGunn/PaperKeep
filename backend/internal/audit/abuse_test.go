package audit_test

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/nikhil/scanvault-api/internal/audit"
	"github.com/nikhil/scanvault-api/internal/testhelper"
)

func TestHandleAbuseSignal_Success(t *testing.T) {
	pool := testhelper.SetupTestDB(t)
	slogger := slog.New(slog.NewTextHandler(os.Stderr, nil))
	l := audit.NewLogger(pool, slogger)
	h := audit.NewAbuseHandler(l)

	body := audit.AbuseSignalRequest{
		SignalType: "spam",
		AccountID:  0,
		Details:    map[string]any{"ip": "1.2.3.4"},
	}
	b, _ := json.Marshal(body)

	req := httptest.NewRequest(http.MethodPost, "/v1/internal/abuse-signal", bytes.NewReader(b))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.HandleAbuseSignal(w, req)
	assert.Equal(t, http.StatusNoContent, w.Code)
}

func TestHandleAbuseSignal_MissingSignalType(t *testing.T) {
	pool := testhelper.SetupTestDB(t)
	slogger := slog.New(slog.NewTextHandler(os.Stderr, nil))
	l := audit.NewLogger(pool, slogger)
	h := audit.NewAbuseHandler(l)

	body := `{"account_id": 1}`
	req := httptest.NewRequest(http.MethodPost, "/v1/internal/abuse-signal", bytes.NewReader([]byte(body)))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.HandleAbuseSignal(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestHandleAbuseSignal_BadJSON(t *testing.T) {
	pool := testhelper.SetupTestDB(t)
	slogger := slog.New(slog.NewTextHandler(os.Stderr, nil))
	l := audit.NewLogger(pool, slogger)
	h := audit.NewAbuseHandler(l)

	req := httptest.NewRequest(http.MethodPost, "/v1/internal/abuse-signal", bytes.NewReader([]byte("{bad json")))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.HandleAbuseSignal(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestHandleAbuseSignal_RecordedInDB(t *testing.T) {
	pool := testhelper.SetupTestDB(t)
	slogger := slog.New(slog.NewTextHandler(os.Stderr, nil))
	l := audit.NewLogger(pool, slogger)
	h := audit.NewAbuseHandler(l)
	ctx := context.Background()

	// Create an account so FK constraint is satisfied for account_id > 0
	var accountID int64
	require.NoError(t, pool.QueryRow(ctx, `
		INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params)
		VALUES ('abuse@test.com','h','{}','\x01','\x02','{}') RETURNING id
	`).Scan(&accountID))

	body := audit.AbuseSignalRequest{
		SignalType: "ddos",
		AccountID:  accountID,
	}
	b, _ := json.Marshal(body)

	req := httptest.NewRequest(http.MethodPost, "/v1/internal/abuse-signal", bytes.NewReader(b))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.HandleAbuseSignal(w, req)
	require.Equal(t, http.StatusNoContent, w.Code)

	// Verify in DB
	events, err := l.ListRecentEvents(ctx, accountID, 10)
	require.NoError(t, err)
	require.Len(t, events, 1)
	assert.Equal(t, audit.EventAbuseSignal, events[0].EventType)
}
