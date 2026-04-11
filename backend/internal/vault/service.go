package vault

import (
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"log/slog"
	"regexp"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/nikhil/scanvault-api/internal/db"
)

// -------------------------------------------------------------------------
// Sentinel errors
// -------------------------------------------------------------------------

var (
	ErrDocumentNotFound   = errors.New("document not found")
	ErrDocumentConflict   = errors.New("version conflict")
	ErrDuplicateUUID      = errors.New("document with this UUID already exists")
	ErrInvalidUUID        = errors.New("invalid UUID: must be a v7 UUID")
	ErrQuotaExceeded      = errors.New("account quota exceeded")
	ErrPageSizeExceeded   = errors.New("page size exceeds 50 MB limit")
	ErrPageIndexTaken     = errors.New("page index already exists for this document")
	ErrPageNotFound       = errors.New("page not found")
	ErrPageNotConfirmed   = errors.New("page upload not confirmed by R2")
)

// maxPageBytes is 50 MB — the maximum allowed encrypted page size.
const maxPageBytes = 50 * 1024 * 1024

// uuidV7Regex validates a v7 UUID. Version nibble must be 7.
// Example: 018f1234-5678-7abc-bdef-000000000000
var uuidV7Regex = regexp.MustCompile(
	`^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`,
)

// -------------------------------------------------------------------------
// Request / response types
// -------------------------------------------------------------------------

type CreateDocumentRequest struct {
	UUID              string `json:"uuid"`
	EncryptedMetadata string `json:"encrypted_metadata"` // base64
	MetadataNonce     string `json:"metadata_nonce"`     // base64
	PageCount         int32  `json:"page_count"`
}

type DocumentResponse struct {
	UUID              string `json:"uuid"`
	EncryptedMetadata string `json:"encrypted_metadata"`
	MetadataNonce     string `json:"metadata_nonce"`
	Version           int64  `json:"version"`
	PageCount         int32  `json:"page_count"`
	TotalSizeBytes    int64  `json:"total_size_bytes"`
	UpdatedAt         string `json:"updated_at"`
}

type ListDocumentsResponse struct {
	Documents  []DocumentResponse `json:"documents"`
	NextCursor string             `json:"next_cursor,omitempty"`
}

// -------------------------------------------------------------------------
// Service
// -------------------------------------------------------------------------

// Service implements vault document and page operations.
type Service struct {
	pool    *pgxpool.Pool
	queries *db.Queries
	storage ObjectStorage
	logger  *slog.Logger
}

// NewService constructs a vault Service.
func NewService(pool *pgxpool.Pool, storage ObjectStorage, logger *slog.Logger) *Service {
	return &Service{
		pool:    pool,
		queries: db.New(pool),
		storage: storage,
		logger:  logger,
	}
}

// -------------------------------------------------------------------------
// 2A.3 — Create document
// -------------------------------------------------------------------------

// CreateDocument creates a new vault document for the authenticated account.
func (s *Service) CreateDocument(ctx context.Context, accountID int64, req CreateDocumentRequest) (DocumentResponse, error) {
	// Validate UUID format (must be v7)
	if !uuidV7Regex.MatchString(req.UUID) {
		return DocumentResponse{}, ErrInvalidUUID
	}

	// Decode encrypted_metadata
	encMeta, err := base64.StdEncoding.DecodeString(req.EncryptedMetadata)
	if err != nil {
		return DocumentResponse{}, fmt.Errorf("%w: encrypted_metadata: %v", ErrInvalidUUID, err)
	}
	nonce, err := base64.StdEncoding.DecodeString(req.MetadataNonce)
	if err != nil {
		return DocumentResponse{}, fmt.Errorf("%w: metadata_nonce: %v", ErrInvalidUUID, err)
	}

	var parsedUUID pgtype.UUID
	if err := parsedUUID.Scan(req.UUID); err != nil {
		return DocumentResponse{}, ErrInvalidUUID
	}

	// Ensure quota row exists
	if _, err := s.queries.GetOrCreateQuota(ctx, accountID); err != nil {
		return DocumentResponse{}, fmt.Errorf("ensure quota: %w", err)
	}

	doc, err := s.queries.CreateDocument(ctx, db.CreateDocumentParams{
		Uuid:              parsedUUID,
		AccountID:         accountID,
		EncryptedMetadata: encMeta,
		MetadataNonce:     nonce,
		PageCount:         req.PageCount,
	})
	if err != nil {
		if isDuplicateKeyError(err) {
			return DocumentResponse{}, ErrDuplicateUUID
		}
		return DocumentResponse{}, fmt.Errorf("create document: %w", err)
	}

	// Increment document count quota
	if _, err := s.queries.IncrementQuotaUsage(ctx, db.IncrementQuotaUsageParams{
		AccountID:        accountID,
		StorageBytesUsed: 0,
		DocumentsCount:   1,
	}); err != nil {
		s.logger.Error("failed to increment document quota count", slog.String("error", err.Error()))
		// Non-fatal: quota is advisory
	}

	return documentToResponse(doc), nil
}

