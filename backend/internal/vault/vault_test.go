// Package vault_test contains integration tests for vault endpoints.
// Tests use real Postgres via testcontainers and a mock ObjectStorage.
package vault_test

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	"database/sql"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	_ "github.com/lib/pq"
	"github.com/pressly/goose/v3"
	"github.com/testcontainers/testcontainers-go"
	pg "github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/nikhil/scanvault-api/internal/auth"
	"github.com/nikhil/scanvault-api/internal/db"
	appmiddleware "github.com/nikhil/scanvault-api/internal/middleware"
	"github.com/nikhil/scanvault-api/internal/vault"
)

// -------------------------------------------------------------------------
// Test fixtures
// -------------------------------------------------------------------------

func migrationsDir(t *testing.T) string {
	t.Helper()
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("could not determine caller file path")
	}
	return filepath.Join(filepath.Dir(file), "..", "..", "db", "migrations")
}

func startPostgres(t *testing.T) (dsn string, cleanup func()) {
	t.Helper()
	ctx := context.Background()
	ctr, err := pg.Run(ctx, "postgres:16-alpine",
		pg.WithDatabase("testdb"),
		pg.WithUsername("testuser"),
		pg.WithPassword("testpass"),
		testcontainers.WithWaitStrategy(
			wait.ForLog("database system is ready to accept connections").
				WithOccurrence(2).WithStartupTimeout(60*time.Second),
		),
	)
	if err != nil {
		t.Fatalf("start postgres container: %v", err)
	}
	host, _ := ctr.Host(ctx)
	port, _ := ctr.MappedPort(ctx, "5432")
	dsn = fmt.Sprintf("postgres://testuser:testpass@%s:%s/testdb?sslmode=disable", host, port.Port())
	cleanup = func() { _ = ctr.Terminate(ctx) }
	return dsn, cleanup
}

func runMigrations(t *testing.T, dsn, direction string) {
	t.Helper()
	dir := migrationsDir(t)
	sqlDB, err := sql.Open("postgres", dsn)
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	defer sqlDB.Close()
	goose.SetTableName("schema_migrations")
	_ = goose.SetDialect("postgres")
	switch direction {
	case "up":
		if err := goose.Up(sqlDB, dir); err != nil {
			t.Fatalf("goose up: %v", err)
		}
	case "down":
		if err := goose.DownTo(sqlDB, dir, 0); err != nil {
			t.Fatalf("goose down: %v", err)
		}
	}
}

func openPool(t *testing.T, dsn string) *pgxpool.Pool {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatalf("open pool: %v", err)
	}
	return pool
}

func tableExists(t *testing.T, pool *pgxpool.Pool, table string) bool {
	t.Helper()
	var exists bool
	_ = pool.QueryRow(context.Background(),
		`SELECT EXISTS(SELECT FROM information_schema.tables WHERE table_schema='public' AND table_name=$1)`,
		table).Scan(&exists)
	return exists
}

func indexExists(t *testing.T, pool *pgxpool.Pool, index string) bool {
	t.Helper()
	var exists bool
	_ = pool.QueryRow(context.Background(),
		`SELECT EXISTS(SELECT FROM pg_indexes WHERE schemaname='public' AND indexname=$1)`,
		index).Scan(&exists)
	return exists
}

// -------------------------------------------------------------------------
// 2A.2 — Migration tests
// -------------------------------------------------------------------------

func TestMigration0002_UpCreatesVaultTables(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")

	pool := openPool(t, dsn)
	defer pool.Close()

	for _, tbl := range []string{"vault_documents", "vault_pages", "account_quotas"} {
		if !tableExists(t, pool, tbl) {
			t.Errorf("table %q not created by migration 0002", tbl)
		}
	}

	for _, idx := range []string{
		"idx_vault_documents_account",
		"idx_vault_documents_account_updated",
		"idx_vault_pages_document",
		"idx_vault_pages_document_index",
	} {
		if !indexExists(t, pool, idx) {
			t.Errorf("index %q not created", idx)
		}
	}
}

