package cache_test

import (
	"fmt"
	"sync"
	"testing"

	"github.com/nikhil/scanvault-api/internal/cache"
)

func TestCacheHitReturnsSameValue(t *testing.T) {
	c := cache.New[string, int](10)
	c.Set("a", 42)
	v, ok := c.Get("a")
	if !ok {
		t.Fatal("expected cache hit")
	}
	if v != 42 {
		t.Fatalf("expected 42, got %d", v)
	}
}

func TestCacheMissCallsLoader(t *testing.T) {
	c := cache.New[string, int](10)
	calls := 0
	v, err := c.GetOrLoad("x", func() (int, error) {
		calls++
		return 99, nil
	})
	if err != nil {
		t.Fatal(err)
	}
	if v != 99 {
		t.Fatalf("expected 99, got %d", v)
	}
	if calls != 1 {
		t.Fatalf("loader called %d times, want 1", calls)
	}

	// Second call should be a cache hit — loader NOT called again.
	v2, err2 := c.GetOrLoad("x", func() (int, error) {
		calls++
		return 0, nil
	})
	if err2 != nil {
		t.Fatal(err2)
	}
	if v2 != 99 {
		t.Fatalf("expected 99 from cache, got %d", v2)
	}
	if calls != 1 {
		t.Fatalf("loader called again (calls=%d), want 1", calls)
	}
}

func TestCacheInvalidatedAfterDelete(t *testing.T) {
	c := cache.New[string, int](10)
	c.Set("key", 7)
	c.Delete("key")
	_, ok := c.Get("key")
	if ok {
		t.Fatal("expected cache miss after delete")
	}
}

func TestCacheEvictsOldestEntryWhenFull(t *testing.T) {
	cap := 3
	c := cache.New[int, string](cap)

	// Fill to capacity: 1, 2, 3
	c.Set(1, "one")
	c.Set(2, "two")
	c.Set(3, "three")

	// Access 1 so it's MRU, then access 2.
	c.Get(1)
	c.Get(2)

	// Insert 4 — should evict the LRU entry (3, since we accessed 1 and 2 more recently).
	c.Set(4, "four")

	if c.Len() != cap {
		t.Fatalf("expected len=%d, got %d", cap, c.Len())
	}

	if _, ok := c.Get(3); ok {
		t.Fatal("entry 3 should have been evicted (LRU)")
	}
	if _, ok := c.Get(1); !ok {
		t.Fatal("entry 1 should still be present (was recently used)")
	}
	if _, ok := c.Get(2); !ok {
		t.Fatal("entry 2 should still be present (was recently used)")
	}
	if _, ok := c.Get(4); !ok {
		t.Fatal("entry 4 should be present (just inserted)")
	}
}

func TestCacheLenUpdates(t *testing.T) {
	c := cache.New[string, int](5)
	if c.Len() != 0 {
		t.Fatal("expected empty cache")
	}
	c.Set("a", 1)
	c.Set("b", 2)
	if c.Len() != 2 {
		t.Fatalf("expected len=2, got %d", c.Len())
	}
	c.Delete("a")
	if c.Len() != 1 {
		t.Fatalf("expected len=1, got %d", c.Len())
	}
}

func TestCacheConcurrentAccess(t *testing.T) {
	c := cache.New[int, int](100)
	var wg sync.WaitGroup
	for i := range 50 {
		wg.Add(1)
		go func(n int) {
			defer wg.Done()
			c.Set(n, n*2)
			c.Get(n)
			c.Delete(n)
		}(i)
	}
	wg.Wait()
}

func TestCacheSetUpdatesExistingKey(t *testing.T) {
	c := cache.New[string, int](5)
	c.Set("k", 1)
	c.Set("k", 2)
	v, ok := c.Get("k")
	if !ok || v != 2 {
		t.Fatalf("expected 2 after update, got %d ok=%v", v, ok)
	}
	if c.Len() != 1 {
		t.Fatalf("expected len=1 after update, got %d", c.Len())
	}
}

func TestCacheCapacityPanic(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Fatal("expected panic on capacity <= 0")
		}
	}()
	cache.New[string, string](0)
}

func TestCacheLoaderErrorNotCached(t *testing.T) {
	c := cache.New[string, int](5)
	calls := 0
	_, err := c.GetOrLoad("fail", func() (int, error) {
		calls++
		return 0, fmt.Errorf("load error")
	})
	if err == nil {
		t.Fatal("expected error from loader")
	}
	if c.Len() != 0 {
		t.Fatal("errored entry should not be cached")
	}
	// Loader should be called again on next attempt.
	_, _ = c.GetOrLoad("fail", func() (int, error) {
		calls++
		return 0, fmt.Errorf("still failing")
	})
	if calls != 2 {
		t.Fatalf("expected loader called 2 times, got %d", calls)
	}
}
