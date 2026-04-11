package middleware

import (
	"net/http"
	"sync"
	"time"
)

// bucket is a fixed-window rate-limit bucket for a single key.
type bucket struct {
	mu        sync.Mutex
	count     int
	windowEnd time.Time
}

// allow returns true if the request is within the allowed limit.
// The window resets when windowEnd passes.
func (b *bucket) allow(limit int, window time.Duration) bool {
	b.mu.Lock()
	defer b.mu.Unlock()

	now := time.Now()
	if now.After(b.windowEnd) {
		// Start a fresh window
		b.count = 0
		b.windowEnd = now.Add(window)
	}
	if b.count >= limit {
		return false
	}
	b.count++
	return true
}

// RateLimiter holds all in-memory buckets and enforces per-key limits.
type RateLimiter struct {
	mu      sync.Mutex
	buckets map[string]*bucket
}

// NewRateLimiter creates an empty in-memory rate limiter.
// The backing map is cleaned up lazily on each request (expired buckets removed).
func NewRateLimiter() *RateLimiter {
	return &RateLimiter{buckets: make(map[string]*bucket)}
}

// Allow reports whether key is within limit over window. Thread-safe.
func (rl *RateLimiter) Allow(key string, limit int, window time.Duration) bool {
	rl.mu.Lock()
	b, ok := rl.buckets[key]
	if !ok {
		b = &bucket{}
		rl.buckets[key] = b
	}
	rl.mu.Unlock()

	return b.allow(limit, window)
}

// Cleanup removes expired buckets to prevent unbounded memory growth.
// Call this periodically (e.g. from a background goroutine) in production.
func (rl *RateLimiter) Cleanup() {
	now := time.Now()
	rl.mu.Lock()
	defer rl.mu.Unlock()
	for k, b := range rl.buckets {
		b.mu.Lock()
		expired := now.After(b.windowEnd)
		b.mu.Unlock()
		if expired {
			delete(rl.buckets, k)
		}
	}
}

// LoginRateLimit limits login attempts: 5 per IP per 15 minutes.
// The IP is taken from r.RemoteAddr (after RealIP middleware has run).
func LoginRateLimit(rl *RateLimiter) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ip := extractIP(r)
			if !rl.Allow("login:"+ip, 5, 15*time.Minute) {
				respondTooManyRequests(w, "rate_limit.login", "Too many login attempts. Try again later.")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// SignupRateLimit limits account creation: 3 per IP per hour.
func SignupRateLimit(rl *RateLimiter) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ip := extractIP(r)
			if !rl.Allow("signup:"+ip, 3, time.Hour) {
				respondTooManyRequests(w, "rate_limit.signup", "Too many signup attempts. Try again later.")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// extractIP returns the remote IP from the request.
// chi's RealIP middleware will have already set RemoteAddr from X-Forwarded-For.
func extractIP(r *http.Request) string {
	addr := r.RemoteAddr
	// RemoteAddr is "ip:port" for TCP connections; strip port.
	for i := len(addr) - 1; i >= 0; i-- {
		if addr[i] == ':' {
			return addr[:i]
		}
	}
	return addr
}

func respondTooManyRequests(w http.ResponseWriter, code, message string) {
	writeJSON(w, http.StatusTooManyRequests, errEnvelope{Error: errBody{Code: code, Message: message}})
}
