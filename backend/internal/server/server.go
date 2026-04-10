// Package server wires the chi router, middleware chain, and HTTP server.
package server

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/nikhil/scanvault-api/internal/accounts"
	"github.com/nikhil/scanvault-api/internal/config"
)

// Server holds the HTTP server and its dependencies.
type Server struct {
	httpServer  *http.Server
	router      http.Handler
	cfg         *config.Config
	db          *pgxpool.Pool
	logger      *slog.Logger
	accountsSvc *accounts.Service
}

// New constructs a Server with the full middleware chain and routes wired up.
// accountsSvc may be nil (e.g. in tests that don't need auth routes).
func New(cfg *config.Config, db *pgxpool.Pool, logger *slog.Logger, accountsSvc ...*accounts.Service) *Server {
	var svc *accounts.Service
	if len(accountsSvc) > 0 {
		svc = accountsSvc[0]
	}
	s := &Server{cfg: cfg, db: db, logger: logger, accountsSvc: svc}

	r := chi.NewRouter()

	// -------------------------------------------------------------------------
	// Middleware chain (order is significant)
	// -------------------------------------------------------------------------
	r.Use(middleware.RequestID)    // Attach X-Request-Id to every request
	r.Use(s.echoRequestID())       // Echo X-Request-Id back on every response
	r.Use(middleware.RealIP)       // Honour X-Forwarded-For / X-Real-IP from Caddy
	r.Use(s.structuredLogger())    // JSON request log line via slog
	r.Use(middleware.Recoverer)   // Recover from panics, return 500
	r.Use(middleware.Timeout(60 * time.Second)) // Global request timeout
	r.Use(s.secureHeaders())      // Security response headers
	r.Use(s.corsMiddleware())     // CORS policy

	// -------------------------------------------------------------------------
	// Routes
	// -------------------------------------------------------------------------
	r.Get("/health", s.handleHealth)
	r.Get("/ready", s.handleReady)

	// v1 API
	r.Route("/v1", func(r chi.Router) {
		if s.accountsSvc != nil {
			h := accounts.NewHandler(s.accountsSvc)
			r.Post("/accounts", h.HandleCreateAccount)
			r.Get("/accounts/verify", h.HandleVerifyEmail)
			r.Post("/sessions", h.HandleLogin)
			r.Post("/sessions/refresh", h.HandleRefresh)
		}
	})

	s.router = r
	s.httpServer = &http.Server{
		Addr:              ":" + cfg.ServerPort,
		Handler:           r,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      60 * time.Second,
		IdleTimeout:       120 * time.Second,
		ReadHeaderTimeout: 5 * time.Second,
	}

	return s
}

// NewWithPanicRoute builds a server that also registers a route that panics —
// used in tests to verify the Recoverer middleware works.
func NewWithPanicRoute(cfg *config.Config, db *pgxpool.Pool, logger *slog.Logger) *Server {
	s := New(cfg, db, logger)
	r := s.router.(*chi.Mux)
	r.Get("/test/panic", func(w http.ResponseWriter, r *http.Request) {
		panic("deliberate test panic")
	})
	return s
}

// NewWithSlowRoute builds a server that registers a route which sleeps for d —
// used in tests to verify graceful shutdown waits for in-flight requests.
func NewWithSlowRoute(cfg *config.Config, db *pgxpool.Pool, logger *slog.Logger, d time.Duration) *Server {
	s := New(cfg, db, logger)
	r := s.router.(*chi.Mux)
	r.Get("/test/slow", func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(d)
		w.WriteHeader(http.StatusOK)
	})
	return s
}

// Handler returns the underlying http.Handler, for use with httptest.
func (s *Server) Handler() http.Handler {
	return s.router
}

// Serve accepts connections on l instead of binding itself. Used in tests.
func (s *Server) Serve(l net.Listener) error {
	s.httpServer.Handler = s.router
	return s.httpServer.Serve(l)
}

// Start begins listening. It blocks until the server is shut down.
// Returns http.ErrServerClosed on graceful shutdown, other errors on failure.
func (s *Server) Start() error {
	s.logger.Info("server starting", slog.String("addr", s.httpServer.Addr),
		slog.String("env", s.cfg.Environment))
	err := s.httpServer.ListenAndServe()
	if errors.Is(err, http.ErrServerClosed) {
		return nil
	}
	return err
}

// Shutdown performs a graceful shutdown with the given context deadline.
func (s *Server) Shutdown(ctx context.Context) error {
	s.logger.Info("server shutting down gracefully")
	return s.httpServer.Shutdown(ctx)
}

