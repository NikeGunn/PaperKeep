// Package audit — abuse signal handling (3A.11).
package audit

import (
	"encoding/json"
	"net/http"
)

// AbuseSignalRequest is the body for POST /v1/internal/abuse-signal.
type AbuseSignalRequest struct {
	SignalType string         `json:"signal_type"`
	AccountID  int64          `json:"account_id,omitempty"`
	Details    map[string]any `json:"details,omitempty"`
}

// AbuseHandler handles the internal abuse-signal endpoint.
type AbuseHandler struct {
	logger *Logger
}

// NewAbuseHandler creates an AbuseHandler.
func NewAbuseHandler(logger *Logger) *AbuseHandler {
	return &AbuseHandler{logger: logger}
}

// HandleAbuseSignal records an abuse signal to the audit log.
// POST /v1/internal/abuse-signal — no auth required (internal network only).
func (h *AbuseHandler) HandleAbuseSignal(w http.ResponseWriter, r *http.Request) {
	var req AbuseSignalRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON body"})
		return
	}
	if req.SignalType == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "signal_type is required"})
		return
	}

	meta := map[string]any{
		"signal_type": req.SignalType,
	}
	for k, v := range req.Details {
		meta[k] = v
	}

	h.logger.LogEvent(r.Context(), req.AccountID, EventAbuseSignal, meta, "")
	w.WriteHeader(http.StatusNoContent)
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
