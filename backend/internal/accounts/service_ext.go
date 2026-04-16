package accounts

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"

	"github.com/nikhil/scanvault-api/internal/auth"
)

// =========================================================================
// Additional sentinel errors
// =========================================================================

var (
	ErrAccountLocked       = errors.New("account temporarily locked")
	ErrPasswordResetToken  = errors.New("password reset token invalid or expired")
	ErrDeletionNotPending  = errors.New("no deletion pending for this account")
)

// =========================================================================
// 3A.1 — Conflict backup retrieval
// =========================================================================

// ConflictBackup is one row from the conflict_backups table.
type ConflictBackup struct {
	ID         int64  `json:"id"`
	AccountID  int64  `json:"account_id"`
	DocID      string `json:"doc_id"`
	ClientData []byte `json:"client_data"`
	CreatedAt  string `json:"created_at"`
}

// GetConflictBackups returns all conflict backups for the given account and document.
func (s *Service) GetConflictBackups(ctx context.Context, accountID int64, docUUID string) ([]ConflictBackup, error) {
	const q = `
		SELECT id, account_id, doc_id, client_data, created_at
		FROM conflict_backups
		WHERE account_id = $1 AND doc_id = $2
		ORDER BY created_at DESC
	`
	rows, err := s.pool.Query(ctx, q, accountID, docUUID)
	if err != nil {
		return nil, fmt.Errorf("list conflict backups: %w", err)
	}
	defer rows.Close()

	var out []ConflictBackup
	for rows.Next() {
		var cb ConflictBackup
		var createdAt interface{}
		if err := rows.Scan(&cb.ID, &cb.AccountID, &cb.DocID, &cb.ClientData, &createdAt); err != nil {
			return nil, fmt.Errorf("scan conflict backup: %w", err)
		}
		switch v := createdAt.(type) {
		case time.Time:
			cb.CreatedAt = v.UTC().Format(time.RFC3339)
		case fmt.Stringer:
			cb.CreatedAt = v.String()
		default:
			cb.CreatedAt = fmt.Sprint(v)
		}
		out = append(out, cb)
	}
	return out, rows.Err()
}

// =========================================================================
// 3A.4 — CAPTCHA verification (injected into CreateAccount flow)
// =========================================================================

// SetCaptchaVerifier injects the captcha verifier into the service.
// Call this after New() when CAPTCHA_SECRET_KEY is set.
func (s *Service) SetCaptchaVerifier(v captchaVerifierIface) {
	s.captchaVerifier = v
}

// =========================================================================
// 3A.5 — Account lockout helpers
// =========================================================================

const maxFailedAttempts = 5
const lockoutDuration = 15 * time.Minute

// checkAndRecordFailedLogin checks if the account is locked and increments failure count.
// Returns ErrAccountLocked if currently locked.
// Increments failed_login_attempts and sets locked_until after maxFailedAttempts.
func (s *Service) checkAndRecordFailedLogin(ctx context.Context, accountID int64) error {
	// Increment counter and get new values atomically
	const q = `
		UPDATE accounts
		SET failed_login_attempts = failed_login_attempts + 1,
		    locked_until = CASE
		        WHEN failed_login_attempts + 1 >= $2 THEN NOW() + $3::interval
		        ELSE locked_until
		    END,
		    updated_at = NOW()
		WHERE id = $1
		RETURNING failed_login_attempts, locked_until
	`
	lockDuration := fmt.Sprintf("%d seconds", int(lockoutDuration.Seconds()))
	var attempts int
	var lockedUntil pgtype.Timestamptz
	err := s.pool.QueryRow(ctx, q, accountID, maxFailedAttempts, lockDuration).
		Scan(&attempts, &lockedUntil)
	if err != nil {
		s.logger.Error("record failed login", slog.String("err", err.Error()))
	}
	return nil
}

// resetFailedLoginCount resets failed_login_attempts to 0 on successful login.
func (s *Service) resetFailedLoginCount(ctx context.Context, accountID int64) {
	const q = `UPDATE accounts SET failed_login_attempts = 0, locked_until = NULL, updated_at = NOW() WHERE id = $1`
	if _, err := s.pool.Exec(ctx, q, accountID); err != nil {
		s.logger.Error("reset failed login count", slog.String("err", err.Error()))
	}
}

