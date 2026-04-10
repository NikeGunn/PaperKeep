package db_test

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	_ "github.com/lib/pq" // Postgres driver for database/sql (used by goose)
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/pressly/goose/v3"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"
)

// migrationsDir returns the absolute path to db/migrations relative to this file.
func migrationsDir(t *testing.T) string {
	t.Helper()
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("could not determine caller file path")
	}
	// file is .../backend/internal/db/migration_test.go
	// migrations are at .../backend/db/migrations
	return filepath.Join(filepath.Dir(file), "..", "..", "db", "migrations")
}

// startPostgres spins up a throwaway Postgres 16 container and returns its DSN.
func startPostgres(t *testing.T) (dsn string, cleanup func()) {
	t.Helper()
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

	host, err := ctr.Host(ctx)
	if err != nil {
		t.Fatalf("get container host: %v", err)
	}
	port, err := ctr.MappedPort(ctx, "5432")
	if err != nil {
		t.Fatalf("get container port: %v", err)
	}

	dsn = fmt.Sprintf("postgres://testuser:testpass@%s:%s/testdb?sslmode=disable", host, port.Port())
	cleanup = func() {
		if err := ctr.Terminate(ctx); err != nil {
			t.Logf("failed to terminate container: %v", err)
		}
	}
	return dsn, cleanup
}

// openPgxPool returns a connected pgxpool for query assertions.
func openPgxPool(t *testing.T, dsn string) *pgxpool.Pool {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatalf("open pgxpool: %v", err)
	}
	if err := pool.Ping(ctx); err != nil {
		t.Fatalf("ping pool: %v", err)
	}
	return pool
}

// openStdlibDB opens a database/sql.DB for goose (uses lib/pq driver).
func openStdlibDB(t *testing.T, dsn string) *sql.DB {
	t.Helper()
	db, err := sql.Open("postgres", dsn)
	if err != nil {
		t.Fatalf("open stdlib db: %v", err)
	}
	return db
}

// runMigrations applies goose migrations up or down using the embedded FS approach.
func runMigrations(t *testing.T, dsn string, direction string) {
	t.Helper()
	dir := migrationsDir(t)
	db := openStdlibDB(t, dsn)
	defer db.Close()

	goose.SetTableName("schema_migrations")
	if err := goose.SetDialect("postgres"); err != nil {
		t.Fatalf("goose set dialect: %v", err)
	}

	switch direction {
	case "up":
		if err := goose.Up(db, dir); err != nil {
			t.Fatalf("goose up: %v", err)
		}
	case "down":
		if err := goose.DownTo(db, dir, 0); err != nil {
			t.Fatalf("goose down: %v", err)
		}
	default:
		t.Fatalf("unknown direction: %s", direction)
	}
}

// tableExists checks whether a table exists in the public schema.
func tableExists(t *testing.T, pool *pgxpool.Pool, table string) bool {
	t.Helper()
	var exists bool
	err := pool.QueryRow(context.Background(),
		`SELECT EXISTS (
            SELECT FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = $1
        )`, table).Scan(&exists)
	if err != nil {
		t.Fatalf("tableExists(%q): %v", table, err)
	}
	return exists
}

// indexExists checks whether an index exists in the public schema.
func indexExists(t *testing.T, pool *pgxpool.Pool, index string) bool {
	t.Helper()
	var exists bool
	err := pool.QueryRow(context.Background(),
		`SELECT EXISTS (
            SELECT FROM pg_indexes
            WHERE schemaname = 'public' AND indexname = $1
        )`, index).Scan(&exists)
	if err != nil {
		t.Fatalf("indexExists(%q): %v", index, err)
	}
	return exists
}

// -------------------------------------------------------------------------
// Tests
// -------------------------------------------------------------------------

// TestMigration_UpCreatesAllTables verifies the Up migration creates all 3 tables
// and all required indexes.
func TestMigration_UpCreatesAllTables(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	dsn, cleanup := startPostgres(t)
	defer cleanup()

	runMigrations(t, dsn, "up")

	pool := openPgxPool(t, dsn)
	defer pool.Close()

	tables := []string{"accounts", "refresh_tokens", "email_verification_tokens"}
	for _, tbl := range tables {
		if !tableExists(t, pool, tbl) {
			t.Errorf("table %q was not created by migration", tbl)
		}
	}

	indexes := []string{
		"idx_accounts_email",
		"idx_accounts_status",
		"idx_refresh_tokens_account",
		"idx_refresh_tokens_expires",
		"idx_email_ver_tokens_account",
	}
	for _, idx := range indexes {
		if !indexExists(t, pool, idx) {
			t.Errorf("index %q was not created by migration", idx)
		}
	}
}

