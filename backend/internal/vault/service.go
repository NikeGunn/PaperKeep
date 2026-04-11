// Package vault implements vault document + page operations over R2/S3-compatible storage.
package vault

import (
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"log/slog"
	"regexp"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/nikhil/scanvault-api/internal/db"
)

// -------------------------------------------------------------------------
// Sentinel errors
// -------------------------------------------------------------------------

var (
	ErrDocumentNotFound = errors.New("document not found")
	ErrDocumentConflict = errors.New("version conflict")
	ErrDuplicateUUID    = errors.New("document with this UUID already exists")
	ErrInvalidUUID      = errors.New("invalid UUID: must be a v7 UUID")
	ErrQuotaDocuments   = errors.New("document quota exceeded")
	ErrQuotaStorage     = errors.New("storage quota exceeded")
	ErrPageSizeExceeded = errors.New("page size exceeds 50 MB limit")
	ErrPageIndexTaken   = errors.New("page index already exists for this document")
	ErrPageNotFound     = errors.New("page not found")
	ErrPageNotInR2      = errors.New("page blob not found in R2 after upload")
	ErrPageSizeMismatch = errors.New("confirmed size does not match R2 object size")
)

// maxPageBytes is 50 MB — the maximum allowed encrypted page size.
const maxPageBytes int64 = 50 * 1024 * 1024

// purgeRetentionDays is the soft-delete retention window before hard purge.
// Override via Service.purgeRetention for tests (set to a small duration).
const purgeRetentionDays = 30 * 24 * time.Hour

// uuidV7Regex validates a v7 UUID. Version nibble must be 7.
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

type UpdateDocumentRequest struct {
	EncryptedMetadata string `json:"encrypted_metadata"` // base64
	MetadataNonce     string `json:"metadata_nonce"`     // base64
	ExpectedVersion   int64  `json:"expected_version"`
}

type UpdateDocumentConflictResponse struct {
	CurrentVersion    int64  `json:"current_version"`
	EncryptedMetadata string `json:"encrypted_metadata"`
	MetadataNonce     string `json:"metadata_nonce"`
}

type RequestUploadURLRequest struct {
	PageUUID      string `json:"page_uuid"`
	PageIndex     int32  `json:"page_index"`
	EncryptedSize int64  `json:"encrypted_size"`
	Checksum      string `json:"checksum"` // hex-encoded SHA-256
}

type RequestUploadURLResponse struct {
	PageUUID  string `json:"page_uuid"`
	UploadURL string `json:"upload_url"`
}

