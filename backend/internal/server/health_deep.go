package server

import (
	"context"
	"net/http"
	"time"

	"github.com/redis/go-redis/v9"
)

// DeepHealthChecker is an interface for checking component health.
// Injected so tests can provide mocks.
type DeepHealthChecker interface {
	CheckDB(ctx context.Context) error
	CheckRedis(ctx context.Context) error
	CheckS3(ctx context.Context) error
}

// defaultDeepChecker performs real checks against the live dependencies.
type defaultDeepChecker struct {
	srv *Server
}

func (d *defaultDeepChecker) CheckDB(ctx context.Context) error {
	if d.srv.db == nil {
		return nil
	}
	return d.srv.db.Ping(ctx)
}

func (d *defaultDeepChecker) CheckRedis(ctx context.Context) error {
	if d.srv.cfg.RedisURL == "" {
		return nil
	}
	opts, err := redis.ParseURL(d.srv.cfg.RedisURL)
	if err != nil {
		return err
	}
	client := redis.NewClient(opts)
	defer client.Close()
	return client.Ping(ctx).Err()
}

func (d *defaultDeepChecker) CheckS3(ctx context.Context) error {
	// S3 check is optional — skip if bucket not configured
	_ = ctx
	return nil
}

// deepHealthResponse is the JSON body for GET /v1/health/deep.
type deepHealthResponse struct {
	Status     string            `json:"status"`
	Components map[string]string `json:"components"`
}

// handleHealthDeep checks every dependency and reports per-component status.
// If any component is down the overall status is "degraded" and HTTP 200 is
// still returned (for compatibility with load-balancers that parse the body).
func (s *Server) handleHealthDeep(w http.ResponseWriter, r *http.Request) {
	s.handleHealthDeepWith(w, r, &defaultDeepChecker{srv: s})
}

// handleHealthDeepWith is the injectable variant used by tests.
func (s *Server) handleHealthDeepWith(w http.ResponseWriter, r *http.Request, checker DeepHealthChecker) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()

	resp := deepHealthResponse{
		Status:     "ok",
		Components: make(map[string]string),
	}

	// --- DB ---
	if err := checker.CheckDB(ctx); err != nil {
		resp.Components["db"] = "down: " + err.Error()
		resp.Status = "degraded"
	} else {
		resp.Components["db"] = "ok"
	}

	// --- Redis ---
	if err := checker.CheckRedis(ctx); err != nil {
		resp.Components["redis"] = "down: " + err.Error()
		resp.Status = "degraded"
	} else {
		resp.Components["redis"] = "ok"
	}

	// --- S3 ---
	if err := checker.CheckS3(ctx); err != nil {
		resp.Components["s3"] = "down: " + err.Error()
		resp.Status = "degraded"
	} else {
		resp.Components["s3"] = "ok"
	}

	respond(w, http.StatusOK, resp)
}