// TestMigration_DownDropsAllTables verifies the Down migration removes all tables.
func TestMigration_DownDropsAllTables(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	dsn, cleanup := startPostgres(t)
	defer cleanup()

	runMigrations(t, dsn, "up")
	runMigrations(t, dsn, "down")

	pool := openPgxPool(t, dsn)
	defer pool.Close()

	tables := []string{"accounts", "refresh_tokens", "email_verification_tokens"}
	for _, tbl := range tables {
		if tableExists(t, pool, tbl) {
			t.Errorf("table %q still exists after migration down", tbl)
		}
	}
}

// TestMigration_AccountStatusConstraint verifies the CHECK constraint on accounts.status.
func TestMigration_AccountStatusConstraint(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	dsn, cleanup := startPostgres(t)
	defer cleanup()

	runMigrations(t, dsn, "up")

	pool := openPgxPool(t, dsn)
	defer pool.Close()

	ctx := context.Background()

	insert := func(status string) error {
		_, err := pool.Exec(ctx, `
            INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params, status)
            VALUES ($1, 'hash', '{}', '\x00', '\x00', '{}', $2)
        `, fmt.Sprintf("test+%s@example.com", status), status)
		return err
	}

	for _, s := range []string{"active", "suspended", "pending_deletion"} {
		if err := insert(s); err != nil {
			t.Errorf("valid status %q should succeed, got: %v", s, err)
		}
	}

	for _, s := range []string{"invalid", "banned", "deleted"} {
		if err := insert(s); err == nil {
			t.Errorf("invalid status %q should fail CHECK constraint, but succeeded", s)
		}
	}
}

// TestMigration_RefreshTokenCascadeDelete verifies ON DELETE CASCADE from accounts.
func TestMigration_RefreshTokenCascadeDelete(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	dsn, cleanup := startPostgres(t)
	defer cleanup()

	runMigrations(t, dsn, "up")

	pool := openPgxPool(t, dsn)
	defer pool.Close()

	ctx := context.Background()

	var accountID int64
	err := pool.QueryRow(ctx, `
        INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params)
        VALUES ('cascade@example.com', 'hash', '{}', '\x00', '\x00', '{}')
        RETURNING id
    `).Scan(&accountID)
	if err != nil {
		t.Fatalf("insert account: %v", err)
	}

	_, err = pool.Exec(ctx, `
        INSERT INTO refresh_tokens (account_id, token_hash, expires_at)
        VALUES ($1, '\xdeadbeef', NOW() + INTERVAL '7 days')
    `, accountID)
	if err != nil {
		t.Fatalf("insert refresh token: %v", err)
	}

	_, err = pool.Exec(ctx, `DELETE FROM accounts WHERE id = $1`, accountID)
	if err != nil {
		t.Fatalf("delete account: %v", err)
	}

	var count int
	err = pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM refresh_tokens WHERE account_id = $1`, accountID,
	).Scan(&count)
	if err != nil {
		t.Fatalf("count refresh tokens: %v", err)
	}
	if count != 0 {
		t.Errorf("expected 0 refresh tokens after cascade delete, got %d", count)
	}
}

// TestMigration_EmailUniqueness verifies the UNIQUE constraint on accounts.email.
func TestMigration_EmailUniqueness(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	dsn, cleanup := startPostgres(t)
	defer cleanup()

	runMigrations(t, dsn, "up")

	pool := openPgxPool(t, dsn)
	defer pool.Close()

	ctx := context.Background()
	email := "unique@example.com"

	insert := func() error {
		_, err := pool.Exec(ctx, `
            INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params)
            VALUES ($1, 'hash', '{}', '\x00', '\x00', '{}')
        `, email)
		return err
	}

	if err := insert(); err != nil {
		t.Fatalf("first insert should succeed: %v", err)
	}
	if err := insert(); err == nil {
		t.Error("second insert with same email should fail UNIQUE constraint")
	}

	// CITEXT: different case should also fail
	_, err := pool.Exec(ctx, `
        INSERT INTO accounts (email, auth_hash, auth_params, wrapped_key, kdf_salt, kdf_params)
        VALUES ($1, 'hash', '{}', '\x00', '\x00', '{}')
    `, "UNIQUE@EXAMPLE.COM")
	if err == nil {
		t.Error("insert with different-case email should fail CITEXT UNIQUE constraint")
	}
}

// TestMigration_IdempotentUpDown verifies up→down→up works correctly.
func TestMigration_IdempotentUpDown(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	dsn, cleanup := startPostgres(t)
	defer cleanup()

	runMigrations(t, dsn, "up")
	runMigrations(t, dsn, "down")
	runMigrations(t, dsn, "up")

	pool := openPgxPool(t, dsn)
	defer pool.Close()

	if !tableExists(t, pool, "accounts") {
		t.Error("accounts table should exist after second up migration")
	}
}

func init() {
	// Disable testcontainers log spam unless debug is requested
	if os.Getenv("TESTCONTAINERS_LOG") == "" {
		os.Setenv("TESTCONTAINERS_RYUK_DISABLED", "false")
	}
}
