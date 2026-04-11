// Package middleware provides HTTP middleware for the ScanVault API.
package middleware

import (
	"context"
	"encoding/json"
	"net/http"
	"strings"

	"github.com/nikhil/scanvault-api/internal/auth"
	"github.com/nikhil/scanvault-api/internal/db"
)

// contextKey is an unexported type for context keys in this package.
type contextKey int

const (
	// claimsKey is the context key for the authenticated Paseto claims.
	claimsKey contextKey = iota
	// accountKey is the context key for the loaded account row.
	accountKey
)

// AccountStore is a minimal interface for loading accounts by ID.
// Satisfied by *db.Queries.
type AccountStore interface {
	GetAccountByID(ctx context.Context, id int64) (db.Account, error)
}

// AuthMiddleware verifies the Bearer Paseto token on each request and loads
// the account into the request context. Returns 401 for missing/invalid tokens
// and 403 for suspended accounts.
func AuthMiddleware(maker *auth.PasetoMaker, store AccountStore) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				respondUnauthorized(w, "auth.missing_token", "Authorization header required")
				return
			}

			const prefix = "Bearer "
			if !strings.HasPrefix(authHeader, prefix) {
				respondUnauthorized(w, "auth.invalid_header", "Authorization header must use Bearer scheme")
				return
			}

			tokenStr := authHeader[len(prefix):]
			if tokenStr == "" {
				respondUnauthorized(w, "auth.missing_token", "Bearer token is empty")
				return
			}

			claims, err := maker.VerifyAccessToken(tokenStr)
			if err != nil {
				respondUnauthorized(w, "auth.invalid_token", "Token is invalid or expired")
				return
			}

			// Load account to check current status (suspended check).
			account, err := store.GetAccountByID(r.Context(), claims.AccountID)
			if err != nil {
				respondUnauthorized(w, "auth.account_not_found", "Account not found")
				return
			}

			if account.Status == "suspended" || account.Status == "pending_deletion" {
				respondForbidden(w, "auth.account_suspended", "Account is suspended")
				return
			}

			// Store both claims and account in context for downstream handlers.
			ctx := context.WithValue(r.Context(), claimsKey, claims)
			ctx = context.WithValue(ctx, accountKey, &account)

			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// ClaimsFromContext retrieves the Paseto claims stored by AuthMiddleware.
// Returns nil if the middleware was not applied or the token was invalid.
func ClaimsFromContext(ctx context.Context) *auth.Claims {
	v, _ := ctx.Value(claimsKey).(*auth.Claims)
	return v
}

// AccountFromContext retrieves the loaded account stored by AuthMiddleware.
// Returns nil if the middleware was not applied.
func AccountFromContext(ctx context.Context) *db.Account {
	v, _ := ctx.Value(accountKey).(*db.Account)
	return v
}

// InjectClaimsForTest injects claims and an optional account into a context.
// This is intended for use in tests that need to simulate authenticated requests
// without running the full auth middleware stack.
func InjectClaimsForTest(ctx context.Context, claims *auth.Claims, account *db.Account) context.Context {
	ctx = context.WithValue(ctx, claimsKey, claims)
	if account != nil {
		ctx = context.WithValue(ctx, accountKey, account)
	}
	return ctx
}

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

type errEnvelope struct {
	Error errBody `json:"error"`
}

type errBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func respondUnauthorized(w http.ResponseWriter, code, message string) {
	writeJSON(w, http.StatusUnauthorized, errEnvelope{Error: errBody{Code: code, Message: message}})
}

func respondForbidden(w http.ResponseWriter, code, message string) {
	writeJSON(w, http.StatusForbidden, errEnvelope{Error: errBody{Code: code, Message: message}})
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
