package vault_test

import (
	"context"
	"fmt"
	"net/url"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/nikhil/scanvault-api/internal/vault"
)

// -------------------------------------------------------------------------
// Mock implementation
// -------------------------------------------------------------------------

// mockStorage is an in-memory ObjectStorage implementation for tests.
// It records calls so tests can assert on them.
type mockStorage struct {
	mu      sync.Mutex
	objects map[string]int64 // key → size
	deleted []string
}

func newMockStorage() *mockStorage {
	return &mockStorage{objects: make(map[string]int64)}
}

func (m *mockStorage) GeneratePresignedPutURL(_ context.Context, key string, maxBytes int64) (string, error) {
	expires := int64(5 * time.Minute / time.Second) // 300
	return fmt.Sprintf("https://r2.example.com/%s?X-Amz-Expires=%d&X-Amz-Signature=fakesig&maxBytes=%d",
		key, expires, maxBytes), nil
}

func (m *mockStorage) GeneratePresignedGetURL(_ context.Context, key string) (string, error) {
	expires := int64(5 * time.Minute / time.Second) // 300
	return fmt.Sprintf("https://r2.example.com/%s?X-Amz-Expires=%d&X-Amz-Signature=fakesig",
		key, expires), nil
}

func (m *mockStorage) DeleteObject(_ context.Context, key string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.objects, key)
	m.deleted = append(m.deleted, key)
	return nil
}

func (m *mockStorage) HeadObject(_ context.Context, key string) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	size, ok := m.objects[key]
	if !ok {
		return 0, fmt.Errorf("object not found: %s", key)
	}
	return size, nil
}

func (m *mockStorage) put(key string, size int64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.objects[key] = size
}

// -------------------------------------------------------------------------
// ObjectKey tests
// -------------------------------------------------------------------------

func TestObjectKey_Format(t *testing.T) {
	key := vault.ObjectKey("acct-123", "doc-456", "page-789")
	expected := "vault/acct-123/doc-456/page-789.enc"
	if key != expected {
		t.Errorf("ObjectKey = %q, want %q", key, expected)
	}
}

func TestObjectKey_ContainsAllSegments(t *testing.T) {
	const (
		accountUUID = "00000000-0000-0000-0000-000000000001"
		docUUID     = "00000000-0000-0000-0000-000000000002"
		pageUUID    = "00000000-0000-0000-0000-000000000003"
	)
	key := vault.ObjectKey(accountUUID, docUUID, pageUUID)

	if !strings.HasPrefix(key, "vault/") {
		t.Errorf("key should start with 'vault/', got %q", key)
	}
	if !strings.Contains(key, accountUUID) {
		t.Errorf("key should contain accountUUID, got %q", key)
	}
	if !strings.Contains(key, docUUID) {
		t.Errorf("key should contain docUUID, got %q", key)
	}
	if !strings.Contains(key, pageUUID) {
		t.Errorf("key should contain pageUUID, got %q", key)
	}
	if !strings.HasSuffix(key, ".enc") {
		t.Errorf("key should end with '.enc', got %q", key)
	}
}

// -------------------------------------------------------------------------
// Mock presigned URL tests
// -------------------------------------------------------------------------

func TestMockStorage_PutURL_ContainsKey(t *testing.T) {
	m := newMockStorage()
	key := vault.ObjectKey("a", "b", "c")
	u, err := m.GeneratePresignedPutURL(context.Background(), key, 1024)
	if err != nil {
		t.Fatalf("GeneratePresignedPutURL: %v", err)
	}
	if !strings.Contains(u, key) {
		t.Errorf("put URL should contain the key %q, got %q", key, u)
	}
}

func TestMockStorage_GetURL_ContainsKey(t *testing.T) {
	m := newMockStorage()
	key := vault.ObjectKey("a", "b", "c")
	u, err := m.GeneratePresignedGetURL(context.Background(), key)
	if err != nil {
		t.Fatalf("GeneratePresignedGetURL: %v", err)
	}
	if !strings.Contains(u, key) {
		t.Errorf("get URL should contain the key %q, got %q", key, u)
	}
}

