package accounts

import (
	"encoding/json"
	"errors"
	"net/http"

	"github.com/go-chi/chi/v5"

	appmiddleware "github.com/nikhil/scanvault-api/internal/middleware"
)

// -------------------------------------------------------------------------
// GET /v1/vault/documents/{uuid}/conflicts  (3A.1)
// -------------------------------------------------------------------------

// HandleGetConflicts returns all conflict backups for a document.
func (h *Handler) HandleGetConflicts(w http.ResponseWriter, r *http.Request) {
	claims := appmiddleware.ClaimsFromContext(r.Context())
	if claims == nil {
		respondErr(w, http.StatusUnauthorized, "auth.missing_claims", "Authentication required")
		return
	}
	docUUID := chi.URLParam(r, "uuid")
	backups, err := h.svc.GetConflictBackups(r.Context(), claims.AccountID, docUUID)
	if err != nil {
		respondErr(w, http.StatusInternalServerError, "conflicts.internal", "Internal server error")
		return
	}
	respond(w, http.StatusOK, map[string]any{"conflicts": backups})
}

// -------------------------------------------------------------------------
// POST /v1/accounts/password-reset-request  (3A.6)
// -------------------------------------------------------------------------

type passwordResetRequestBody struct {
	Email string `json:"email"`
}

// HandlePasswordResetRequest creates a password reset token.
func (h *Handler) HandlePasswordResetRequest(w http.ResponseWriter, r *http.Request) {
	var req passwordResetRequestBody
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondErr(w, http.StatusBadRequest, "accounts.bad_request", "Invalid JSON body")
		return
	}
	if req.Email == "" {
		respondErr(w, http.StatusBadRequest, "accounts.missing_fields", "email is required")
		return
	}

	resp, err := h.svc.PasswordResetRequest(r.Context(), req.Email)
	if err != nil {
		respondErr(w, http.StatusInternalServerError, "accounts.internal", "Internal server error")
		return
	}
	respond(w, http.StatusOK, resp)
}

// -------------------------------------------------------------------------
// POST /v1/accounts/password-reset-confirm  (3A.6)
// -------------------------------------------------------------------------

