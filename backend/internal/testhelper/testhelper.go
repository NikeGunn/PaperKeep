// Package testhelper provides shared utilities for integration tests.
package testhelper

import (
	"context"
	"database/sql"
	"fmt"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	_ "github.com/lib/pq"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/pressly/goose/v3"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"
)

// MigrationsDir returns the absolute path to db/migrations.
func MigrationsDir() string {
	_, file, _, _ := runtime.Caller(0)
	// file is .../backend/internal/testhelper/testhelper.go
	// migrations are at .../backend/db/migrations
	return filepath.Join(filepath.Dir(file), "..", "..", "db", "migrations")
}

// SetupTestDB starts a throwaway Postgres container, runs all migrations, and
// returns a connected *pgxpool.Pool. The container and pool are cleaned up
// automatically when t finishes.
func SetupTestDB(t *testing.T) *pgxpool.Pool {
	t.Helper()
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	ctx := context.Background()

	ctr, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("testdb"),
		postgres.WithUsername("testuser"),
		postgres.WithPassword("testpass"),
		testcontainers.WithWaitStrategy(
			wait.ForLog("database system is ready to accept connections").
				WithOccurrence(2).
				WithStartupTimeout(60*time.Second),
		),
	)
	if err != nil {
		t.Fatalf("start postgres container: %v", err)
	}
	t.Cleanup(func() {
		if err := ctr.Terminate(ctx); err != nil {
			t.Logf("terminate postgres container: %v", err)
		}
	})

	host, err := ctr.Host(ctx)
	if err != nil {
		t.Fatalf("get container host: %v", err)
	}
	port, err := ctr.MappedPort(ctx, "5432")
	if err != nil {
		t.Fatalf("get container port: %v", err)
	}

	dsn := fmt.Sprintf("postgres://testuser:testpass@%s:%s/testdb?sslmode=disable", host, port.Port())

	// Run migrations
	db, err := sql.Open("postgres", dsn)
	if err != nil {
		t.Fatalf("open stdlib db: %v", err)
	}
	defer db.Close()

	goose.SetTableName("schema_migrations")
	if err := goose.SetDialect("postgres"); err != nil {
		t.Fatalf("goose set dialect: %v", err)
	}
	if err := goose.Up(db, MigrationsDir()); err != nil {
		t.Fatalf("goose up: %v", err)
	}

	// Open pgxpool
	ctxConn, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctxConn, dsn)
	if err != nil {
		t.Fatalf("open pgxpool: %v", err)
	}
	if err := pool.Ping(ctxConn); err != nil {
		t.Fatalf("ping pool: %v", err)
	}
	t.Cleanup(pool.Close)

	return pool
}