func TestMigration0002_DownDropsVaultTables(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	runMigrations(t, dsn, "down")

	pool := openPool(t, dsn)
	defer pool.Close()

	for _, tbl := range []string{"vault_documents", "vault_pages", "account_quotas"} {
		if tableExists(t, pool, tbl) {
			t.Errorf("table %q still exists after migration down", tbl)
		}
	}
}

func TestMigration0002_VaultDocumentConstraints(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")

	pool := openPool(t, dsn)
	defer pool.Close()
	ctx := context.Background()

	// Insert an account first
	var accountID int64
	err := pool.QueryRow(ctx, `
		INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params)
		VALUES ('vault@example.com', 'hash', '{}', '\x00', '\x00', '{}')
		RETURNING id
	`).Scan(&accountID)
	if err != nil {
		t.Fatalf("insert account: %v", err)
	}

	// Insert valid document
	_, err = pool.Exec(ctx, `
		INSERT INTO vault_documents (uuid, account_id, encrypted_metadata, metadata_nonce)
		VALUES (gen_random_uuid(), $1, '\xdeadbeef', '\xdeadbeef')
	`, accountID)
	if err != nil {
		t.Fatalf("insert valid document: %v", err)
	}

	// NOT NULL: encrypted_metadata must be present
	_, err = pool.Exec(ctx, `
		INSERT INTO vault_documents (uuid, account_id, encrypted_metadata, metadata_nonce)
		VALUES (gen_random_uuid(), $1, NULL, '\xdeadbeef')
	`, accountID)
	if err == nil {
		t.Error("expected NOT NULL violation for encrypted_metadata")
	}

	// FK: non-existent account_id
	_, err = pool.Exec(ctx, `
		INSERT INTO vault_documents (uuid, account_id, encrypted_metadata, metadata_nonce)
		VALUES (gen_random_uuid(), 99999, '\xdeadbeef', '\xdeadbeef')
	`)
	if err == nil {
		t.Error("expected FK violation for non-existent account_id")
	}
}

func TestMigration0002_VaultPageConstraints(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")

	pool := openPool(t, dsn)
	defer pool.Close()
	ctx := context.Background()

	var accountID int64
	_ = pool.QueryRow(ctx, `
		INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params)
		VALUES ('page_test@example.com', 'hash', '{}', '\x00', '\x00', '{}')
		RETURNING id
	`).Scan(&accountID)

	var docID int64
	_ = pool.QueryRow(ctx, `
		INSERT INTO vault_documents (uuid, account_id, encrypted_metadata, metadata_nonce)
		VALUES (gen_random_uuid(), $1, '\xdeadbeef', '\xdeadbeef')
		RETURNING id
	`, accountID).Scan(&docID)

	// Duplicate page_index for same document should fail
	insertPage := func(idx int) error {
		_, err := pool.Exec(ctx, `
			INSERT INTO vault_pages (uuid, document_id, page_index, r2_key, encrypted_size, checksum)
			VALUES (gen_random_uuid(), $1, $2, 'vault/a/b/c.enc', 1024, '\xdeadbeef')
		`, docID, idx)
		return err
	}

	if err := insertPage(0); err != nil {
		t.Fatalf("first page insert: %v", err)
	}
	if err := insertPage(0); err == nil {
		t.Error("duplicate page_index should violate unique constraint")
	}

	// Invalid status
	_, err := pool.Exec(ctx, `
		INSERT INTO vault_pages (uuid, document_id, page_index, r2_key, encrypted_size, checksum, status)
		VALUES (gen_random_uuid(), $1, 5, 'vault/a/b/c2.enc', 1024, '\xdeadbeef', 'invalid_status')
	`, docID)
	if err == nil {
		t.Error("invalid status should violate CHECK constraint")
	}
}

// -------------------------------------------------------------------------
// Helpers for endpoint tests
// -------------------------------------------------------------------------

// testPasetoKey is a base64-encoded 32-byte key for use in tests only.
const testPasetoKey = "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=" // 32 'A' bytes, base64