// HandlePasswordResetConfirm validates the token and updates the password.
func (h *Handler) HandlePasswordResetConfirm(w http.ResponseWriter, r *http.Request) {
	var req PasswordResetConfirmRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondErr(w, http.StatusBadRequest, "accounts.bad_request", "Invalid JSON body")
		return
	}
	if req.Token == "" || req.NewAuthHash == "" {
		respondErr(w, http.StatusBadRequest, "accounts.missing_fields", "token and new_auth_hash are required")
		return
	}

	if err := h.svc.PasswordResetConfirm(r.Context(), req); err != nil {
		switch {
		case errors.Is(err, ErrPasswordResetToken):
			respondErr(w, http.StatusBadRequest, "accounts.reset_invalid", "Password reset token is invalid or expired")
		default:
			respondErr(w, http.StatusInternalServerError, "accounts.internal", "Internal server error")
		}
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// -------------------------------------------------------------------------
// GET /v1/accounts/me/activity  (3A.8)
// -------------------------------------------------------------------------

// HandleGetActivity returns the last 50 audit events for the authenticated account.
func (h *Handler) HandleGetActivity(w http.ResponseWriter, r *http.Request) {
	claims := appmiddleware.ClaimsFromContext(r.Context())
	if claims == nil {
		respondErr(w, http.StatusUnauthorized, "auth.missing_claims", "Authentication required")
		return
	}

	events, err := h.svc.GetAccountActivity(r.Context(), claims.AccountID)
	if err != nil {
		respondErr(w, http.StatusInternalServerError, "accounts.internal", "Internal server error")
		return
	}
	respond(w, http.StatusOK, map[string]any{"events": events})
}

// -------------------------------------------------------------------------
// GET /v1/sessions  (3A.9)
// -------------------------------------------------------------------------

// HandleListSessions returns all active sessions for the current account.
func (h *Handler) HandleListSessions(w http.ResponseWriter, r *http.Request) {
	claims := appmiddleware.ClaimsFromContext(r.Context())
	if claims == nil {
		respondErr(w, http.StatusUnauthorized, "auth.missing_claims", "Authentication required")
		return
	}

	sessions, err := h.svc.ListSessions(r.Context(), claims.AccountID)
	if err != nil {
		respondErr(w, http.StatusInternalServerError, "sessions.internal", "Internal server error")
		return
	}
	respond(w, http.StatusOK, map[string]any{"sessions": sessions})
}

// -------------------------------------------------------------------------
// DELETE /v1/sessions/{uuid}  (3A.9)
// -------------------------------------------------------------------------

// HandleRevokeSession revokes a specific session by UUID.
func (h *Handler) HandleRevokeSession(w http.ResponseWriter, r *http.Request) {
	claims := appmiddleware.ClaimsFromContext(r.Context())
	if claims == nil {
		respondErr(w, http.StatusUnauthorized, "auth.missing_claims", "Authentication required")
		return
	}

	sessionUUID := chi.URLParam(r, "uuid")
	if err := h.svc.RevokeSession(r.Context(), claims.AccountID, sessionUUID); err != nil {
		switch {
		case errors.Is(err, ErrBadCredentials):
			respondErr(w, http.StatusNotFound, "sessions.not_found", "Session not found")
		default:
			respondErr(w, http.StatusInternalServerError, "sessions.internal", "Internal server error")
		}
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// -------------------------------------------------------------------------
// DELETE /v1/accounts/me  (3A.10)
// -------------------------------------------------------------------------

// HandleDeleteAccount initiates the 7-day deletion grace period.
func (h *Handler) HandleDeleteAccount(w http.ResponseWriter, r *http.Request) {
	claims := appmiddleware.ClaimsFromContext(r.Context())
	if claims == nil {
		respondErr(w, http.StatusUnauthorized, "auth.missing_claims", "Authentication required")
		return
	}

	resp, err := h.svc.RequestAccountDeletion(r.Context(), claims.AccountID)
	if err != nil {
		respondErr(w, http.StatusInternalServerError, "accounts.internal", "Internal server error")
		return
	}
	respond(w, http.StatusOK, resp)
}

// -------------------------------------------------------------------------
// POST /v1/accounts/me/cancel-deletion  (3A.10)
// -------------------------------------------------------------------------

// HandleCancelDeletion cancels a pending account deletion.
func (h *Handler) HandleCancelDeletion(w http.ResponseWriter, r *http.Request) {
	claims := appmiddleware.ClaimsFromContext(r.Context())
	if claims == nil {
		respondErr(w, http.StatusUnauthorized, "auth.missing_claims", "Authentication required")
		return
	}

	if err := h.svc.CancelAccountDeletion(r.Context(), claims.AccountID); err != nil {
		switch {
		case errors.Is(err, ErrDeletionNotPending):
			respondErr(w, http.StatusBadRequest, "accounts.no_deletion_pending", "No pending deletion found for this account")
		default:
			respondErr(w, http.StatusInternalServerError, "accounts.internal", "Internal server error")
		}
		return
	}
	respond(w, http.StatusOK, map[string]string{"status": "deletion cancelled"})
}

// -------------------------------------------------------------------------
// POST /v1/vault/batch  (3A.2)
// -------------------------------------------------------------------------

// BatchOperation represents a single operation in a batch request.
type BatchOperation struct {
	Op       string          `json:"op"`       // "create", "update", "delete"
	Document json.RawMessage `json:"document"` // operation-specific payload
}

// BatchRequest is the body for POST /v1/vault/batch.
type BatchRequest struct {
	Operations []BatchOperation `json:"operations"`
}

// BatchItemResult is the per-item result in a 207 Multi-Status response.
type BatchItemResult struct {
	Index  int    `json:"index"`
	Status int    `json:"status"`
	Error  string `json:"error,omitempty"`
}

// BatchResponse is the 207 Multi-Status body.
type BatchResponse struct {
	Results []BatchItemResult `json:"results"`
}