func TestMockStorage_PutURL_HasExpiry(t *testing.T) {
	m := newMockStorage()
	u, err := m.GeneratePresignedPutURL(context.Background(), "some/key", 512)
	if err != nil {
		t.Fatalf("GeneratePresignedPutURL: %v", err)
	}
	d, err := vault.ParsePresignedURLExpiry(u)
	if err != nil {
		t.Fatalf("ParsePresignedURLExpiry: %v", err)
	}
	const want = 5 * time.Minute
	if d != want {
		t.Errorf("expiry = %v, want %v", d, want)
	}
}

func TestMockStorage_GetURL_HasExpiry(t *testing.T) {
	m := newMockStorage()
	u, err := m.GeneratePresignedGetURL(context.Background(), "some/key")
	if err != nil {
		t.Fatalf("GeneratePresignedGetURL: %v", err)
	}
	d, err := vault.ParsePresignedURLExpiry(u)
	if err != nil {
		t.Fatalf("ParsePresignedURLExpiry: %v", err)
	}
	const want = 5 * time.Minute
	if d != want {
		t.Errorf("expiry = %v, want %v", d, want)
	}
}

func TestMockStorage_HeadObject_Found(t *testing.T) {
	m := newMockStorage()
	key := vault.ObjectKey("a", "b", "c")
	m.put(key, 1234)

	size, err := m.HeadObject(context.Background(), key)
	if err != nil {
		t.Fatalf("HeadObject: %v", err)
	}
	if size != 1234 {
		t.Errorf("HeadObject size = %d, want 1234", size)
	}
}

func TestMockStorage_HeadObject_NotFound(t *testing.T) {
	m := newMockStorage()
	_, err := m.HeadObject(context.Background(), "does/not/exist")
	if err == nil {
		t.Error("HeadObject should return error for missing key")
	}
}

func TestMockStorage_DeleteObject_RemovesKey(t *testing.T) {
	m := newMockStorage()
	key := vault.ObjectKey("a", "b", "c")
	m.put(key, 100)

	if err := m.DeleteObject(context.Background(), key); err != nil {
		t.Fatalf("DeleteObject: %v", err)
	}
	_, err := m.HeadObject(context.Background(), key)
	if err == nil {
		t.Error("HeadObject should return error after deletion")
	}
}

// -------------------------------------------------------------------------
// ParsePresignedURLExpiry tests
// -------------------------------------------------------------------------

func TestParsePresignedURLExpiry_Valid(t *testing.T) {
	u := "https://example.com/key?X-Amz-Expires=300&other=val"
	d, err := vault.ParsePresignedURLExpiry(u)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if d != 5*time.Minute {
		t.Errorf("got %v, want %v", d, 5*time.Minute)
	}
}

func TestParsePresignedURLExpiry_Missing(t *testing.T) {
	u := "https://example.com/key?other=val"
	_, err := vault.ParsePresignedURLExpiry(u)
	if err == nil {
		t.Error("expected error for missing X-Amz-Expires")
	}
}

func TestParsePresignedURLExpiry_InvalidURL(t *testing.T) {
	_, err := vault.ParsePresignedURLExpiry("://bad url")
	if err == nil {
		t.Error("expected error for invalid URL")
	}
}

// -------------------------------------------------------------------------
// Interface compliance: ensure mockStorage satisfies ObjectStorage
// -------------------------------------------------------------------------

var _ vault.ObjectStorage = (*mockStorage)(nil)

// -------------------------------------------------------------------------
// URL key format regression test
// -------------------------------------------------------------------------

func TestPresignedPutURL_KeyFormat(t *testing.T) {
	m := newMockStorage()
	const (
		accountUUID = "11111111-1111-1111-1111-111111111111"
		docUUID     = "22222222-2222-2222-2222-222222222222"
		pageUUID    = "33333333-3333-3333-3333-333333333333"
	)
	key := vault.ObjectKey(accountUUID, docUUID, pageUUID)
	rawURL, err := m.GeneratePresignedPutURL(context.Background(), key, 1024*1024)
	if err != nil {
		t.Fatalf("GeneratePresignedPutURL: %v", err)
	}

	parsed, err := url.Parse(rawURL)
	if err != nil {
		t.Fatalf("url.Parse: %v", err)
	}

	// The path should contain the full key
	if !strings.Contains(parsed.Path, "vault/") {
		t.Errorf("URL path should contain 'vault/', got path %q", parsed.Path)
	}
	if !strings.Contains(parsed.Path, pageUUID+".enc") {
		t.Errorf("URL path should contain page uuid + .enc, got path %q", parsed.Path)
	}
}
