package accounts

import (
	"encoding/json"
	"errors"
	"net/http"
)

// Handler exposes the accounts service over HTTP.
type Handler struct {
	svc *Service
}

// NewHandler wraps a Service in an HTTP handler.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// -------------------------------------------------------------------------
// POST /v1/accounts
// -------------------------------------------------------------------------

// HandleCreateAccount creates a new user account.
//
//	201 — created successfully
//	400 — invalid input (bad email, missing fields)
//	409 — email already registered
func (h *Handler) HandleCreateAccount(w http.ResponseWriter, r *http.Request) {
	var req CreateAccountRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondErr(w, http.StatusBadRequest, "accounts.bad_request", "Invalid JSON body")
		return
	}

	resp, err := h.svc.CreateAccount(r.Context(), req)
	if err != nil {
		switch {
		case errors.Is(err, ErrInvalidEmail):
			respondErr(w, http.StatusBadRequest, "accounts.invalid_email", "Invalid or missing required fields")
		case errors.Is(err, ErrEmailTaken):
			respondErr(w, http.StatusConflict, "accounts.email_taken", "Email already registered")
		default:
			respondErr(w, http.StatusInternalServerError, "accounts.internal", "Internal server error")
		}
		return
	}

	respond(w, http.StatusCreated, resp)
}

// -------------------------------------------------------------------------
// GET /v1/accounts/verify?token=...
// -------------------------------------------------------------------------

// HandleVerifyEmail processes an email verification link.
//
//	200 — verified
//	400 — token missing
//	404 — token not found
//	410 — token expired or already used
func (h *Handler) HandleVerifyEmail(w http.ResponseWriter, r *http.Request) {
	token := r.URL.Query().Get("token")
	if token == "" {
		respondErr(w, http.StatusBadRequest, "accounts.missing_token", "token query parameter required")
		return
	}

	err := h.svc.VerifyEmail(r.Context(), token)
	if err != nil {
		switch {
		case errors.Is(err, ErrTokenNotFound):
			respondErr(w, http.StatusNotFound, "accounts.token_not_found", "Verification token not found")
		case errors.Is(err, ErrTokenExpired):
			respondErr(w, http.StatusGone, "accounts.token_expired", "Verification token has expired or already been used")
		default:
			respondErr(w, http.StatusInternalServerError, "accounts.internal", "Internal server error")
		}
		return
	}

	respond(w, http.StatusOK, map[string]string{"status": "verified"})
}

// -------------------------------------------------------------------------
// POST /v1/sessions
// -------------------------------------------------------------------------

// HandleLogin authenticates a user and returns a token pair.
//
//	200 — success with access_token + refresh_token
//	400 — bad request
//	401 — invalid credentials
//	403 — account exists but email not verified
func (h *Handler) HandleLogin(w http.ResponseWriter, r *http.Request) {
	var req LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondErr(w, http.StatusBadRequest, "sessions.bad_request", "Invalid JSON body")
		return
	}
	if req.Email == "" || req.AuthHash == "" {
		respondErr(w, http.StatusBadRequest, "sessions.missing_fields", "email and auth_hash are required")
		return
	}

	resp, err := h.svc.Login(r.Context(), req)
	if err != nil {
		switch {
		case errors.Is(err, ErrBadCredentials):
			respondErr(w, http.StatusUnauthorized, "sessions.invalid_credentials", "Invalid email or password")
		case errors.Is(err, ErrUnverified):
			respondErr(w, http.StatusForbidden, "sessions.unverified", "Email address not verified")
		case errors.Is(err, ErrAccountSuspended):
			respondErr(w, http.StatusForbidden, "sessions.suspended", "Account suspended")
		default:
			respondErr(w, http.StatusInternalServerError, "sessions.internal", "Internal server error")
		}
		return
	}

	respond(w, http.StatusOK, resp)
}

// -------------------------------------------------------------------------
// POST /v1/sessions/refresh
// -------------------------------------------------------------------------

// HandleRefresh rotates a refresh token and returns a new token pair.
//
//	200 — new token pair issued
//	400 — bad request
//	401 — token invalid, expired, or revoked
func (h *Handler) HandleRefresh(w http.ResponseWriter, r *http.Request) {
	var req RefreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondErr(w, http.StatusBadRequest, "sessions.bad_request", "Invalid JSON body")
		return
	}
	if req.RefreshToken == "" {
		respondErr(w, http.StatusBadRequest, "sessions.missing_token", "refresh_token is required")
		return
	}

	resp, err := h.svc.RefreshSession(r.Context(), req)
	if err != nil {
		switch {
		case errors.Is(err, ErrBadCredentials),
			errors.Is(err, ErrRefreshRevoked),
			errors.Is(err, ErrRefreshExpired):
			respondErr(w, http.StatusUnauthorized, "sessions.invalid_token", "Refresh token is invalid, expired, or revoked")
		case errors.Is(err, ErrAccountSuspended):
			respondErr(w, http.StatusForbidden, "sessions.suspended", "Account suspended")
		default:
			respondErr(w, http.StatusInternalServerError, "sessions.internal", "Internal server error")
		}
		return
	}

	respond(w, http.StatusOK, resp)
}

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

type errorEnvelope struct {
	Error errorBody `json:"error"`
}

type errorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func respondErr(w http.ResponseWriter, status int, code, message string) {
	respond(w, status, errorEnvelope{Error: errorBody{Code: code, Message: message}})
}

func respond(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