func newTestRouter(t *testing.T, pool *pgxpool.Pool, storage vault.ObjectStorage) (http.Handler, *auth.PasetoMaker) {
	t.Helper()
	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))

	maker, err := auth.NewPasetoMaker(testPasetoKey)
	if err != nil {
		t.Fatalf("new paseto maker: %v", err)
	}

	store := db.New(pool)
	authMW := appmiddleware.AuthMiddleware(maker, store)

	svc := vault.NewService(pool, storage, logger)
	h := vault.NewHandler(svc)

	r := chi.NewRouter()
	r.With(authMW).Route("/v1", func(r chi.Router) {
		h.RegisterRoutes(r)
	})
	return r, maker
}

// seedAccount inserts a test account and returns its ID.
func seedAccount(t *testing.T, pool *pgxpool.Pool, email string) int64 {
	t.Helper()
	var id int64
	err := pool.QueryRow(context.Background(), `
		INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params, email_verified)
		VALUES ($1, 'hash', '{}', '\x00', '\x00', '{}', TRUE)
		RETURNING id
	`, email).Scan(&id)
	if err != nil {
		t.Fatalf("seed account: %v", err)
	}
	return id
}

// bearerToken returns an Authorization header value with a fresh access token.
func bearerToken(t *testing.T, maker *auth.PasetoMaker, accountID int64) string {
	t.Helper()
	token, err := maker.MakeAccessToken(auth.Claims{
		AccountID: accountID,
	})
	if err != nil {
		t.Fatalf("make access token: %v", err)
	}
	return "Bearer " + token
}

// validDocBody returns a valid CreateDocument JSON body with the given UUID.
func validDocBody(uuid string) []byte {
	meta := base64.StdEncoding.EncodeToString([]byte("encrypted-title"))
	nonce := base64.StdEncoding.EncodeToString([]byte("0123456789012345")) // 16 bytes nonce
	b, _ := json.Marshal(map[string]any{
		"uuid":               uuid,
		"encrypted_metadata": meta,
		"metadata_nonce":     nonce,
		"page_count":         0,
	})
	return b
}

// -------------------------------------------------------------------------
// 2A.3 — Create document tests
// -------------------------------------------------------------------------

func TestCreateDocument_HappyPath(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "create@example.com")

	body := validDocBody("018f1234-5678-7abc-8def-000000000001")
	req := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", bearerToken(t, maker, accountID))

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusCreated {
		t.Fatalf("want 201, got %d: %s", w.Code, w.Body.String())
	}

	var resp vault.DocumentResponse
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.UUID != "018f1234-5678-7abc-8def-000000000001" {
		t.Errorf("UUID = %q, want 018f1234-5678-7abc-8def-000000000001", resp.UUID)
	}
	if resp.Version != 1 {
		t.Errorf("Version = %d, want 1", resp.Version)
	}
}

func TestCreateDocument_NoAuth(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, _ := newTestRouter(t, pool, newMockStorage())

	body := validDocBody("018f1234-5678-7abc-8def-000000000002")
	req := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("want 401, got %d", w.Code)
	}
}

func TestCreateDocument_MissingFields(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "missing@example.com")

	// Missing uuid
	body, _ := json.Marshal(map[string]any{
		"encrypted_metadata": base64.StdEncoding.EncodeToString([]byte("meta")),
		"metadata_nonce":     base64.StdEncoding.EncodeToString([]byte("nonce-nonce-nnnn")),
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", bearerToken(t, maker, accountID))

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("want 400, got %d: %s", w.Code, w.Body.String())
	}
}

func TestCreateDocument_InvalidUUIDVersion(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "invalid_uuid@example.com")

	// UUIDv4 (version nibble = 4, not 7)
	body := validDocBody("550e8400-e29b-41d4-a716-446655440000")
	req := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", bearerToken(t, maker, accountID))

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("want 400 for non-v7 UUID, got %d: %s", w.Code, w.Body.String())
	}
}

