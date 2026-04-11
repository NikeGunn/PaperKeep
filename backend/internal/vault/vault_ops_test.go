// vault_ops_test.go — integration tests for 2A.5 through 2A.12
// Shares helpers (startPostgres, runMigrations, newTestRouter, seedAccount,
// bearerToken, validDocBody) from vault_test.go (same package).
package vault_test

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/nikhil/scanvault-api/internal/vault"
)

// -------------------------------------------------------------------------
// Helpers shared across 2A.5–2A.12
// -------------------------------------------------------------------------

// createDoc creates a document via the API and returns its UUID.
func createDoc(t *testing.T, router http.Handler, token, uuid string) {
	t.Helper()
	body := validDocBody(uuid)
	req := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	if w.Code != http.StatusCreated {
		t.Fatalf("createDoc %s: want 201, got %d: %s", uuid, w.Code, w.Body.String())
	}
}

// getDocVersion fetches the current document version via the API.
func getDocVersion(t *testing.T, router http.Handler, token, uuid string) int64 {
	t.Helper()
	req := httptest.NewRequest(http.MethodGet, "/v1/vault/documents/"+uuid, nil)
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("getDoc %s: want 200, got %d", uuid, w.Code)
	}
	var resp struct {
		Document vault.DocumentResponse `json:"document"`
	}
	_ = json.NewDecoder(w.Body).Decode(&resp)
	return resp.Document.Version
}

// updateDoc sends PUT /v1/vault/documents/{uuid} with the given expectedVersion.
func updateDoc(t *testing.T, router http.Handler, token, uuid string, expectedVersion int64) *httptest.ResponseRecorder {
	t.Helper()
	meta := base64.StdEncoding.EncodeToString([]byte("updated-encrypted-title"))
	nonce := base64.StdEncoding.EncodeToString([]byte("0123456789012345"))
	body, _ := json.Marshal(map[string]any{
		"encrypted_metadata": meta,
		"metadata_nonce":     nonce,
		"expected_version":   expectedVersion,
	})
	req := httptest.NewRequest(http.MethodPut, "/v1/vault/documents/"+uuid, bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	return w
}

// deleteDoc sends DELETE /v1/vault/documents/{uuid}.
func deleteDoc(t *testing.T, router http.Handler, token, uuid string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodDelete, "/v1/vault/documents/"+uuid, nil)
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	return w
}

// pageChecksum returns a hex SHA-256 checksum of dummy ciphertext.
func pageChecksum() string {
	h := sha256.Sum256([]byte("fake-ciphertext"))
	return fmt.Sprintf("%x", h[:])
}

// requestUploadURL calls POST /v1/vault/documents/{docUUID}/pages/upload-url.
func requestUploadURL(t *testing.T, router http.Handler, token, docUUID, pageUUID string, encryptedSize int64) *httptest.ResponseRecorder {
	t.Helper()
	body, _ := json.Marshal(map[string]any{
		"page_uuid":      pageUUID,
		"page_index":     0,
		"encrypted_size": encryptedSize,
		"checksum":       pageChecksum(),
	})
	req := httptest.NewRequest(http.MethodPost,
		"/v1/vault/documents/"+docUUID+"/pages/upload-url",
		bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	return w
}

// confirmUpload calls POST /v1/vault/documents/{docUUID}/pages/{pageUUID}/confirm.
func confirmUpload(t *testing.T, router http.Handler, token, docUUID, pageUUID string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodPost,
		"/v1/vault/documents/"+docUUID+"/pages/"+pageUUID+"/confirm",
		nil)
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	return w
}

// downloadURL calls GET /v1/vault/documents/{docUUID}/pages/{pageUUID}/download-url.
func downloadURL(t *testing.T, router http.Handler, token, docUUID, pageUUID string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodGet,
		"/v1/vault/documents/"+docUUID+"/pages/"+pageUUID+"/download-url",
		nil)
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	return w
}

// -------------------------------------------------------------------------
// 2A.5 — Optimistic concurrency tests
// -------------------------------------------------------------------------

