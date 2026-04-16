package sentryclient_test

import (
	"errors"
	"net/http"
	"testing"

	"github.com/nikhil/scanvault-api/internal/sentryclient"
)

// captureRecorder records calls to CaptureError.
type captureRecorder struct {
	calls []capturedEvent
}

type capturedEvent struct {
	err  error
	tags map[string]string
}

func (r *captureRecorder) CaptureError(err error, tags map[string]string) {
	r.calls = append(r.calls, capturedEvent{err: err, tags: tags})
}

func setup(t *testing.T, recorder sentryclient.Transport) {
	t.Helper()
	sentryclient.ResetForTest()
	sentryclient.Init("https://fake@sentry.io/1", recorder)
	t.Cleanup(sentryclient.ResetForTest)
}

// --------------------------------------------------------------------------

func TestCaptureHTTPError500Reported(t *testing.T) {
	r := &captureRecorder{}
	setup(t, r)

	testErr := errors.New("database exploded")
	sentryclient.CaptureHTTPError(http.StatusInternalServerError, testErr)

	if len(r.calls) != 1 {
		t.Fatalf("expected 1 capture, got %d", len(r.calls))
	}
	if r.calls[0].err != testErr {
		t.Errorf("wrong error captured")
	}
	if r.calls[0].tags["status_code"] != "500" {
		t.Errorf("expected tag status_code=500, got %v", r.calls[0].tags)
	}
}

func TestCaptureHTTPError4xxNotReported(t *testing.T) {
	r := &captureRecorder{}
	setup(t, r)

	for _, code := range []int{400, 401, 403, 404, 422, 429} {
		sentryclient.CaptureHTTPError(code, errors.New("client mistake"))
	}

	if len(r.calls) != 0 {
		t.Fatalf("expected no captures for 4xx, got %d", len(r.calls))
	}
}

func TestCaptureErrorUnconditional(t *testing.T) {
	r := &captureRecorder{}
	setup(t, r)

	sentryclient.CaptureError(errors.New("something bad"))
	if len(r.calls) != 1 {
		t.Fatalf("expected 1 capture, got %d", len(r.calls))
	}
}

func TestCaptureErrorNilNoOp(t *testing.T) {
	r := &captureRecorder{}
	setup(t, r)

	sentryclient.CaptureError(nil)
	if len(r.calls) != 0 {
		t.Fatalf("expected no captures for nil error, got %d", len(r.calls))
	}
}

func TestNoopTransportWhenDSNEmpty(t *testing.T) {
	sentryclient.ResetForTest()
	sentryclient.Init("") // no DSN → noop transport
	t.Cleanup(sentryclient.ResetForTest)

	// Should not panic
	sentryclient.CaptureHTTPError(500, errors.New("oops"))
	sentryclient.CaptureError(errors.New("also fine"))
}

func TestInit502AlsoReported(t *testing.T) {
	r := &captureRecorder{}
	setup(t, r)

	sentryclient.CaptureHTTPError(http.StatusBadGateway, errors.New("upstream"))
	if len(r.calls) != 1 {
		t.Fatalf("expected 1 capture for 502, got %d", len(r.calls))
	}
}