// isAccountLocked returns true if the account's locked_until is in the future.
func (s *Service) isAccountLocked(ctx context.Context, accountID int64) (bool, error) {
	const q = `SELECT locked_until FROM accounts WHERE id = $1`
	var lockedUntil pgtype.Timestamptz
	err := s.pool.QueryRow(ctx, q, accountID).Scan(&lockedUntil)
	if err != nil {
		return false, fmt.Errorf("check lockout: %w", err)
	}
	if lockedUntil.Valid && time.Now().Before(lockedUntil.Time) {
		return true, nil
	}
	return false, nil
}

// =========================================================================
// 3A.6 — Password reset flow
// =========================================================================

const passwordResetTokenTTL = time.Hour

// PasswordResetRequestResponse is returned by PasswordResetRequest.
type PasswordResetRequestResponse struct {
	DataLossWarning string `json:"data_loss_warning"`
}

// PasswordResetRequest creates a password reset token for the given email.
// For privacy, always returns success even if the email is not found.
func (s *Service) PasswordResetRequest(ctx context.Context, email string) (*PasswordResetRequestResponse, error) {
	acc, err := s.q.GetAccountByEmail(ctx, email)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			// Don't reveal whether the email exists
			return &PasswordResetRequestResponse{
				DataLossWarning: "If this email is registered, a reset link has been sent. Note: resetting your password will make previously encrypted documents unrecoverable unless you have your keys backed up.",
			}, nil
		}
		return nil, fmt.Errorf("look up account: %w", err)
	}

	// Generate reset token
	rawToken, tokenHash, err := generatePasswordResetToken()
	if err != nil {
		return nil, fmt.Errorf("generate reset token: %w", err)
	}

	expiresAt := pgtype.Timestamptz{Time: time.Now().Add(passwordResetTokenTTL), Valid: true}
	const q = `
		INSERT INTO password_reset_tokens (account_id, token_hash, expires_at)
		VALUES ($1, $2, $3)
	`
	if _, err := s.pool.Exec(ctx, q, acc.ID, tokenHash, expiresAt); err != nil {
		return nil, fmt.Errorf("store reset token: %w", err)
	}

	// In dev: just log the token. In production: send email via Postmark.
	s.logger.Info("password reset token (stub)",
		slog.String("email", email),
		slog.String("token_preview", rawToken[:8]+"..."),
		slog.String("raw_token", rawToken), // dev only — remove in prod
	)

	return &PasswordResetRequestResponse{
		DataLossWarning: "Resetting your password will make previously encrypted documents unrecoverable unless you have your encryption keys backed up.",
	}, nil
}

// PasswordResetConfirmRequest is the body for confirming a password reset.
type PasswordResetConfirmRequest struct {
	Token       string `json:"token"`
	NewAuthHash string `json:"new_auth_hash"`
	NewAuthParams string `json:"new_auth_params"`
}

// PasswordResetConfirm validates the token and updates the password.
func (s *Service) PasswordResetConfirm(ctx context.Context, req PasswordResetConfirmRequest) error {
	if req.Token == "" || req.NewAuthHash == "" {
		return ErrPasswordResetToken
	}

	tokenHash := hashPasswordResetToken(req.Token)

	const q = `
		SELECT id, account_id, expires_at, used_at
		FROM password_reset_tokens
		WHERE token_hash = $1
		LIMIT 1
	`
	var (
		tokenID   int64
		accountID int64
		expiresAt pgtype.Timestamptz
		usedAt    pgtype.Timestamptz
	)
	err := s.pool.QueryRow(ctx, q, tokenHash).Scan(&tokenID, &accountID, &expiresAt, &usedAt)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrPasswordResetToken
		}
		return fmt.Errorf("lookup reset token: %w", err)
	}

	if usedAt.Valid {
		return ErrPasswordResetToken // already used
	}
	if time.Now().After(expiresAt.Time) {
		return ErrPasswordResetToken // expired
	}

	// Hash new auth material
	newServerHash, err := auth.HashPassword(req.NewAuthHash, s.cfg.Argon2Time, s.cfg.Argon2Memory, s.cfg.Argon2Threads)
	if err != nil {
		return fmt.Errorf("hash new password: %w", err)
	}

	// Atomically: update password + mark token used + revoke all refresh tokens
	conn, err := s.pool.Acquire(ctx)
	if err != nil {
		return fmt.Errorf("acquire connection: %w", err)
	}
	defer conn.Release()

	tx, err := conn.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin tx: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()

	newAuthParams := pgtype.Text{String: req.NewAuthParams, Valid: req.NewAuthParams != ""}
	if _, err := tx.Exec(ctx, `
		UPDATE accounts SET auth_hash = $1, auth_params = $2, updated_at = NOW() WHERE id = $3
	`, newServerHash, newAuthParams, accountID); err != nil {
		return fmt.Errorf("update password: %w", err)
	}

	if _, err := tx.Exec(ctx, `
		UPDATE password_reset_tokens SET used_at = NOW() WHERE id = $1
	`, tokenID); err != nil {
		return fmt.Errorf("mark token used: %w", err)
	}

	if _, err := tx.Exec(ctx, `
		UPDATE refresh_tokens SET revoked_at = NOW() WHERE account_id = $1 AND revoked_at IS NULL
	`, accountID); err != nil {
		return fmt.Errorf("revoke refresh tokens: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("commit tx: %w", err)
	}

	s.logger.Info("password reset confirmed", slog.Int64("account_id", accountID))
	return nil
}