func TestCreateDocument_DuplicateUUID(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "dup@example.com")

	docUUID := "018f1234-5678-7abc-8def-000000000010"
	body := validDocBody(docUUID)

	doCreate := func() *httptest.ResponseRecorder {
		req := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", bearerToken(t, maker, accountID))
		w := httptest.NewRecorder()
		router.ServeHTTP(w, req)
		return w
	}

	w1 := doCreate()
	if w1.Code != http.StatusCreated {
		t.Fatalf("first create: want 201, got %d: %s", w1.Code, w1.Body.String())
	}
	w2 := doCreate()
	if w2.Code != http.StatusConflict {
		t.Errorf("duplicate create: want 409, got %d: %s", w2.Code, w2.Body.String())
	}
}

// -------------------------------------------------------------------------
// 2A.4 — List documents tests
// -------------------------------------------------------------------------

func TestListDocuments_Empty(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "list_empty@example.com")

	req := httptest.NewRequest(http.MethodGet, "/v1/vault/documents", nil)
	req.Header.Set("Authorization", bearerToken(t, maker, accountID))

	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("want 200, got %d: %s", w.Code, w.Body.String())
	}

	var resp vault.ListDocumentsResponse
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if len(resp.Documents) != 0 {
		t.Errorf("want 0 documents, got %d", len(resp.Documents))
	}
}

func TestListDocuments_PaginationCursor(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "list_cursor@example.com")

	// Create 3 documents with sequential UUIDv7s
	uuids := []string{
		"018f1234-5678-7abc-8def-000000000020",
		"018f1234-5678-7abc-8def-000000000021",
		"018f1234-5678-7abc-8def-000000000022",
	}
	for _, u := range uuids {
		body := validDocBody(u)
		req := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", bearerToken(t, maker, accountID))
		w := httptest.NewRecorder()
		router.ServeHTTP(w, req)
		if w.Code != http.StatusCreated {
			t.Fatalf("create doc %s: %d: %s", u, w.Code, w.Body.String())
		}
	}

	// List with limit=2 — should get first 2 + next_cursor
	req := httptest.NewRequest(http.MethodGet, "/v1/vault/documents?limit=2", nil)
	req.Header.Set("Authorization", bearerToken(t, maker, accountID))
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("want 200, got %d: %s", w.Code, w.Body.String())
	}

	var resp vault.ListDocumentsResponse
	_ = json.NewDecoder(w.Body).Decode(&resp)
	if len(resp.Documents) != 2 {
		t.Errorf("want 2 documents, got %d", len(resp.Documents))
	}
	if resp.NextCursor == "" {
		t.Error("expected next_cursor for pagination")
	}

	// Second page using cursor
	req2 := httptest.NewRequest(http.MethodGet, "/v1/vault/documents?limit=2&after="+resp.NextCursor, nil)
	req2.Header.Set("Authorization", bearerToken(t, maker, accountID))
	w2 := httptest.NewRecorder()
	router.ServeHTTP(w2, req2)

	var resp2 vault.ListDocumentsResponse
	_ = json.NewDecoder(w2.Body).Decode(&resp2)
	if len(resp2.Documents) != 1 {
		t.Errorf("want 1 document on second page, got %d", len(resp2.Documents))
	}
	if resp2.NextCursor != "" {
		t.Error("no next_cursor expected on last page")
	}
}

func TestListDocuments_ExcludesSoftDeleted(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	// Directly insert a soft-deleted document
	ctx := context.Background()
	var accountID int64
	_ = pool.QueryRow(ctx, `
		INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params, email_verified)
		VALUES ('softdel@example.com', 'hash', '{}', '\x00', '\x00', '{}', TRUE)
		RETURNING id
	`).Scan(&accountID)

	_, _ = pool.Exec(ctx, `
		INSERT INTO vault_documents (uuid, account_id, encrypted_metadata, metadata_nonce, deleted_at)
		VALUES ('018f1234-5678-7abc-8def-000000000030'::uuid, $1, '\xdeadbeef', '\xdeadbeef', NOW())
	`, accountID)

	router, maker := newTestRouter(t, pool, newMockStorage())
	req := httptest.NewRequest(http.MethodGet, "/v1/vault/documents", nil)
	req.Header.Set("Authorization", bearerToken(t, maker, accountID))
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	var resp vault.ListDocumentsResponse
	_ = json.NewDecoder(w.Body).Decode(&resp)
	if len(resp.Documents) != 0 {
		t.Errorf("soft-deleted document should not appear in list, got %d", len(resp.Documents))
	}
}