func TestUpdateDocument_CorrectVersion(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "upd_ok@example.com")
	token := bearerToken(t, maker, accountID)

	docUUID := "018f1234-5678-7abc-8def-000000000060"
	createDoc(t, router, token, docUUID)

	version := getDocVersion(t, router, token, docUUID)
	w := updateDoc(t, router, token, docUUID, version)
	if w.Code != http.StatusOK {
		t.Errorf("correct version update: want 200, got %d: %s", w.Code, w.Body.String())
	}

	newVersion := getDocVersion(t, router, token, docUUID)
	if newVersion != version+1 {
		t.Errorf("version should increment: want %d, got %d", version+1, newVersion)
	}
}

func TestUpdateDocument_WrongVersion_Conflict(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "upd_conflict@example.com")
	token := bearerToken(t, maker, accountID)

	docUUID := "018f1234-5678-7abc-8def-000000000061"
	createDoc(t, router, token, docUUID)

	// Supply wrong version (0 instead of 1)
	w := updateDoc(t, router, token, docUUID, 0)
	if w.Code != http.StatusConflict {
		t.Errorf("wrong version: want 409, got %d: %s", w.Code, w.Body.String())
	}

	// Response body should contain current_version
	var body map[string]any
	_ = json.NewDecoder(w.Body).Decode(&body)
	if body["current_version"] == nil {
		t.Error("conflict response should contain current_version")
	}
}

func TestUpdateDocument_ConcurrentUpdates_OnlyOneSucceeds(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "concurrent@example.com")
	token := bearerToken(t, maker, accountID)

	docUUID := "018f1234-5678-7abc-8def-000000000062"
	createDoc(t, router, token, docUUID)

	version := getDocVersion(t, router, token, docUUID)

	const numGoroutines = 5
	results := make([]int, numGoroutines)
	var wg sync.WaitGroup
	for i := 0; i < numGoroutines; i++ {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			w := updateDoc(t, router, token, docUUID, version)
			results[idx] = w.Code
		}(i)
	}
	wg.Wait()

	successes := 0
	conflicts := 0
	for _, code := range results {
		switch code {
		case http.StatusOK:
			successes++
		case http.StatusConflict:
			conflicts++
		}
	}
	if successes != 1 {
		t.Errorf("concurrent updates: want exactly 1 success, got %d (conflicts=%d, all=%v)", successes, conflicts, results)
	}
	if conflicts != numGoroutines-1 {
		t.Errorf("concurrent updates: want %d conflicts, got %d", numGoroutines-1, conflicts)
	}
}

func TestUpdateDocument_OtherAccount_NotFound(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	ownerID := seedAccount(t, pool, "upd_owner@example.com")
	attackerID := seedAccount(t, pool, "upd_attacker@example.com")

	docUUID := "018f1234-5678-7abc-8def-000000000063"
	createDoc(t, router, bearerToken(t, maker, ownerID), docUUID)

	w := updateDoc(t, router, bearerToken(t, maker, attackerID), docUUID, 1)
	if w.Code != http.StatusNotFound {
		t.Errorf("other account update: want 404, got %d", w.Code)
	}
}

// -------------------------------------------------------------------------
// 2A.6 — Soft delete tests
// -------------------------------------------------------------------------

