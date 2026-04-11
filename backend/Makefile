# ScanVault Backend Makefile
# All targets assume you are in the backend/ directory.

# ── Build metadata injected via -ldflags ────────────────────────────────────
VERSION    ?= $(shell cat ../VERSION 2>/dev/null || echo dev)
COMMIT     ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo unknown)
BUILD_DATE ?= $(shell date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo unknown)
LDFLAGS    := -X github.com/nikhil/scanvault-api/internal/logger.Version=$(VERSION) \
              -X github.com/nikhil/scanvault-api/internal/logger.Commit=$(COMMIT) \
              -X github.com/nikhil/scanvault-api/internal/logger.BuildDate=$(BUILD_DATE)

# ── Postgres connection (used by migrate-* targets) ─────────────────────────
DB_URL ?= postgres://scanvault:scanvault@localhost:5432/scanvault?sslmode=disable
MIGRATIONS_DIR := db/migrations

.PHONY: run test lint migrate-up migrate-down sqlc build help

## run: start the API server (requires .env or env vars)
run:
	go run -ldflags "$(LDFLAGS)" ./cmd/api/...

## build: compile a static binary to bin/scanvault-api
build:
	@mkdir -p bin
	CGO_ENABLED=0 go build -ldflags "$(LDFLAGS)" -o bin/scanvault-api ./cmd/api/...

## test: run all tests with race detector and coverage report
test:
	CGO_ENABLED=1 go test -race -count=1 -coverprofile=coverage.out ./...
	go tool cover -func=coverage.out | tail -1

## test-short: run only unit tests (no integration / testcontainers)
test-short:
	CGO_ENABLED=1 go test -race -count=1 -short ./...

## lint: run golangci-lint (must be installed)
lint:
	golangci-lint run ./...

## migrate-up: apply all pending migrations
migrate-up:
	goose -dir $(MIGRATIONS_DIR) postgres "$(DB_URL)" up

## migrate-down: roll back the most recent migration
migrate-down:
	goose -dir $(MIGRATIONS_DIR) postgres "$(DB_URL)" down

## migrate-status: show migration status
migrate-status:
	goose -dir $(MIGRATIONS_DIR) postgres "$(DB_URL)" status

## sqlc: regenerate type-safe query code from SQL
sqlc:
	sqlc generate

## clean: remove build artifacts
clean:
	rm -rf bin/ coverage.out

## help: list available targets with descriptions
help:
	@grep -E '^## ' Makefile | sed 's/^## //' | column -t -s ':'