// Addr returns the address the server is actually listening on.
// Only valid after Start() is called (or for test servers via httptest).
func (s *Server) Addr() string {
	return s.httpServer.Addr
}

// ListenAndServeOnFreePort binds to :0 (random free port) for testing.
// Returns a Listener the caller must pass to Serve(), and the chosen address.
func ListenAndServeOnFreePort() (net.Listener, error) {
	return net.Listen("tcp", ":0")
}

// -------------------------------------------------------------------------
// Internal middleware helpers
// -------------------------------------------------------------------------

// echoRequestID echoes the request ID from the context back as an X-Request-Id
// response header so clients can correlate requests with logs.
func (s *Server) echoRequestID() func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if id := middleware.GetReqID(r.Context()); id != "" {
				w.Header().Set("X-Request-Id", id)
			}
			next.ServeHTTP(w, r)
		})
	}
}

// structuredLogger returns middleware that emits one JSON log line per request.
func (s *Server) structuredLogger() func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
			start := time.Now()
			next.ServeHTTP(ww, r)
			s.logger.Info("request",
				slog.String("request_id", middleware.GetReqID(r.Context())),
				slog.String("method", r.Method),
				slog.String("path", r.URL.Path),
				slog.Int("status", ww.Status()),
				slog.Int64("bytes", int64(ww.BytesWritten())),
				slog.Duration("duration", time.Since(start)),
				slog.String("remote_addr", r.RemoteAddr),
			)
		})
	}
}

// secureHeaders sets standard security response headers on every response.
func (s *Server) secureHeaders() func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			h := w.Header()
			h.Set("X-Content-Type-Options", "nosniff")
			h.Set("X-Frame-Options", "DENY")
			h.Set("Referrer-Policy", "strict-origin-when-cross-origin")
			h.Set("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
			// HSTS is set by Caddy in production — skip in dev to avoid browser lock-in
			if s.cfg.Environment == "prod" {
				h.Set("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
			}
			next.ServeHTTP(w, r)
		})
	}
}

// corsMiddleware returns a simple CORS handler tuned for the ScanVault API.
// Android clients use the API directly (no browser CORS needed),
// but we may add a web dashboard later.
func (s *Server) corsMiddleware() func(http.Handler) http.Handler {
	allowed := map[string]bool{
		"https://scanvault.app":         true,
		"https://www.scanvault.app":     true,
		"https://app-staging.scanvault.app": true,
	}
	// In dev, allow all origins for local tooling
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			origin := r.Header.Get("Origin")
			if origin != "" {
				if s.cfg.Environment == "dev" || allowed[origin] {
					w.Header().Set("Access-Control-Allow-Origin", origin)
					w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
					w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-Id")
					w.Header().Set("Access-Control-Max-Age", "3600")
				}
			}
			if r.Method == http.MethodOptions {
				w.WriteHeader(http.StatusNoContent)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// -------------------------------------------------------------------------
// Health & ready handlers
// -------------------------------------------------------------------------

// healthResponse is the JSON body for /health.
type healthResponse struct {
	Status  string `json:"status"`
	Version string `json:"version"`
}

// handleHealth returns 200 immediately. Caddy uses this to decide whether
// to route traffic to this instance. It does NOT check dependencies.
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	respond(w, http.StatusOK, healthResponse{
		Status:  "ok",
		Version: "0.1.0",
	})
}

// readyResponse is the JSON body for /ready.
type readyResponse struct {
	Status     string            `json:"status"`
	Components map[string]string `json:"components"`
}

// handleReady checks each dependency and returns 200 only when all are healthy.
// Returns 503 if any dependency is down, with component-level detail.
func (s *Server) handleReady(w http.ResponseWriter, r *http.Request) {
	resp := readyResponse{
		Status:     "ok",
		Components: make(map[string]string),
	}

	// Check Postgres
	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
	defer cancel()

	if s.db != nil {
		if err := s.db.Ping(ctx); err != nil {
			resp.Components["postgres"] = "unhealthy: " + err.Error()
			resp.Status = "degraded"
		} else {
			resp.Components["postgres"] = "ok"
		}
	} else {
		resp.Components["postgres"] = "not connected"
		resp.Status = "degraded"
	}

	status := http.StatusOK
	if resp.Status != "ok" {
		status = http.StatusServiceUnavailable
	}
	respond(w, status, resp)
}

// respond encodes v as JSON and writes it with the given status code.
func respond(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		// Nothing useful to do here — headers already sent
		_ = err
	}
}