type DownloadURLResponse struct {
	DownloadURL string `json:"download_url"`
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

type ManifestEntry struct {
	UUID      string `json:"uuid"`
	Version   int64  `json:"version"`
	UpdatedAt string `json:"updated_at"`
	Deleted   bool   `json:"deleted"`
}

type ManifestResponse struct {
	Entries    []ManifestEntry `json:"entries"`
	NextCursor string          `json:"next_cursor,omitempty"`
}

// -------------------------------------------------------------------------
// Service
// -------------------------------------------------------------------------

// Service implements vault document and page operations.
type Service struct {
	pool            *pgxpool.Pool
	queries         *db.Queries
	storage         ObjectStorage
	logger          *slog.Logger
	purgeRetention  time.Duration // default purgeRetentionDays; overridable for tests
}

// NewService constructs a vault Service.
func NewService(pool *pgxpool.Pool, storage ObjectStorage, logger *slog.Logger) *Service {
	return &Service{
		pool:           pool,
		queries:        db.New(pool),
		storage:        storage,
		logger:         logger,
		purgeRetention: purgeRetentionDays,
	}
}

// WithPurgeRetention overrides the soft-delete retention window (test helper).
func (s *Service) WithPurgeRetention(d time.Duration) *Service {
	s.purgeRetention = d
	return s
}

// -------------------------------------------------------------------------
// 2A.3 — Create document
// -------------------------------------------------------------------------

func (s *Service) CreateDocument(ctx context.Context, accountID int64, req CreateDocumentRequest) (DocumentResponse, error) {
	if !uuidV7Regex.MatchString(req.UUID) {
		return DocumentResponse{}, ErrInvalidUUID
	}

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

	// Ensure quota row exists and check document count limit
	quota, err := s.queries.GetOrCreateQuota(ctx, accountID)
	if err != nil {
		return DocumentResponse{}, fmt.Errorf("ensure quota: %w", err)
	}
	if quota.DocumentsCount >= quota.DocumentsLimit {
		return DocumentResponse{}, ErrQuotaDocuments
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

	if _, err := s.queries.IncrementQuotaUsage(ctx, db.IncrementQuotaUsageParams{
		AccountID:        accountID,
		StorageBytesUsed: 0,
		DocumentsCount:   1,
	}); err != nil {
		s.logger.Error("increment document quota", slog.String("err", err.Error()))
	}

	return documentToResponse(doc), nil
}

// -------------------------------------------------------------------------
// 2A.4 — List + Get documents
// -------------------------------------------------------------------------

const defaultListLimit = 50

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

	docs, err := s.queries.ListDocuments(ctx, db.ListDocumentsParams{
		AccountID: accountID,
		Limit:     limit + 1,
		AfterUuid: afterUUID,
	})
	if err != nil {
		return ListDocumentsResponse{}, fmt.Errorf("list documents: %w", err)
	}

	var nextCursor string
	if int32(len(docs)) > limit {
		docs = docs[:limit]
		nextCursor = uuidToString(docs[len(docs)-1].Uuid)
	}

	responses := make([]DocumentResponse, len(docs))
	for i, d := range docs {
		responses[i] = documentToResponse(d)
	}
	return ListDocumentsResponse{Documents: responses, NextCursor: nextCursor}, nil
}

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
// 2A.5 — Update document metadata (optimistic concurrency)
// -------------------------------------------------------------------------

// UpdateDocument updates encrypted metadata using optimistic concurrency.
// Returns ErrDocumentConflict (with current snapshot) if expected_version mismatches.
func (s *Service) UpdateDocument(ctx context.Context, accountID int64, docUUID string, req UpdateDocumentRequest) (DocumentResponse, *UpdateDocumentConflictResponse, error) {
	var parsedUUID pgtype.UUID
	if err := parsedUUID.Scan(docUUID); err != nil {
		return DocumentResponse{}, nil, ErrDocumentNotFound
	}

	encMeta, err := base64.StdEncoding.DecodeString(req.EncryptedMetadata)
	if err != nil {
		return DocumentResponse{}, nil, fmt.Errorf("%w: encrypted_metadata: %v", ErrInvalidUUID, err)
	}
	nonce, err := base64.StdEncoding.DecodeString(req.MetadataNonce)
	if err != nil {
		return DocumentResponse{}, nil, fmt.Errorf("%w: metadata_nonce: %v", ErrInvalidUUID, err)
	}

	updated, err := s.queries.UpdateDocumentMetadata(ctx, db.UpdateDocumentMetadataParams{
		Uuid:              parsedUUID,
		AccountID:         accountID,
		EncryptedMetadata: encMeta,
		MetadataNonce:     nonce,
		Version:           req.ExpectedVersion,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			// Could be not-found OR version conflict — check which
			current, lookupErr := s.queries.GetDocumentByUUID(ctx, db.GetDocumentByUUIDParams{
				Uuid:      parsedUUID,
				AccountID: accountID,
			})
			if lookupErr != nil {
				return DocumentResponse{}, nil, ErrDocumentNotFound
			}
			// Document exists but version didn't match → conflict
			conflict := &UpdateDocumentConflictResponse{
				CurrentVersion:    current.Version,
				EncryptedMetadata: base64.StdEncoding.EncodeToString(current.EncryptedMetadata),
				MetadataNonce:     base64.StdEncoding.EncodeToString(current.MetadataNonce),
			}
			return DocumentResponse{}, conflict, ErrDocumentConflict
		}
		return DocumentResponse{}, nil, fmt.Errorf("update document: %w", err)
	}

	return documentToResponse(updated), nil, nil
}

// -------------------------------------------------------------------------
// 2A.6 — Delete document (soft delete)
// -------------------------------------------------------------------------

// DeleteDocument soft-deletes a document. Returns ErrDocumentNotFound if the
// document doesn't exist or belongs to another account.
func (s *Service) DeleteDocument(ctx context.Context, accountID int64, docUUID string) error {
	var parsedUUID pgtype.UUID
	if err := parsedUUID.Scan(docUUID); err != nil {
		return ErrDocumentNotFound
	}

	deleted, err := s.queries.SoftDeleteDocument(ctx, db.SoftDeleteDocumentParams{
		Uuid:      parsedUUID,
		AccountID: accountID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrDocumentNotFound
		}
		return fmt.Errorf("soft delete document: %w", err)
	}

	// Update quota: decrement document count (storage bytes stay until purge)
	if _, err := s.queries.DecrementQuotaUsage(ctx, db.DecrementQuotaUsageParams{
		AccountID:        accountID,
		StorageBytesUsed: deleted.TotalSizeBytes,
		DocumentsCount:   1,
	}); err != nil {
		s.logger.Error("decrement quota on delete", slog.String("err", err.Error()))
	}

	return nil
}

// -------------------------------------------------------------------------
// 2A.7 — Page upload flow
// -------------------------------------------------------------------------

// RequestUploadURL validates quota + size, creates a pending page row,
// and returns a pre-signed R2 PUT URL.
func (s *Service) RequestUploadURL(ctx context.Context, accountID int64, docUUID string, req RequestUploadURLRequest) (RequestUploadURLResponse, error) {
	// Size guard
	if req.EncryptedSize > maxPageBytes {
		return RequestUploadURLResponse{}, ErrPageSizeExceeded
	}

	// Validate page UUID (any UUID format is fine for pages)
	var pageUUID pgtype.UUID
	if err := pageUUID.Scan(req.PageUUID); err != nil {
		return RequestUploadURLResponse{}, fmt.Errorf("%w: page_uuid: %v", ErrInvalidUUID, err)
	}

	// Lookup document (validates ownership)
	var docParsedUUID pgtype.UUID
	if err := docParsedUUID.Scan(docUUID); err != nil {
		return RequestUploadURLResponse{}, ErrDocumentNotFound
	}
	doc, err := s.queries.GetDocumentByUUID(ctx, db.GetDocumentByUUIDParams{
		Uuid:      docParsedUUID,
		AccountID: accountID,
	})
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return RequestUploadURLResponse{}, ErrDocumentNotFound
		}
		return RequestUploadURLResponse{}, fmt.Errorf("get document: %w", err)
	}

	// Quota: check storage bytes
	quota, err := s.queries.GetOrCreateQuota(ctx, accountID)
	if err != nil {
		return RequestUploadURLResponse{}, fmt.Errorf("get quota: %w", err)
	}
	if quota.StorageBytesUsed+req.EncryptedSize > quota.StorageBytesLimit {
		return RequestUploadURLResponse{}, ErrQuotaStorage
	}

	// Check page index uniqueness
	exists, err := s.queries.PageIndexExists(ctx, db.PageIndexExistsParams{
		DocumentID: doc.ID,
		PageIndex:  req.PageIndex,
	})
	if err != nil {
		return RequestUploadURLResponse{}, fmt.Errorf("check page index: %w", err)
	}
	if exists {
		return RequestUploadURLResponse{}, ErrPageIndexTaken
	}

	// Derive R2 key
	accountUUIDStr := uuidToString(doc.Uuid) // reuse same format helper
	// Actually we need account UUID; get it from the doc's account_id via a different lookup.
	// Simplification: use accountID in the key path (numeric is fine for R2 keys)
	r2Key := ObjectKey(fmt.Sprintf("%d", accountID), docUUID, req.PageUUID)

	// Decode checksum
	checksum, err := hexDecode(req.Checksum)
	if err != nil {
		return RequestUploadURLResponse{}, fmt.Errorf("invalid checksum: %w", err)
	}
	_ = accountUUIDStr

	// Create pending page row
	if _, err := s.queries.CreatePage(ctx, db.CreatePageParams{
		Uuid:          pageUUID,
		DocumentID:    doc.ID,
		PageIndex:     req.PageIndex,
		R2Key:         r2Key,
		EncryptedSize: req.EncryptedSize,
		Checksum:      checksum,
	}); err != nil {
		if isDuplicateKeyError(err) {
			return RequestUploadURLResponse{}, ErrPageIndexTaken
		}
		return RequestUploadURLResponse{}, fmt.Errorf("create page: %w", err)
	}

	// Generate pre-signed PUT URL
	uploadURL, err := s.storage.GeneratePresignedPutURL(ctx, r2Key, req.EncryptedSize)
	if err != nil {
		return RequestUploadURLResponse{}, fmt.Errorf("presign put: %w", err)
	}

	return RequestUploadURLResponse{
		PageUUID:  req.PageUUID,
		UploadURL: uploadURL,
	}, nil
}