func TestDeleteDocument_SoftDelete_GoneFromList(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "del_soft@example.com")
	token := bearerToken(t, maker, accountID)

	docUUID := "018f1234-5678-7abc-8def-000000000070"
	createDoc(t, router, token, docUUID)

	// Delete
	w := deleteDoc(t, router, token, docUUID)
	if w.Code != http.StatusNoContent {
		t.Fatalf("delete: want 204, got %d: %s", w.Code, w.Body.String())
	}

	// Should not appear in list
	req := httptest.NewRequest(http.MethodGet, "/v1/vault/documents", nil)
	req.Header.Set("Authorization", token)
	wr := httptest.NewRecorder()
	router.ServeHTTP(wr, req)
	var listResp vault.ListDocumentsResponse
	_ = json.NewDecoder(wr.Body).Decode(&listResp)
	for _, d := range listResp.Documents {
		if d.UUID == docUUID {
			t.Error("soft-deleted document should not appear in list")
		}
	}

	// Should still exist in DB (check via direct query or GET returning 404)
	var cnt int
	_ = pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM vault_documents WHERE uuid=$1`,
		docUUID).Scan(&cnt)
	if cnt == 0 {
		t.Error("soft-deleted document should still be in DB")
	}
}

func TestDeleteDocument_OtherAccount_NotFound(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	ownerID := seedAccount(t, pool, "del_owner@example.com")
	attackerID := seedAccount(t, pool, "del_attacker@example.com")

	docUUID := "018f1234-5678-7abc-8def-000000000071"
	createDoc(t, router, bearerToken(t, maker, ownerID), docUUID)

	w := deleteDoc(t, router, bearerToken(t, maker, attackerID), docUUID)
	if w.Code != http.StatusNotFound {
		t.Errorf("other account delete: want 404, got %d", w.Code)
	}
}

// -------------------------------------------------------------------------
// 2A.7 — Page upload flow
// -------------------------------------------------------------------------

func TestUploadFlow_PendingToConfirmed(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	ms := newMockStorage()
	router, maker := newTestRouter(t, pool, ms)
	accountID := seedAccount(t, pool, "upload_ok@example.com")
	token := bearerToken(t, maker, accountID)

	docUUID := "018f1234-5678-7abc-8def-000000000080"
	pageUUID := "018f1234-5678-7abc-8def-000000000081"
	createDoc(t, router, token, docUUID)

	// Step 1: request upload URL → should return 201 with upload_url
	w := requestUploadURL(t, router, token, docUUID, pageUUID, 1024)
	if w.Code != http.StatusCreated {
		t.Fatalf("request upload URL: want 201, got %d: %s", w.Code, w.Body.String())
	}
	var urlResp vault.RequestUploadURLResponse
	_ = json.NewDecoder(w.Body).Decode(&urlResp)
	if urlResp.UploadURL == "" {
		t.Fatal("upload_url should not be empty")
	}
	if urlResp.PageUUID != pageUUID {
		t.Errorf("page_uuid = %q, want %q", urlResp.PageUUID, pageUUID)
	}

	// Simulate the client uploading to R2 (put blob in mock storage)
	r2Key := vault.ObjectKey(fmt.Sprintf("%d", accountID), docUUID, pageUUID)
	ms.put(r2Key, 1024)

	// Step 2: confirm → should return 204
	wc := confirmUpload(t, router, token, docUUID, pageUUID)
	if wc.Code != http.StatusNoContent {
		t.Fatalf("confirm upload: want 204, got %d: %s", wc.Code, wc.Body.String())
	}

	// Verify page is now confirmed in DB
	var status string
	_ = pool.QueryRow(context.Background(),
		`SELECT status FROM vault_pages WHERE uuid=$1`, pageUUID).Scan(&status)
	if status != "confirmed" {
		t.Errorf("page status = %q, want 'confirmed'", status)
	}
}

func TestUploadFlow_ExceedsMaxSize(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "upload_big@example.com")
	token := bearerToken(t, maker, accountID)

	docUUID := "018f1234-5678-7abc-8def-000000000082"
	pageUUID := "018f1234-5678-7abc-8def-000000000083"
	createDoc(t, router, token, docUUID)

	const tooBig = int64(51 * 1024 * 1024) // 51 MB
	w := requestUploadURL(t, router, token, docUUID, pageUUID, tooBig)
	if w.Code != http.StatusRequestEntityTooLarge {
		t.Errorf("oversized page: want 413, got %d: %s", w.Code, w.Body.String())
	}
}

func TestUploadFlow_OverStorageQuota(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "upload_quota@example.com")
	token := bearerToken(t, maker, accountID)

	// Set storage quota to 1 byte so the next upload exceeds it
	_, _ = pool.Exec(context.Background(), `
		INSERT INTO account_quotas (account_id, storage_bytes_limit, storage_bytes_used, documents_count, documents_limit)
		VALUES ($1, 1, 0, 0, 1000)
		ON CONFLICT (account_id) DO UPDATE SET storage_bytes_limit = 1
	`, accountID)

	docUUID := "018f1234-5678-7abc-8def-000000000084"
	pageUUID := "018f1234-5678-7abc-8def-000000000085"
	createDoc(t, router, token, docUUID)

	w := requestUploadURL(t, router, token, docUUID, pageUUID, 1024)
	if w.Code != http.StatusPaymentRequired {
		t.Errorf("over quota: want 402, got %d: %s", w.Code, w.Body.String())
	}
}

// -------------------------------------------------------------------------
// 2A.8 — Download URL
// -------------------------------------------------------------------------

func TestDownloadURL_HappyPath(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	ms := newMockStorage()
	router, maker := newTestRouter(t, pool, ms)
	accountID := seedAccount(t, pool, "dl_ok@example.com")
	token := bearerToken(t, maker, accountID)

	docUUID := "018f1234-5678-7abc-8def-000000000090"
	pageUUID := "018f1234-5678-7abc-8def-000000000091"
	createDoc(t, router, token, docUUID)

	requestUploadURL(t, router, token, docUUID, pageUUID, 512)
	r2Key := vault.ObjectKey(fmt.Sprintf("%d", accountID), docUUID, pageUUID)
	ms.put(r2Key, 512)
	confirmUpload(t, router, token, docUUID, pageUUID)

	w := downloadURL(t, router, token, docUUID, pageUUID)
	if w.Code != http.StatusOK {
		t.Fatalf("download URL: want 200, got %d: %s", w.Code, w.Body.String())
	}
	var resp vault.DownloadURLResponse
	_ = json.NewDecoder(w.Body).Decode(&resp)
	if resp.DownloadURL == "" {
		t.Error("download_url should not be empty")
	}
	if !strings.Contains(resp.DownloadURL, pageUUID) {
		t.Errorf("download_url should contain page UUID, got %q", resp.DownloadURL)
	}
}

func TestDownloadURL_OtherAccount_NotFound(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	ms := newMockStorage()
	router, maker := newTestRouter(t, pool, ms)
	ownerID := seedAccount(t, pool, "dl_owner@example.com")
	attackerID := seedAccount(t, pool, "dl_attacker@example.com")

	docUUID := "018f1234-5678-7abc-8def-000000000092"
	pageUUID := "018f1234-5678-7abc-8def-000000000093"
	ownerToken := bearerToken(t, maker, ownerID)

	createDoc(t, router, ownerToken, docUUID)
	requestUploadURL(t, router, ownerToken, docUUID, pageUUID, 256)
	r2Key := vault.ObjectKey(fmt.Sprintf("%d", ownerID), docUUID, pageUUID)
	ms.put(r2Key, 256)
	confirmUpload(t, router, ownerToken, docUUID, pageUUID)

	w := downloadURL(t, router, bearerToken(t, maker, attackerID), docUUID, pageUUID)
	if w.Code != http.StatusNotFound {
		t.Errorf("other account download: want 404, got %d", w.Code)
	}
}

// -------------------------------------------------------------------------
// 2A.9 — Background purge
// -------------------------------------------------------------------------

func TestPurge_DeletesExpiredDocuments(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	accountID := seedAccountDirect(t, pool, "purge_expired@example.com")

	// Insert a document that was soft-deleted 31 days ago
	var docID int64
	_ = pool.QueryRow(context.Background(), `
		INSERT INTO vault_documents (uuid, account_id, encrypted_metadata, metadata_nonce, deleted_at)
		VALUES ('018f1234-5678-7abc-8def-0000000000a0'::uuid, $1, '\xdeadbeef', '\xdeadbeef', NOW() - INTERVAL '31 days')
		RETURNING id
	`, accountID).Scan(&docID)

	ms := newMockStorage()
	svc := newTestService(t, pool, ms)
	cutoff := time.Now().Add(-30 * 24 * time.Hour)
	_, docsDeleted, err := svc.PurgeExpiredWithRetention(context.Background(), cutoff)
	if err != nil {
		t.Fatalf("purge: %v", err)
	}
	if docsDeleted != 1 {
		t.Errorf("want 1 purged document, got %d", docsDeleted)
	}

	var count int
	_ = pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM vault_documents WHERE id=$1`, docID).Scan(&count)
	if count != 0 {
		t.Error("expired document should be hard-deleted after purge")
	}
}

