// Package cache provides a thread-safe in-process LRU cache with a maximum
// capacity and write-through invalidation.
package cache

import (
	"container/list"
	"sync"
)

// entry is a single item stored in the cache.
type entry[K comparable, V any] struct {
	key   K
	value V
}

// Cache is a generic, thread-safe LRU cache.
// The zero value is not usable — use New().
type Cache[K comparable, V any] struct {
	mu       sync.Mutex
	capacity int
	items    map[K]*list.Element
	lru      *list.List // front = MRU, back = LRU
}

// New creates a Cache that holds at most capacity entries.
// capacity must be > 0 (panics otherwise).
func New[K comparable, V any](capacity int) *Cache[K, V] {
	if capacity <= 0 {
		panic("cache: capacity must be > 0")
	}
	return &Cache[K, V]{
		capacity: capacity,
		items:    make(map[K]*list.Element, capacity),
		lru:      list.New(),
	}
}

// Get returns the value associated with key and true if found.
// Accessing a key marks it as recently used.
func (c *Cache[K, V]) Get(key K) (V, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()

	el, ok := c.items[key]
	if !ok {
		var zero V
		return zero, false
	}
	c.lru.MoveToFront(el)
	return el.Value.(*entry[K, V]).value, true
}

// Set stores key → value.  If the cache is full, the least-recently-used
// entry is evicted first.
func (c *Cache[K, V]) Set(key K, value V) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if el, ok := c.items[key]; ok {
		// Update existing entry and mark as MRU.
		el.Value.(*entry[K, V]).value = value
		c.lru.MoveToFront(el)
		return
	}

	// Evict LRU entry if at capacity.
	if c.lru.Len() >= c.capacity {
		oldest := c.lru.Back()
		if oldest != nil {
			c.lru.Remove(oldest)
			delete(c.items, oldest.Value.(*entry[K, V]).key)
		}
	}

	el := c.lru.PushFront(&entry[K, V]{key: key, value: value})
	c.items[key] = el
}

// Delete removes key from the cache (no-op if not present).
func (c *Cache[K, V]) Delete(key K) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if el, ok := c.items[key]; ok {
		c.lru.Remove(el)
		delete(c.items, key)
	}
}

// Len returns the number of entries currently in the cache.
func (c *Cache[K, V]) Len() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.lru.Len()
}

// GetOrLoad returns the cached value for key, or calls loader to fetch it,
// stores the result, and returns it. Errors from loader are not cached.
func (c *Cache[K, V]) GetOrLoad(key K, loader func() (V, error)) (V, error) {
	if v, ok := c.Get(key); ok {
		return v, nil
	}
	v, err := loader()
	if err != nil {
		return v, err
	}
	c.Set(key, v)
	return v, nil
}
