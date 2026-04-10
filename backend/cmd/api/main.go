package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/nikhil/scanvault-api/internal/config"
	"github.com/nikhil/scanvault-api/internal/server"
)

func main() {
	// -------------------------------------------------------------------------
	// Logger — JSON structured logging, no secrets ever emitted
	// -------------------------------------------------------------------------
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))
	slog.SetDefault(logger)

	// -------------------------------------------------------------------------
	// Config — crash loudly on misconfiguration so the process never starts silently broken
	// -------------------------------------------------------------------------
	cfg := config.MustLoad()
	logger.Info("config loaded", slog.String("env", cfg.Environment), slog.String("port", cfg.ServerPort))

	// -------------------------------------------------------------------------
	// Database — pgxpool with a 10-second connection timeout
	// -------------------------------------------------------------------------
	ctx := context.Background()
	dbCtx, dbCancel := context.WithTimeout(ctx, 10*time.Second)
	pool, err := pgxpool.New(dbCtx, cfg.DatabaseURL)
	dbCancel()
	if err != nil {
		logger.Error("failed to open database pool", slog.String("error", err.Error()))
		os.Exit(1)
	}
	defer pool.Close()

	if err := pool.Ping(ctx); err != nil {
		logger.Error("database not reachable at startup", slog.String("error", err.Error()))
		os.Exit(1)
	}
	logger.Info("database connected")

	// -------------------------------------------------------------------------
	// Server
	// -------------------------------------------------------------------------
	srv := server.New(cfg, pool, logger)

	// Graceful shutdown: wait for SIGTERM or SIGINT, then give in-flight
	// requests up to 30 seconds to complete before forcibly closing.
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGTERM, syscall.SIGINT)

	go func() {
		if err := srv.Start(); err != nil {
			logger.Error("server error", slog.String("error", err.Error()))
			os.Exit(1)
		}
	}()

	<-quit
	logger.Info("shutdown signal received")

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer shutdownCancel()

	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("shutdown error", slog.String("error", err.Error()))
		os.Exit(1)
	}

	logger.Info("server stopped cleanly")
}
