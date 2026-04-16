// Package ratelimit provides a Redis-backed sliding-window rate limiter.
package ratelimit

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// RedisLimiter implements a sliding-window rate limiter using a Redis sorted set.
// Each key maps to a sorted set where members are unique request IDs and scores
// are Unix timestamps (nanoseconds). The Lua script atomically removes expired
// entries, adds the new request, and checks the count.
type RedisLimiter struct {
	client *redis.Client
}

// NewRedisLimiter constructs a RedisLimiter connected to the given Redis URL.
// redisURL format: redis://:password@host:port/db
func NewRedisLimiter(redisURL string) (*RedisLimiter, error) {
	opts, err := redis.ParseURL(redisURL)
	if err != nil {
		return nil, fmt.Errorf("parse redis URL: %w", err)
	}
	client := redis.NewClient(opts)
	return &RedisLimiter{client: client}, nil
}

// NewRedisLimiterFromClient constructs a RedisLimiter from an existing redis.Client.
// Useful for testing with miniredis.
func NewRedisLimiterFromClient(client *redis.Client) *RedisLimiter {
	return &RedisLimiter{client: client}
}

// slidingWindowLua atomically:
//  1. Removes members with score < (now - window_ns)
//  2. Adds the new request with score=now
//  3. Sets the key TTL to the window
//  4. Returns the current count
//
// KEYS[1] = rate limit key
// ARGV[1] = now (unix nanoseconds as string)
// ARGV[2] = window start (now - window_ns) as string
// ARGV[3] = unique member (request timestamp + random suffix)
// ARGV[4] = window in seconds (for EXPIRE)
var slidingWindowLua = redis.NewScript(`
local key    = KEYS[1]
local now    = tonumber(ARGV[1])
local cutoff = tonumber(ARGV[2])
local member = ARGV[3]
local ttl    = tonumber(ARGV[4])

redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)
redis.call('ZADD', key, now, member)
redis.call('EXPIRE', key, ttl)
return redis.call('ZCARD', key)
`)

// Allow returns true if the key is within limit requests per window.
// It uses a Redis sliding-window algorithm: each call adds a scored entry to
// a sorted set and counts members within the window.
func (r *RedisLimiter) Allow(ctx context.Context, key string, limit int, window time.Duration) (bool, error) {
	now := time.Now().UnixNano()
	windowNs := window.Nanoseconds()
	cutoff := now - windowNs
	member := fmt.Sprintf("%d", now)
	ttlSecs := int64(window.Seconds()) + 1

	result, err := slidingWindowLua.Run(
		ctx, r.client,
		[]string{key},
		fmt.Sprintf("%d", now),
		fmt.Sprintf("%d", cutoff),
		member,
		fmt.Sprintf("%d", ttlSecs),
	).Int64()
	if err != nil {
		return false, fmt.Errorf("redis rate limit: %w", err)
	}
	return result <= int64(limit), nil
}

// Close closes the underlying Redis connection.
func (r *RedisLimiter) Close() error {
	return r.client.Close()
}
