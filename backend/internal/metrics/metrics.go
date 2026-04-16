// Package metrics exposes custom CloudWatch metrics for the ScanVault API.
//
// Metrics published:
//   - RequestCount        (Count)    — incremented per handled request
//   - RequestDurationMs   (Milliseconds) — duration of each request
//
// The namespace is "ScanVault". When AWS_REGION is not set (local dev)
// the emitter falls back to a no-op so the app still starts.
package metrics

import (
	"context"
	"log/slog"
	"sync"
	"sync/atomic"
	"time"
)

// Emitter sends metric data points to a backend (CloudWatch in prod, noop/fake
// in tests).
type Emitter interface {
	// EmitCount records an integer count metric.
	EmitCount(ctx context.Context, name string, value int64, dims map[string]string) error
	// EmitDuration records a duration metric in milliseconds.
	EmitDuration(ctx context.Context, name string, durationMs float64, dims map[string]string) error
}

// Collector accumulates in-memory counters and delegates to an Emitter.
type Collector struct {
	emitter      Emitter
	requestCount atomic.Int64
	mu           sync.Mutex
	// rolling sum and count for average duration
	durationSum   float64
	durationCount int64
}

// NewCollector creates a Collector backed by the given Emitter.
func NewCollector(emitter Emitter) *Collector {
	return &Collector{emitter: emitter}
}

// RecordRequest increments the request counter and records the duration.
// It also asynchronously pushes the metrics to the Emitter.
func (c *Collector) RecordRequest(ctx context.Context, durationMs float64) {
	c.requestCount.Add(1)

	c.mu.Lock()
	c.durationSum += durationMs
	c.durationCount++
	c.mu.Unlock()

	// Fire-and-forget to CloudWatch — never block the request path.
	go func() {
		if err := c.emitter.EmitCount(ctx, "RequestCount", 1, nil); err != nil {
			slog.Default().Warn("metrics: EmitCount failed", slog.String("error", err.Error()))
		}
		if err := c.emitter.EmitDuration(ctx, "RequestDurationMs", durationMs, nil); err != nil {
			slog.Default().Warn("metrics: EmitDuration failed", slog.String("error", err.Error()))
		}
	}()
}

// Snapshot returns a point-in-time summary of the accumulated metrics.
func (c *Collector) Snapshot() Snapshot {
	c.mu.Lock()
	defer c.mu.Unlock()

	var avgDur float64
	if c.durationCount > 0 {
		avgDur = c.durationSum / float64(c.durationCount)
	}
	return Snapshot{
		RequestCount:        c.requestCount.Load(),
		AvgRequestDurationMs: avgDur,
		TotalDurationMs:     c.durationSum,
	}
}

// Snapshot is a copy of the Collector's counters at a point in time.
type Snapshot struct {
	RequestCount         int64   `json:"request_count"`
	AvgRequestDurationMs float64 `json:"avg_request_duration_ms"`
	TotalDurationMs      float64 `json:"total_duration_ms"`
}

// -------------------------------------------------------------------------
// NoopEmitter — used in local dev / tests
// -------------------------------------------------------------------------

// NoopEmitter discards all metric events.
type NoopEmitter struct{}

func (NoopEmitter) EmitCount(_ context.Context, _ string, _ int64, _ map[string]string) error {
	return nil
}
func (NoopEmitter) EmitDuration(_ context.Context, _ string, _ float64, _ map[string]string) error {
	return nil
}

// -------------------------------------------------------------------------
// CloudWatchEmitter
// -------------------------------------------------------------------------

// CloudWatchPutter is the subset of the CloudWatch client used by CloudWatchEmitter.
// Defined as an interface so tests can inject a mock.
type CloudWatchPutter interface {
	PutMetricData(ctx context.Context, namespace string, metricName string, value float64, unit string, dims map[string]string) error
}

// CloudWatchEmitter publishes metrics to AWS CloudWatch under the "ScanVault" namespace.
type CloudWatchEmitter struct {
	namespace string
	putter    CloudWatchPutter
}

// NewCloudWatchEmitter creates a CloudWatchEmitter. Pass a CloudWatchPutter
// implementation (real SDK wrapper in production, mock in tests).
func NewCloudWatchEmitter(namespace string, putter CloudWatchPutter) *CloudWatchEmitter {
	return &CloudWatchEmitter{namespace: namespace, putter: putter}
}

func (e *CloudWatchEmitter) EmitCount(ctx context.Context, name string, value int64, dims map[string]string) error {
	return e.putter.PutMetricData(ctx, e.namespace, name, float64(value), "Count", dims)
}

func (e *CloudWatchEmitter) EmitDuration(ctx context.Context, name string, durationMs float64, dims map[string]string) error {
	return e.putter.PutMetricData(ctx, e.namespace, name, durationMs, "Milliseconds", dims)
}

// -------------------------------------------------------------------------
// HTTP middleware helper
// -------------------------------------------------------------------------

// MiddlewareFunc returns an http.Handler middleware that records request metrics.
// Import net/http in the caller — we avoid pulling http into this package.
type RequestRecorder func(durationMs float64)

// NewRequestTimer starts a timer and returns a function that, when called,
// records the elapsed time via recorder.
func NewRequestTimer(recorder RequestRecorder) (stop func()) {
	start := time.Now()
	return func() {
		elapsed := float64(time.Since(start).Microseconds()) / 1000.0
		recorder(elapsed)
	}
}
