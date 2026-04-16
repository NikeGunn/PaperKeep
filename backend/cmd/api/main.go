package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/nikhil/scanvault-api/internal/accounts"
	"github.com/nikhil/scanvault-api/internal/config"
	applogger "github.com/nikhil/scanvault-api/internal/logger"
	"github.com/nikhil/scanvault-api/internal/server"
	"github.com/nikhil/scanvault-api/internal/vault"
)

func main() {
	// -------------------------------------------------------------------------
	// Logger — JSON structured, no secrets ever emitted, build metadata baked in
	// -------------------------------------------------------------------------
	logger := applogger.New(slog.LevelInfo)
	slog.SetDefault(logger)

	// -------------------------------------------------------------------------
	// Config — crash loudly on misconfiguration
	// -------------------------------------------------------------------------
	cfg := config.MustLoad()
	logger.Info("config loaded",
		slog.String("env", cfg.Environment),
		slog.String("port", cfg.ServerPort),
	)

	// -------------------------------------------------------------------------
	// Database — pgxpool, 10-second connection timeout at startup
	// -------------------------------------------------------------------------
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

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
	// Services
	// -------------------------------------------------------------------------
	accountsSvc, err := accounts.New(pool, cfg, logger)
	if err != nil {
		logger.Error("failed to initialize accounts service", slog.String("error", err.Error()))
		os.Exit(1)
	}

	s3Storage, err := vault.NewS3Storage(ctx, cfg.S3BucketName)
	if err != nil {
		logger.Error("failed to initialize S3 storage", slog.String("error", err.Error()))
		os.Exit(1)
	}

	vaultSvc := vault.NewService(pool, s3Storage, logger)
	vaultSvc.StartPurgeWorker(ctx, time.Hour)
	logger.Info("vault service initialized", slog.String("bucket", cfg.S3BucketName))

	// -------------------------------------------------------------------------
	// Server
	// -------------------------------------------------------------------------
	srv := server.New(cfg, pool, logger, accountsSvc).WithVaultService(vaultSvc)

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
	cancel()

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer shutdownCancel()

	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("shutdown error", slog.String("error", err.Error()))
		os.Exit(1)
	}
	logger.Info("server stopped cleanly")
}