func TestPurge_SparesFreshSoftDeleted(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	accountID := seedAccountDirect(t, pool, "purge_fresh@example.com")

	// Soft-deleted only 1 day ago — should NOT be purged
	var docID int64
	_ = pool.QueryRow(context.Background(), `
		INSERT INTO vault_documents (uuid, account_id, encrypted_metadata, metadata_nonce, deleted_at)
		VALUES ('018f1234-5678-7abc-8def-0000000000a1'::uuid, $1, '\xdeadbeef', '\xdeadbeef', NOW() - INTERVAL '1 day')
		RETURNING id
	`, accountID).Scan(&docID)

	ms := newMockStorage()
	svc := newTestService(t, pool, ms)
	cutoff := time.Now().Add(-30 * 24 * time.Hour)
	_, docsDeleted, err := svc.PurgeExpiredWithRetention(context.Background(), cutoff)
	if err != nil {
		t.Fatalf("purge: %v", err)
	}
	if docsDeleted != 0 {
		t.Errorf("fresh soft-deleted doc should not be purged, but %d were deleted", docsDeleted)
	}

	var count int
	_ = pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM vault_documents WHERE id=$1`, docID).Scan(&count)
	if count != 1 {
		t.Error("fresh soft-deleted document should still be in DB")
	}
}

func TestPurge_DeletesExpiredPages(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	accountID := seedAccountDirect(t, pool, "purge_page@example.com")

	var docID int64
	_ = pool.QueryRow(context.Background(), `
		INSERT INTO vault_documents (uuid, account_id, encrypted_metadata, metadata_nonce)
		VALUES ('018f1234-5678-7abc-8def-0000000000b0'::uuid, $1, '\xdeadbeef', '\xdeadbeef')
		RETURNING id
	`, accountID).Scan(&docID)

	const r2Key = "vault/1/doc/page.enc"
	var pageID int64
	_ = pool.QueryRow(context.Background(), `
		INSERT INTO vault_pages (uuid, document_id, page_index, r2_key, encrypted_size, checksum, deleted_at)
		VALUES ('018f1234-5678-7abc-8def-0000000000b1'::uuid, $1, 0, $2, 512, '\xdeadbeef', NOW() - INTERVAL '31 days')
		RETURNING id
	`, docID, r2Key).Scan(&pageID)

	ms := newMockStorage()
	ms.put(r2Key, 512)

	svc := newTestService(t, pool, ms)
	cutoff := time.Now().Add(-30 * 24 * time.Hour)
	pagesDeleted, _, err := svc.PurgeExpiredWithRetention(context.Background(), cutoff)
	if err != nil {
		t.Fatalf("purge: %v", err)
	}
	if pagesDeleted != 1 {
		t.Errorf("want 1 purged page, got %d", pagesDeleted)
	}

	// R2 object should be deleted
	if _, err := ms.HeadObject(context.Background(), r2Key); err == nil {
		t.Error("R2 object should be deleted after purge")
	}
}

// -------------------------------------------------------------------------
// 2A.10 — Quota enforcement
// -------------------------------------------------------------------------

func TestQuota_DocumentLimitEnforced(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "quota_docs@example.com")
	token := bearerToken(t, maker, accountID)

	// Set documents_limit to 1
	_, _ = pool.Exec(context.Background(), `
		INSERT INTO account_quotas (account_id, documents_limit, documents_count, storage_bytes_limit, storage_bytes_used)
		VALUES ($1, 1, 1, 524288000, 0)
		ON CONFLICT (account_id) DO UPDATE SET documents_limit=1, documents_count=1
	`, accountID)

	body := validDocBody("018f1234-5678-7abc-8def-0000000000c0")
	req := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusPaymentRequired {
		t.Errorf("quota exceeded: want 402, got %d: %s", w.Code, w.Body.String())
	}
}

func TestQuota_ResetsAfterDeletion(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "quota_reset@example.com")
	token := bearerToken(t, maker, accountID)

	docUUID := "018f1234-5678-7abc-8def-0000000000c1"
	createDoc(t, router, token, docUUID)

	// Now cap the limit to current count to simulate "full"
	_, _ = pool.Exec(context.Background(), `
		UPDATE account_quotas SET documents_limit = documents_count WHERE account_id = $1
	`, accountID)

	// Delete the document to free one slot
	w := deleteDoc(t, router, token, docUUID)
	if w.Code != http.StatusNoContent {
		t.Fatalf("delete: want 204, got %d", w.Code)
	}

	// Now creating another should succeed
	body := validDocBody("018f1234-5678-7abc-8def-0000000000c2")
	req := httptest.NewRequest(http.MethodPost, "/v1/vault/documents", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", token)
	wr := httptest.NewRecorder()
	router.ServeHTTP(wr, req)
	if wr.Code != http.StatusCreated {
		t.Errorf("after deletion, new doc should succeed: want 201, got %d: %s", wr.Code, wr.Body.String())
	}
}

// -------------------------------------------------------------------------
// 2A.11 — Sync manifest
// -------------------------------------------------------------------------

func TestManifest_ReturnsSinceTimestamp(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "manifest@example.com")
	token := bearerToken(t, maker, accountID)

	// Record time before creating docs
	before := time.Now().UTC().Add(-time.Second)

	createDoc(t, router, token, "018f1234-5678-7abc-8def-0000000000d0")
	createDoc(t, router, token, "018f1234-5678-7abc-8def-0000000000d1")

	since := before.Format(time.RFC3339)
	req := httptest.NewRequest(http.MethodGet, "/v1/vault/manifest?since="+since, nil)
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("manifest: want 200, got %d: %s", w.Code, w.Body.String())
	}

	var resp vault.ManifestResponse
	_ = json.NewDecoder(w.Body).Decode(&resp)
	if len(resp.Entries) < 2 {
		t.Errorf("manifest: want ≥2 entries, got %d", len(resp.Entries))
	}
}

func TestManifest_ExcludesOlderDocs(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "manifest_old@example.com")
	token := bearerToken(t, maker, accountID)

	createDoc(t, router, token, "018f1234-5678-7abc-8def-0000000000d2")

	// Since = future → no results
	future := time.Now().UTC().Add(time.Hour).Format(time.RFC3339)
	req := httptest.NewRequest(http.MethodGet, "/v1/vault/manifest?since="+future, nil)
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	var resp vault.ManifestResponse
	_ = json.NewDecoder(w.Body).Decode(&resp)
	if len(resp.Entries) != 0 {
		t.Errorf("manifest with future since: want 0 entries, got %d", len(resp.Entries))
	}
}

func TestManifest_CursorPagination(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	router, maker := newTestRouter(t, pool, newMockStorage())
	accountID := seedAccount(t, pool, "manifest_cursor@example.com")
	token := bearerToken(t, maker, accountID)

	before := time.Now().UTC().Add(-time.Second)
	uuids := []string{
		"018f1234-5678-7abc-8def-0000000000e0",
		"018f1234-5678-7abc-8def-0000000000e1",
		"018f1234-5678-7abc-8def-0000000000e2",
	}
	for _, u := range uuids {
		createDoc(t, router, token, u)
	}

	since := before.Format(time.RFC3339)
	req := httptest.NewRequest(http.MethodGet, "/v1/vault/manifest?since="+since+"&limit=2", nil)
	req.Header.Set("Authorization", token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	var resp vault.ManifestResponse
	_ = json.NewDecoder(w.Body).Decode(&resp)
	if len(resp.Entries) != 2 {
		t.Errorf("manifest page 1: want 2 entries, got %d", len(resp.Entries))
	}
	if resp.NextCursor == "" {
		t.Error("manifest page 1: expected next_cursor")
	}

	// Page 2
	req2 := httptest.NewRequest(http.MethodGet,
		"/v1/vault/manifest?since="+since+"&limit=2&after="+resp.NextCursor, nil)
	req2.Header.Set("Authorization", token)
	w2 := httptest.NewRecorder()
	router.ServeHTTP(w2, req2)

	var resp2 vault.ManifestResponse
	_ = json.NewDecoder(w2.Body).Decode(&resp2)
	if len(resp2.Entries) != 1 {
		t.Errorf("manifest page 2: want 1 entry, got %d", len(resp2.Entries))
	}
}

// -------------------------------------------------------------------------
// 2A.12 — Full E2E integration test
// -------------------------------------------------------------------------

func TestE2E_CreateUploadConfirmListDownloadDeletePurge(t *testing.T) {
	if testing.Short() {
		t.Skip("integration test")
	}
	dsn, cleanup := startPostgres(t)
	defer cleanup()
	runMigrations(t, dsn, "up")
	pool := openPool(t, dsn)
	defer pool.Close()

	ms := newMockStorage()
	router, maker := newTestRouter(t, pool, ms)
	accountID := seedAccount(t, pool, "e2e@example.com")
	token := bearerToken(t, maker, accountID)

	const (
		docUUID  = "018f1234-5678-7abc-8def-0000000000f0"
		pageUUID = "018f1234-5678-7abc-8def-0000000000f1"
	)

	// 1. Create document
	createDoc(t, router, token, docUUID)
	t.Log("✓ create document")

	// 2. Request upload URL
	wu := requestUploadURL(t, router, token, docUUID, pageUUID, 2048)
	if wu.Code != http.StatusCreated {
		t.Fatalf("request upload URL: %d %s", wu.Code, wu.Body.String())
	}
	t.Log("✓ request upload URL")

	// 3. Simulate upload to R2
	r2Key := vault.ObjectKey(fmt.Sprintf("%d", accountID), docUUID, pageUUID)
	ms.put(r2Key, 2048)
	t.Log("✓ simulate R2 upload")

	// 4. Confirm upload
	wc := confirmUpload(t, router, token, docUUID, pageUUID)
	if wc.Code != http.StatusNoContent {
		t.Fatalf("confirm upload: %d %s", wc.Code, wc.Body.String())
	}
	t.Log("✓ confirm upload")

	// 5. List documents — should include our doc
	req := httptest.NewRequest(http.MethodGet, "/v1/vault/documents", nil)
	req.Header.Set("Authorization", token)
	wl := httptest.NewRecorder()
	router.ServeHTTP(wl, req)
	var listResp vault.ListDocumentsResponse
	_ = json.NewDecoder(wl.Body).Decode(&listResp)
	found := false
	for _, d := range listResp.Documents {
		if d.UUID == docUUID {
			found = true
			break
		}
	}
	if !found {
		t.Fatal("document not found in list after creation")
	}
	t.Log("✓ list documents")

	// 6. Download URL
	wd := downloadURL(t, router, token, docUUID, pageUUID)
	if wd.Code != http.StatusOK {
		t.Fatalf("download URL: %d %s", wd.Code, wd.Body.String())
	}
	var dlResp vault.DownloadURLResponse
	_ = json.NewDecoder(wd.Body).Decode(&dlResp)
	if dlResp.DownloadURL == "" {
		t.Fatal("download_url is empty")
	}
	t.Log("✓ download URL")

	// 7. Delete document
	wdel := deleteDoc(t, router, token, docUUID)
	if wdel.Code != http.StatusNoContent {
		t.Fatalf("delete: %d %s", wdel.Code, wdel.Body.String())
	}
	t.Log("✓ soft delete")

	// 8. Purge — set cutoff to now so the just-deleted doc is expired
	// (we back-date the deleted_at in DB directly to simulate >30 days)
	_, _ = pool.Exec(context.Background(), `
		UPDATE vault_documents SET deleted_at = NOW() - INTERVAL '31 days'
		WHERE uuid = $1
	`, docUUID)
	_, _ = pool.Exec(context.Background(), `
		UPDATE vault_pages SET deleted_at = NOW() - INTERVAL '31 days'
		WHERE uuid = $1
	`, pageUUID)

	svc := newTestService(t, pool, ms)
	cutoff := time.Now().Add(-30 * 24 * time.Hour)
	pagesDeleted, docsDeleted, err := svc.PurgeExpiredWithRetention(context.Background(), cutoff)
	if err != nil {
		t.Fatalf("purge: %v", err)
	}
	if pagesDeleted == 0 {
		t.Error("purge should have deleted the expired page")
	}
	if docsDeleted == 0 {
		t.Error("purge should have deleted the expired document")
	}
	t.Logf("✓ purge: %d pages, %d docs deleted", pagesDeleted, docsDeleted)

	// Verify R2 object is gone
	if _, err := ms.HeadObject(context.Background(), r2Key); err == nil {
		t.Error("R2 object should have been deleted by purge")
	}
	t.Log("✓ R2 object purged")
}

// -------------------------------------------------------------------------
// Test-only helpers
// -------------------------------------------------------------------------

// seedAccountDirect inserts an account directly and returns its ID.
// Used in purge/service tests that don't need an HTTP token.
func seedAccountDirect(t *testing.T, pool *pgxpool.Pool, email string) int64 {
	t.Helper()
	return seedAccount(t, pool, email)
}

// newTestService builds a vault.Service pointing at the given pool and storage.
func newTestService(t *testing.T, pool *pgxpool.Pool, ms vault.ObjectStorage) *vault.Service {
	t.Helper()
	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
	return vault.NewService(pool, ms, logger)
}