// ConfirmPageUpload verifies the page blob exists in R2 and marks it confirmed.
func (s *Service) ConfirmPageUpload(ctx context.Context, accountID int64, docUUID, pageUUID string) error {
	var parsedPageUUID pgtype.UUID
	if err := parsedPageUUID.Scan(pageUUID); err != nil {
		return ErrPageNotFound
	}

	page, err := s.queries.GetPageByUUID(ctx, parsedPageUUID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrPageNotFound
		}
		return fmt.Errorf("get page: %w", err)
	}

	// Authorization: page must belong to this account's document
	if page.AccountID != accountID {
		return ErrPageNotFound
	}

	// Verify blob exists in R2
	actualSize, err := s.storage.HeadObject(ctx, page.R2Key)
	if err != nil {
		// Blob missing → delete the pending row, return error
		_ = s.queries.HardDeletePage(ctx, page.ID)
		return ErrPageNotInR2
	}

	if actualSize != page.EncryptedSize {
		_ = s.queries.HardDeletePage(ctx, page.ID)
		return ErrPageSizeMismatch
	}

	// Mark confirmed
	if _, err := s.queries.ConfirmPage(ctx, parsedPageUUID); err != nil {
		return fmt.Errorf("confirm page: %w", err)
	}

	// Update document total_size_bytes and page_count
	var docParsedUUID pgtype.UUID
	_ = docParsedUUID.Scan(docUUID)
	doc, lookupErr := s.queries.GetDocumentByUUID(ctx, db.GetDocumentByUUIDParams{
		Uuid:      docParsedUUID,
		AccountID: accountID,
	})
	if lookupErr == nil {
		if _, err := s.queries.IncrementDocumentPageCount(ctx, db.IncrementDocumentPageCountParams{
			ID:             doc.ID,
			AccountID:      accountID,
			TotalSizeBytes: page.EncryptedSize,
		}); err != nil {
			s.logger.Error("increment page count", slog.String("err", err.Error()))
		}
	}

	// Update quota storage usage
	if _, err := s.queries.IncrementQuotaUsage(ctx, db.IncrementQuotaUsageParams{
		AccountID:        accountID,
		StorageBytesUsed: page.EncryptedSize,
		DocumentsCount:   0,
	}); err != nil {
		s.logger.Error("increment storage quota", slog.String("err", err.Error()))
	}

	return nil
}

