package ratelimit_test

import (
	"context"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/nikhil/scanvault-api/internal/ratelimit"
)

// startRedis spins up a throwaway Redis container for tests.
func startRedis(t *testing.T) *redis.Client {
	t.Helper()
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	ctx := context.Background()
	req := testcontainers.ContainerRequest{
		Image:        "redis:7-alpine",
		ExposedPorts: []string{"6379/tcp"},
		WaitingFor:   wait.ForLog("Ready to accept connections").WithStartupTimeout(30 * time.Second),
	}
	ctr, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	})
	require.NoError(t, err)
	t.Cleanup(func() { _ = ctr.Terminate(ctx) })

	host, err := ctr.Host(ctx)
	require.NoError(t, err)
	port, err := ctr.MappedPort(ctx, "6379")
	require.NoError(t, err)

	client := redis.NewClient(&redis.Options{
		Addr: host + ":" + port.Port(),
	})
	t.Cleanup(func() { _ = client.Close() })

	return client
}

func TestRedisLimiter_AllowsWithinLimit(t *testing.T) {
	client := startRedis(t)
	rl := ratelimit.NewRedisLimiterFromClient(client)
	ctx := context.Background()

	key := "test:allow:within"
	for i := 0; i < 5; i++ {
		allowed, err := rl.Allow(ctx, key, 5, time.Minute)
		require.NoError(t, err)
		assert.True(t, allowed, "request %d should be allowed", i+1)
	}
}

func TestRedisLimiter_BlocksAboveLimit(t *testing.T) {
	client := startRedis(t)
	rl := ratelimit.NewRedisLimiterFromClient(client)
	ctx := context.Background()

	key := "test:block:above"
	for i := 0; i < 3; i++ {
		_, _ = rl.Allow(ctx, key, 3, time.Minute)
	}
	// 4th request should be blocked
	allowed, err := rl.Allow(ctx, key, 3, time.Minute)
	require.NoError(t, err)
	assert.False(t, allowed, "4th request should be blocked")
}

func TestRedisLimiter_SlidingWindow_ExpiresOldEntries(t *testing.T) {
	client := startRedis(t)
	rl := ratelimit.NewRedisLimiterFromClient(client)
	ctx := context.Background()

	// Use a very short window so old entries expire quickly
	key := "test:sliding:expire"
	window := 100 * time.Millisecond

	// Fill up the limit
	for i := 0; i < 3; i++ {
		allowed, err := rl.Allow(ctx, key, 3, window)
		require.NoError(t, err)
		assert.True(t, allowed)
	}

	// Blocked immediately
	allowed, err := rl.Allow(ctx, key, 3, window)
	require.NoError(t, err)
	assert.False(t, allowed)

	// Wait for window to expire
	time.Sleep(150 * time.Millisecond)

	// Should be allowed again
	allowed, err = rl.Allow(ctx, key, 3, window)
	require.NoError(t, err)
	assert.True(t, allowed, "should be allowed after window expires")
}

func TestRedisLimiter_MultipleKeys_Independent(t *testing.T) {
	client := startRedis(t)
	rl := ratelimit.NewRedisLimiterFromClient(client)
	ctx := context.Background()

	key1 := "test:multi:key1"
	key2 := "test:multi:key2"

	// Exhaust key1
	for i := 0; i < 2; i++ {
		_, _ = rl.Allow(ctx, key1, 2, time.Minute)
	}
	blocked, err := rl.Allow(ctx, key1, 2, time.Minute)
	require.NoError(t, err)
	assert.False(t, blocked, "key1 should be blocked")

	// key2 should still be allowed
	allowed, err := rl.Allow(ctx, key2, 2, time.Minute)
	require.NoError(t, err)
	assert.True(t, allowed, "key2 should be independent")
}