// =========================================================================
// 3A.8 — Account activity endpoint
// =========================================================================

// ActivityEvent is one audit event returned to the client.
type ActivityEvent struct {
	ID        int64           `json:"id"`
	EventType string          `json:"event_type"`
	IPHash    string          `json:"ip_hash,omitempty"`
	Metadata  json.RawMessage `json:"metadata,omitempty"`
	CreatedAt string          `json:"created_at"`
}

// GetAccountActivity returns the last 50 audit events for the account.
func (s *Service) GetAccountActivity(ctx context.Context, accountID int64) ([]ActivityEvent, error) {
	const q = `
		SELECT id, event_type, COALESCE(ip_hash, ''),
		       COALESCE(metadata::text, ''), created_at
		FROM audit_events
		WHERE account_id = $1
		ORDER BY created_at DESC
		LIMIT 50
	`
	rows, err := s.pool.Query(ctx, q, accountID)
	if err != nil {
		return nil, fmt.Errorf("list activity: %w", err)
	}
	defer rows.Close()

	var events []ActivityEvent
	for rows.Next() {
		var e ActivityEvent
		var metaStr string
		var createdAt interface{}
		if err := rows.Scan(&e.ID, &e.EventType, &e.IPHash, &metaStr, &createdAt); err != nil {
			return nil, fmt.Errorf("scan activity event: %w", err)
		}
		if metaStr != "" {
			e.Metadata = json.RawMessage(metaStr)
		}
		switch v := createdAt.(type) {
		case time.Time:
			e.CreatedAt = v.UTC().Format(time.RFC3339)
		case fmt.Stringer:
			e.CreatedAt = v.String()
		default:
			e.CreatedAt = fmt.Sprint(v)
		}
		events = append(events, e)
	}
	return events, rows.Err()
}

// =========================================================================
// 3A.9 — Session listing & revocation
// =========================================================================

// SessionInfo is the public view of one refresh token / session.
type SessionInfo struct {
	UUID        string `json:"uuid"`
	DeviceLabel string `json:"device_label,omitempty"`
	CreatedAt   string `json:"created_at"`
	LastUsedAt  string `json:"last_used_at,omitempty"`
	ExpiresAt   string `json:"expires_at"`
}

// ListSessions returns all active (non-revoked, non-expired) sessions for the account.
func (s *Service) ListSessions(ctx context.Context, accountID int64) ([]SessionInfo, error) {
	const q = `
		SELECT uuid, device_label, created_at, last_used_at, expires_at
		FROM refresh_tokens
		WHERE account_id = $1
		  AND revoked_at IS NULL
		  AND expires_at > NOW()
		ORDER BY last_used_at DESC NULLS LAST
	`
	rows, err := s.pool.Query(ctx, q, accountID)
	if err != nil {
		return nil, fmt.Errorf("list sessions: %w", err)
	}
	defer rows.Close()

	var sessions []SessionInfo
	for rows.Next() {
		var si SessionInfo
		var uuidVal pgtype.UUID
		var deviceLabel *string
		var createdAt, lastUsedAt, expiresAt pgtype.Timestamptz
		if err := rows.Scan(&uuidVal, &deviceLabel, &createdAt, &lastUsedAt, &expiresAt); err != nil {
			return nil, fmt.Errorf("scan session: %w", err)
		}
		if uuidVal.Valid {
			b := uuidVal.Bytes
			si.UUID = fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
				b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
		}
		if deviceLabel != nil {
			si.DeviceLabel = *deviceLabel
		}
		if createdAt.Valid {
			si.CreatedAt = createdAt.Time.UTC().Format(time.RFC3339)
		}
		if lastUsedAt.Valid {
			si.LastUsedAt = lastUsedAt.Time.UTC().Format(time.RFC3339)
		}
		if expiresAt.Valid {
			si.ExpiresAt = expiresAt.Time.UTC().Format(time.RFC3339)
		}
		sessions = append(sessions, si)
	}
	return sessions, rows.Err()
}