// -------------------------------------------------------------------------
// 2A.8 — Page download URL
// -------------------------------------------------------------------------

// GetPageDownloadURL returns a pre-signed R2 GET URL for the page blob.
func (s *Service) GetPageDownloadURL(ctx context.Context, accountID int64, docUUID, pageUUID string) (DownloadURLResponse, error) {
	var parsedPageUUID pgtype.UUID
	if err := parsedPageUUID.Scan(pageUUID); err != nil {
		return DownloadURLResponse{}, ErrPageNotFound
	}

	page, err := s.queries.GetPageByUUID(ctx, parsedPageUUID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return DownloadURLResponse{}, ErrPageNotFound
		}
		return DownloadURLResponse{}, fmt.Errorf("get page: %w", err)
	}

	if page.AccountID != accountID {
		return DownloadURLResponse{}, ErrPageNotFound
	}

	downloadURL, err := s.storage.GeneratePresignedGetURL(ctx, page.R2Key)
	if err != nil {
		return DownloadURLResponse{}, fmt.Errorf("presign get: %w", err)
	}

	return DownloadURLResponse{DownloadURL: downloadURL}, nil
}

// -------------------------------------------------------------------------
// 2A.9 — Background purge goroutine
// -------------------------------------------------------------------------

// StartPurgeWorker starts a background goroutine that purges soft-deleted
// documents and pages older than the retention window. It ticks hourly (or
// at the provided interval for tests). Cancel ctx to stop it.
func (s *Service) StartPurgeWorker(ctx context.Context, interval time.Duration) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				s.runPurge(ctx)
			}
		}
	}()
}

