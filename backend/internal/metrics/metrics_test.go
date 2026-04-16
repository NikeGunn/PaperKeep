package metrics_test

import (
	"context"
	"sync"
	"testing"
	"time"

	"github.com/nikhil/scanvault-api/internal/metrics"
)

// fakeEmitter records every call so tests can assert on it.
type fakeEmitter struct {
	mu       sync.Mutex
	counts   []countCall
	durations []durCall
}

type countCall struct {
	name  string
	value int64
}

type durCall struct {
	name  string
	value float64
}

func (f *fakeEmitter) EmitCount(_ context.Context, name string, value int64, _ map[string]string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.counts = append(f.counts, countCall{name, value})
	return nil
}

func (f *fakeEmitter) EmitDuration(_ context.Context, name string, value float64, _ map[string]string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.durations = append(f.durations, durCall{name, value})
	return nil
}

func waitForMetrics(t *testing.T, f *fakeEmitter, wantCounts, wantDurations int) {
	t.Helper()
	deadline := time.Now().Add(500 * time.Millisecond)
	for time.Now().Before(deadline) {
		f.mu.Lock()
		c, d := len(f.counts), len(f.durations)
		f.mu.Unlock()
		if c >= wantCounts && d >= wantDurations {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	t.Fatalf("timeout waiting for metrics: counts=%d (want %d), durations=%d (want %d)",
		len(f.counts), wantCounts, len(f.durations), wantDurations)
}

func TestRequestCountIncrementsOnRecord(t *testing.T) {
	fe := &fakeEmitter{}
	col := metrics.NewCollector(fe)

	col.RecordRequest(context.Background(), 10.5)
	waitForMetrics(t, fe, 1, 1)

	snap := col.Snapshot()
	if snap.RequestCount != 1 {
		t.Fatalf("expected RequestCount=1, got %d", snap.RequestCount)
	}
}

func TestRequestDurationMsEmitted(t *testing.T) {
	fe := &fakeEmitter{}
	col := metrics.NewCollector(fe)

	col.RecordRequest(context.Background(), 25.0)
	waitForMetrics(t, fe, 1, 1)

	fe.mu.Lock()
	defer fe.mu.Unlock()
	if len(fe.durations) == 0 {
		t.Fatal("expected at least one duration metric")
	}
	if fe.durations[0].name != "RequestDurationMs" {
		t.Fatalf("wrong metric name: %s", fe.durations[0].name)
	}
	if fe.durations[0].value != 25.0 {
		t.Fatalf("expected 25.0ms, got %f", fe.durations[0].value)
	}
}

func TestMetricsIncrementOnMultipleRequests(t *testing.T) {
	fe := &fakeEmitter{}
	col := metrics.NewCollector(fe)

	for range 5 {
		col.RecordRequest(context.Background(), 1.0)
	}
	waitForMetrics(t, fe, 5, 5)

	snap := col.Snapshot()
	if snap.RequestCount != 5 {
		t.Fatalf("expected RequestCount=5, got %d", snap.RequestCount)
	}
}

func TestSnapshotAverageDuration(t *testing.T) {
	fe := &fakeEmitter{}
	col := metrics.NewCollector(fe)

	col.RecordRequest(context.Background(), 10.0)
	col.RecordRequest(context.Background(), 20.0)
	waitForMetrics(t, fe, 2, 2)

	snap := col.Snapshot()
	if snap.AvgRequestDurationMs != 15.0 {
		t.Fatalf("expected avg=15.0ms, got %f", snap.AvgRequestDurationMs)
	}
}

func TestNoopEmitterDoesNotPanic(t *testing.T) {
	col := metrics.NewCollector(metrics.NoopEmitter{})
	col.RecordRequest(context.Background(), 5.0)
	time.Sleep(20 * time.Millisecond) // let goroutine run
}

// --- CloudWatchPutter mock ---

type fakePutter struct {
	mu    sync.Mutex
	calls []putCall
}

type putCall struct {
	namespace string
	name      string
	value     float64
	unit      string
}

func (f *fakePutter) PutMetricData(_ context.Context, ns, name string, value float64, unit string, _ map[string]string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.calls = append(f.calls, putCall{ns, name, value, unit})
	return nil
}

func TestCloudWatchEmitterEmitsCount(t *testing.T) {
	fp := &fakePutter{}
	emitter := metrics.NewCloudWatchEmitter("ScanVault", fp)

	_ = emitter.EmitCount(context.Background(), "RequestCount", 3, nil)

	fp.mu.Lock()
	defer fp.mu.Unlock()
	if len(fp.calls) == 0 {
		t.Fatal("expected PutMetricData to be called")
	}
	if fp.calls[0].namespace != "ScanVault" {
		t.Fatalf("wrong namespace: %s", fp.calls[0].namespace)
	}
	if fp.calls[0].unit != "Count" {
		t.Fatalf("wrong unit: %s", fp.calls[0].unit)
	}
	if fp.calls[0].value != 3 {
		t.Fatalf("wrong value: %f", fp.calls[0].value)
	}
}

func TestCloudWatchEmitterEmitsDuration(t *testing.T) {
	fp := &fakePutter{}
	emitter := metrics.NewCloudWatchEmitter("ScanVault", fp)

	_ = emitter.EmitDuration(context.Background(), "RequestDurationMs", 42.5, nil)

	fp.mu.Lock()
	defer fp.mu.Unlock()
	if fp.calls[0].unit != "Milliseconds" {
		t.Fatalf("expected Milliseconds, got %s", fp.calls[0].unit)
	}
}