// RevokeSession revokes a specific session by UUID.
// Returns ErrBadCredentials if the session doesn't belong to accountID (never 403).
func (s *Service) RevokeSession(ctx context.Context, accountID int64, sessionUUID string) error {
	var parsedUUID pgtype.UUID
	if err := parsedUUID.Scan(sessionUUID); err != nil {
		return ErrBadCredentials
	}

	const q = `
		UPDATE refresh_tokens
		SET revoked_at = NOW()
		WHERE uuid = $1 AND account_id = $2 AND revoked_at IS NULL
	`
	tag, err := s.pool.Exec(ctx, q, parsedUUID, accountID)
	if err != nil {
		return fmt.Errorf("revoke session: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return ErrBadCredentials // not found or already revoked — 404 behaviour
	}
	return nil
}

// =========================================================================
// 3A.10 — Delete account (grace period) & cancel deletion
// =========================================================================

const deletionGracePeriod = 7 * 24 * time.Hour

// DeleteAccountResponse is returned when deletion is initiated.
type DeleteAccountResponse struct {
	ScheduledAt string `json:"scheduled_at"`
	GracePeriod string `json:"grace_period"`
	Message     string `json:"message"`
}

// RequestAccountDeletion marks the account for deletion after a 7-day grace period.
func (s *Service) RequestAccountDeletion(ctx context.Context, accountID int64) (*DeleteAccountResponse, error) {
	scheduledAt := time.Now().UTC().Add(deletionGracePeriod)
	const q = `
		UPDATE accounts
		SET status = 'pending_deletion',
		    deleted_at = $2,
		    updated_at = NOW()
		WHERE id = $1
		RETURNING deleted_at
	`
	var storedAt pgtype.Timestamptz
	err := s.pool.QueryRow(ctx, q, accountID, pgtype.Timestamptz{Time: scheduledAt, Valid: true}).Scan(&storedAt)
	if err != nil {
		return nil, fmt.Errorf("schedule deletion: %w", err)
	}

	s.logger.Info("account deletion scheduled",
		slog.Int64("account_id", accountID),
		slog.Time("scheduled_at", scheduledAt),
	)

	return &DeleteAccountResponse{
		ScheduledAt: scheduledAt.Format(time.RFC3339),
		GracePeriod: "7 days",
		Message:     "Your account will be permanently deleted after the grace period. You can cancel before then.",
	}, nil
}

// CancelAccountDeletion cancels a pending deletion if within the grace period.
func (s *Service) CancelAccountDeletion(ctx context.Context, accountID int64) error {
	const q = `
		UPDATE accounts
		SET status = 'active',
		    deleted_at = NULL,
		    updated_at = NOW()
		WHERE id = $1 AND status = 'pending_deletion'
		RETURNING id
	`
	var id int64
	err := s.pool.QueryRow(ctx, q, accountID).Scan(&id)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrDeletionNotPending
		}
		return fmt.Errorf("cancel deletion: %w", err)
	}
	s.logger.Info("account deletion cancelled", slog.Int64("account_id", accountID))
	return nil
}

// PurgeExpiredAccounts hard-deletes accounts whose deleted_at has passed.
// Safe to call from a background goroutine / scheduler.
func (s *Service) PurgeExpiredAccounts(ctx context.Context) (int64, error) {
	const q = `
		DELETE FROM accounts
		WHERE status = 'pending_deletion'
		  AND deleted_at IS NOT NULL
		  AND deleted_at < NOW()
	`
	tag, err := s.pool.Exec(ctx, q)
	if err != nil {
		return 0, fmt.Errorf("purge expired accounts: %w", err)
	}
	n := tag.RowsAffected()
	if n > 0 {
		s.logger.Info("purged expired accounts", slog.Int64("count", n))
	}
	return n, nil
}

// =========================================================================
// Helpers
// =========================================================================

func generatePasswordResetToken() (raw string, hash []byte, err error) {
	b := make([]byte, 32)
	if _, err = rand.Read(b); err != nil {
		return "", nil, err
	}
	raw = base64.RawURLEncoding.EncodeToString(b)
	sum := sha256.Sum256([]byte(raw))
	return raw, sum[:], nil
}

func hashPasswordResetToken(raw string) []byte {
	sum := sha256.Sum256([]byte(raw))
	return sum[:]
}