func TestListDocuments_NoAuth(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, _ := newTestRouter(t, pool, newMockStorage())
	req := httptest.NewRequest(http.MethodGet, "/v1/vault/documents", nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("want 401, got %d", w.Code)
	}
}

// -------------------------------------------------------------------------
// 2A.4 — Get document by UUID tests
// -------------------------------------------------------------------------

func TestGetDocument_HappyPath(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "getdoc@example.com")

	docUUID := "018f1234-5678-7abc-8def-000000000040"
	body := validDocBody(docUUID)

	// Create it
	createReq := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
	createReq.Header.Set("Content-Type", "application/json")
	createReq.Header.Set("Authorization", bearerToken(t, maker, accountID))
	w := httptest.NewRecorder()
	router.ServeHTTP(w, createReq)
	if w.Code != http.StatusCreated {
		t.Fatalf("create: %d %s", w.Code, w.Body.String())
	}

	// Fetch it
	getReq := httptest.NewRequest(http.MethodGet, "/v1/vault/documents/"+docUUID, nil)
	getReq.Header.Set("Authorization", bearerToken(t, maker, accountID))
	w2 := httptest.NewRecorder()
	router.ServeHTTP(w2, getReq)

	if w2.Code != http.StatusOK {
		t.Fatalf("get: want 200, got %d: %s", w2.Code, w2.Body.String())
	}
	var resp struct {
		Document vault.DocumentResponse `json:"document"`
		Pages    []any                  `json:"pages"`
	}
	_ = json.NewDecoder(w2.Body).Decode(&resp)
	if resp.Document.UUID != docUUID {
		t.Errorf("UUID = %q, want %q", resp.Document.UUID, docUUID)
	}
}

func TestGetDocument_OtherAccountReturns404(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	ownerID := seedAccount(t, pool, "owner@example.com")
	attackerID := seedAccount(t, pool, "attacker@example.com")

	docUUID := "018f1234-5678-7abc-8def-000000000050"
	body := validDocBody(docUUID)

	// Create as owner
	createReq := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
	createReq.Header.Set("Content-Type", "application/json")
	createReq.Header.Set("Authorization", bearerToken(t, maker, ownerID))
	w := httptest.NewRecorder()
	router.ServeHTTP(w, createReq)
	if w.Code != http.StatusCreated {
		t.Fatalf("create: %d %s", w.Code, w.Body.String())
	}

	// Try to get as attacker — should get 404, not 403
	getReq := httptest.NewRequest(http.MethodGet, "/v1/vault/documents/"+docUUID, nil)
	getReq.Header.Set("Authorization", bearerToken(t, maker, attackerID))
	w2 := httptest.NewRecorder()
	router.ServeHTTP(w2, getReq)

	if w2.Code != http.StatusNotFound {
		t.Errorf("want 404 for other user's document, got %d", w2.Code)
	}
}

func TestGetDocument_NonExistent(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "getnonexist@example.com")

	getReq := httptest.NewRequest(http.MethodGet, "/v1/vault/documents/018f1234-5678-7abc-8def-000000000099", nil)
	getReq.Header.Set("Authorization", bearerToken(t, maker, accountID))
	w := httptest.NewRecorder()
	router.ServeHTTP(w, getReq)

	if w.Code != http.StatusNotFound {
		t.Errorf("want 404, got %d", w.Code)
	}
}

func TestGetDocument_NoAuth(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, _ := newTestRouter(t, pool, newMockStorage())
	req := httptest.NewRequest(http.MethodGet, "/v1/vault/documents/018f1234-5678-7abc-8def-000000000001", nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("want 401, got %d", w.Code)
	}
}

func init() {
	if os.Getenv("TESTCONTAINERS_LOG") == "" {
		os.Setenv("TESTCONTAINERS_RYUK_DISABLED", "false")
	}
}