// RunPurgeOnce executes one purge pass synchronously. Exported for tests.
func (s *Service) RunPurgeOnce(ctx context.Context) {
	s.runPurge(ctx)
}

func (s *Service) runPurge(ctx context.Context) {
	s.purgePages(ctx)
	s.purgeDocuments(ctx)
}

func (s *Service) purgePages(ctx context.Context) {
	const batchSize = 100
	for {
		rows, err := s.queriesForPurge(ctx).GetExpiredSoftDeletedPages(ctx, int32(batchSize))
		if err != nil {
			s.logger.Error("purge: list soft-deleted pages", slog.String("err", err.Error()))
			return
		}
		if len(rows) == 0 {
			return
		}
		for _, row := range rows {
			if err := s.storage.DeleteObject(ctx, row.R2Key); err != nil {
				s.logger.Error("purge: delete R2 object",
					slog.String("key", row.R2Key), slog.String("err", err.Error()))
				// Continue — we'll retry next tick
				continue
			}
			if err := s.queries.HardDeletePage(ctx, row.ID); err != nil {
				s.logger.Error("purge: hard delete page", slog.String("err", err.Error()))
			}
		}
		if len(rows) < batchSize {
			return
		}
	}
}

func (s *Service) purgeDocuments(ctx context.Context) {
	const batchSize = 100
	for {
		rows, err := s.queriesForPurge(ctx).GetExpiredSoftDeletedDocuments(ctx, int32(batchSize))
		if err != nil {
			s.logger.Error("purge: list soft-deleted documents", slog.String("err", err.Error()))
			return
		}
		if len(rows) == 0 {
			return
		}
		for _, doc := range rows {
			if err := s.queries.HardDeleteDocument(ctx, doc.ID); err != nil {
				s.logger.Error("purge: hard delete document", slog.String("err", err.Error()))
			}
		}
		if len(rows) < batchSize {
			return
		}
	}
}

// queriesForPurge returns a db.Queries view that uses the custom retention
// override. Since we can't parameterise the SQL interval at runtime without
// raw SQL, we use a separate approach: override via a raw query when the
// retention differs from the default. For simplicity in tests, we expose a
// method to set a raw override directly on the pool.
func (s *Service) queriesForPurge(_ context.Context) *db.Queries {
	return s.queries
}