// -------------------------------------------------------------------------
// 2A.4 — List + Get documents
// -------------------------------------------------------------------------

const defaultListLimit = 50

// ListDocuments returns a cursor-paginated list of documents for the account.
// after is an optional cursor UUID (exclusive). limit <= 50.
func (s *Service) ListDocuments(ctx context.Context, accountID int64, after string, limit int32) (ListDocumentsResponse, error) {
	if limit <= 0 || limit > defaultListLimit {
		limit = defaultListLimit
	}

	var afterUUID pgtype.UUID
	if after != "" {
		if err := afterUUID.Scan(after); err != nil {
			return ListDocumentsResponse{}, fmt.Errorf("invalid cursor: %w", err)
		}
	}
	// pgtype.UUID with Valid=false signals NULL in the query
	if after == "" {
		afterUUID.Valid = false
	}

	docs, err := s.queries.ListDocuments(ctx, db.ListDocumentsParams{
		AccountID: accountID,
		Limit:     limit + 1, // fetch one extra to determine if there's a next page
		AfterUuid: afterUUID,
	})
	if err != nil {
		return ListDocumentsResponse{}, fmt.Errorf("list documents: %w", err)
	}

	var nextCursor string
	if int32(len(docs)) > limit {
		docs = docs[:limit]
		lastUUID := docs[len(docs)-1].Uuid
		nextCursor = uuidToString(lastUUID)
	}

	responses := make([]DocumentResponse, len(docs))
	for i, d := range docs {
		responses[i] = documentToResponse(d)
	}

	return ListDocumentsResponse{Documents: responses, NextCursor: nextCursor}, nil
}

// GetDocument returns a single document and its page manifests.
func (s *Service) GetDocument(ctx context.Context, accountID int64, docUUID string) (DocumentResponse, []db.VaultPage, error) {
	var parsedUUID pgtype.UUID
	if err := parsedUUID.Scan(docUUID); err != nil {
		return DocumentResponse{}, nil, ErrDocumentNotFound
	}

	doc, err := s.queries.GetDocumentByUUID(ctx, db.GetDocumentByUUIDParams{
		Uuid:      parsedUUID,
		AccountID: accountID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return DocumentResponse{}, nil, ErrDocumentNotFound
		}
		return DocumentResponse{}, nil, fmt.Errorf("get document: %w", err)
	}

	pages, err := s.queries.GetPagesByDocument(ctx, doc.ID)
	if err != nil {
		return DocumentResponse{}, nil, fmt.Errorf("get pages: %w", err)
	}

	return documentToResponse(doc), pages, nil
}

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

func documentToResponse(doc db.VaultDocument) DocumentResponse {
	return DocumentResponse{
		UUID:              uuidToString(doc.Uuid),
		EncryptedMetadata: base64.StdEncoding.EncodeToString(doc.EncryptedMetadata),
		MetadataNonce:     base64.StdEncoding.EncodeToString(doc.MetadataNonce),
		Version:           doc.Version,
		PageCount:         doc.PageCount,
		TotalSizeBytes:    doc.TotalSizeBytes,
		UpdatedAt:         doc.UpdatedAt.Time.UTC().Format("2006-01-02T15:04:05Z"),
	}
}

func uuidToString(u pgtype.UUID) string {
	if !u.Valid {
		return ""
	}
	b := u.Bytes
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

// isDuplicateKeyError returns true if err is a Postgres unique constraint violation.
func isDuplicateKeyError(err error) bool {
	if err == nil {
		return false
	}
	// pgx wraps pgconn.PgError; check the SQLSTATE code 23505
	var pgErr interface{ SQLState() string }
	if errors.As(err, &pgErr) {
		return pgErr.SQLState() == "23505"
	}
	return false
}