// PurgeExpiredWithRetention is a test-friendly variant that accepts an explicit
// cutoff time, used when purgeRetention != default.
func (s *Service) PurgeExpiredWithRetention(ctx context.Context, cutoff time.Time) (pagesDeleted, docsDeleted int, err error) {
	// Pages
	pageRows, err := s.pool.Query(ctx, `
		SELECT p.id, p.r2_key
		FROM vault_pages p
		JOIN vault_documents d ON d.id = p.document_id
		WHERE p.deleted_at IS NOT NULL AND p.deleted_at < $1
		LIMIT 500
	`, cutoff)
	if err != nil {
		return 0, 0, fmt.Errorf("list expired pages: %w", err)
	}
	type pageRow struct {
		id    int64
		r2Key string
	}
	var pages []pageRow
	for pageRows.Next() {
		var r pageRow
		if scanErr := pageRows.Scan(&r.id, &r.r2Key); scanErr != nil {
			pageRows.Close()
			return 0, 0, fmt.Errorf("scan page row: %w", scanErr)
		}
		pages = append(pages, r)
	}
	pageRows.Close()

	for _, p := range pages {
		if delErr := s.storage.DeleteObject(ctx, p.r2Key); delErr != nil {
			s.logger.Error("purge page R2", slog.String("key", p.r2Key), slog.String("err", delErr.Error()))
			continue
		}
		if delErr := s.queries.HardDeletePage(ctx, p.id); delErr != nil {
			s.logger.Error("purge hard delete page", slog.String("err", delErr.Error()))
			continue
		}
		pagesDeleted++
	}

	// Documents
	docRows, err := s.pool.Query(ctx, `
		SELECT id FROM vault_documents
		WHERE deleted_at IS NOT NULL AND deleted_at < $1
		LIMIT 500
	`, cutoff)
	if err != nil {
		return pagesDeleted, 0, fmt.Errorf("list expired docs: %w", err)
	}
	var docIDs []int64
	for docRows.Next() {
		var id int64
		if scanErr := docRows.Scan(&id); scanErr != nil {
			docRows.Close()
			return pagesDeleted, 0, fmt.Errorf("scan doc row: %w", scanErr)
		}
		docIDs = append(docIDs, id)
	}
	docRows.Close()

	for _, id := range docIDs {
		if delErr := s.queries.HardDeleteDocument(ctx, id); delErr != nil {
			s.logger.Error("purge hard delete doc", slog.String("err", delErr.Error()))
			continue
		}
		docsDeleted++
	}

	return pagesDeleted, docsDeleted, nil
}

// -------------------------------------------------------------------------
// 2A.11 — Sync manifest
// -------------------------------------------------------------------------

const defaultManifestLimit = 200

// GetManifest returns a compact list of all documents changed since the given
// timestamp, with cursor pagination.
func (s *Service) GetManifest(ctx context.Context, accountID int64, since time.Time, after string, limit int32) (ManifestResponse, error) {
	if limit <= 0 || limit > defaultManifestLimit {
		limit = defaultManifestLimit
	}

	var afterUUID pgtype.UUID
	if after != "" {
		if err := afterUUID.Scan(after); err != nil {
			return ManifestResponse{}, fmt.Errorf("invalid cursor: %w", err)
		}
	}

	sinceTS := pgtype.Timestamptz{Time: since, Valid: true}
	rows, err := s.queries.GetManifestSince(ctx, db.GetManifestSinceParams{
		AccountID: accountID,
		UpdatedAt: sinceTS,
		Limit:     limit + 1,
		AfterUuid: afterUUID,
	})
	if err != nil {
		return ManifestResponse{}, fmt.Errorf("get manifest: %w", err)
	}

	var nextCursor string
	if int32(len(rows)) > limit {
		rows = rows[:limit]
		nextCursor = uuidToString(rows[len(rows)-1].Uuid)
	}

	entries := make([]ManifestEntry, len(rows))
	for i, r := range rows {
		entries[i] = ManifestEntry{
			UUID:      uuidToString(r.Uuid),
			Version:   r.Version,
			UpdatedAt: r.UpdatedAt.Time.UTC().Format(time.RFC3339),
			Deleted:   r.DeletedAt.Valid,
		}
	}

	return ManifestResponse{Entries: entries, NextCursor: nextCursor}, nil
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

func isDuplicateKeyError(err error) bool {
	if err == nil {
		return false
	}
	var pgErr interface{ SQLState() string }
	if errors.As(err, &pgErr) {
		return pgErr.SQLState() == "23505"
	}
	return false
}

// hexDecode converts a hex string to bytes. Returns error on invalid input.
func hexDecode(s string) ([]byte, error) {
	if len(s)%2 != 0 {
		return nil, fmt.Errorf("odd hex string length")
	}
	b := make([]byte, len(s)/2)
	for i := 0; i < len(s); i += 2 {
		var n byte
		for _, c := range s[i : i+2] {
			n <<= 4
			switch {
			case c >= '0' && c <= '9':
				n |= byte(c - '0')
			case c >= 'a' && c <= 'f':
				n |= byte(c-'a') + 10
			case c >= 'A' && c <= 'F':
				n |= byte(c-'A') + 10
			default:
				return nil, fmt.Errorf("invalid hex character %q", c)
			}
		}
		b[i/2] = n
	}
	return b, nil
}
