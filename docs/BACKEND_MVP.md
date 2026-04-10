# ScanVault — Backend MVP

**Project codename:** ScanVault API
**Language:** Go 1.23+
**Framework:** Standard library `net/http` + `chi` router (no heavy framework)
**Database:** PostgreSQL 16
**Object storage:** S3-compatible (Cloudflare R2 preferred — zero egress fees)
**Deployment:** Single binary on a VPS (Hetzner CX22 / DigitalOcean / Railway)
**Cost target:** Under $15/month up to 10,000 users

---

## 0. Why this backend exists at all

The Android app from `FRONTEND_MVP.md` is **fully functional without any backend** for Phases 1–3. Scanning, OCR, PDF export, filters, library, everything — all works offline forever. That's a deliberate choice and a core selling point against CamScanner.

The backend exists only to serve users who want:
- **Cross-device sync** (scan on phone, read on tablet)
- **Cloud backup** (if they lose their phone, their documents survive)
- **Account recovery** (not data recovery — see below)

**The single most important architectural constraint: the server must never see plaintext.** Not titles, not OCR text, not images, not filenames, not page counts, nothing. Everything is encrypted client-side with keys the server never possesses. This is called a **zero-knowledge architecture**, and it means:

- If the server is breached, attackers get encrypted blobs they cannot decrypt
- If we are subpoenaed, we can hand over encrypted blobs that nobody can decrypt
- We have no ability to read user data, even if we wanted to
- This is our #2 differentiator against CamScanner (after "no watermark, no forced login")

This constraint drives every decision in this document. If a proposed feature would require the server to see plaintext, the feature is rejected.

---

## 1. Tech Stack (locked decisions)

### Language & framework
- **Go 1.23+** — chosen because Nikhil already uses Go (PalikaBook). Single static binary, tiny memory footprint, great for a solo dev on a cheap VPS.
- **Router:** `github.com/go-chi/chi/v5` — lightweight, stdlib-compatible, no magic
- **Middleware:** stdlib + chi middlewares (logger, recoverer, timeout, CORS, RequestID)
- **NO web framework like Gin/Echo/Fiber** — chi + stdlib is plenty and easier to audit

### Database
- **PostgreSQL 16** — one instance, managed via migrations
- **pgx v5** (`github.com/jackc/pgx/v5`) — direct, no ORM
- **goose** for migrations (`github.com/pressly/goose/v3`)
- **sqlc** for type-safe query generation (`github.com/sqlc-dev/sqlc`) — we write SQL, it generates Go

No ORM. sqlc + plain SQL is dramatically safer and more maintainable than GORM for a security-critical backend.

### Object storage
- **Cloudflare R2** (primary choice) — S3-compatible API, zero egress fees, $0.015/GB/month storage
- **aws-sdk-go-v2** client pointed at R2 endpoint
- Rationale: a document scanner app could easily have users with 5 GB of scans. Zero egress means we're not paying every time they re-download on a new device.

### Authentication
- **Argon2id** for password hashing (`golang.org/x/crypto/argon2`)
  - Params: time=3, memory=64MB, threads=4, keyLen=32
- **Paseto v4** for session tokens (`github.com/aidantwoods/go-paseto`) — NOT JWT
  - Rationale: JWT's `alg:none` vulnerability history, implementation landmines, weak default algorithms. Paseto is safer by default.
- **Refresh tokens** stored in Postgres (opaque random 256-bit tokens, hashed before storage)
- **No OAuth in MVP** — email + password only. OAuth adds huge complexity and isn't worth it for Phase 1.

### Cryptography
- **Server-side crypto is minimal** because everything is E2E encrypted by the client before upload.
- **Server does need:**
  - Argon2id password hashing
  - HMAC-SHA256 for signing short-lived pre-signed R2 upload URLs
  - TLS 1.3 (via Caddy reverse proxy)
  - `crypto/rand` for token generation
- **Server does NOT do:**
  - Document encryption (client does it)
  - Key management for user data (client owns all user keys)
  - Any cryptographic operation on document content

### Rate limiting
- **ulule/limiter** with Redis backend OR in-memory for MVP
- Different limits for different endpoints (auth is stricter)

### Observability
- **Structured logging:** `log/slog` (stdlib, Go 1.21+)
- **Metrics:** Prometheus exposition via `promhttp`
- **Tracing:** OpenTelemetry with OTLP exporter (optional, defer to Phase 5)
- **Error reporting:** Sentry (`getsentry/sentry-go`) — but configured to strip all user data

### Email
- **Postmark** or **Resend** transactional API
- Used for: email verification, password reset, delete confirmation
- No marketing email in MVP

### Deployment
- **Caddy** as reverse proxy (automatic HTTPS via Let's Encrypt)
- **systemd** service unit for the Go binary
- **Docker** optional (the binary runs fine bare metal — simpler for MVP)
- **Backups:** `pg_dump` nightly to encrypted R2 bucket, 30-day retention

### CI/CD
- **GitHub Actions:** vet → staticcheck → gosec → test → build → deploy to staging on merge to `main`
- **golangci-lint** config enforcing strict rules
- **gosec** for security linting on every PR

---

## 2. Architecture

Clean, boring, monolithic. No microservices. No event buses. No Kubernetes. This is a solo-dev MVP.

```
scanvault-api/
├── cmd/
│   └── api/
│       └── main.go                # Entry point, wires everything together
├── internal/
│   ├── config/                    # Env var loading, validation
│   ├── auth/                      # Argon2id, paseto, middleware
│   ├── accounts/                  # Signup, login, password change, delete
│   ├── vault/                     # The E2E encrypted document storage
│   │   ├── handler.go             # HTTP handlers
│   │   ├── service.go             # Business logic
│   │   ├── repository.go          # Postgres access (sqlc-generated)
│   │   └── storage.go             # R2 client wrapper
│   ├── sync/                      # Manifest, conflict resolution
│   ├── email/                     # Postmark client
│   ├── ratelimit/                 # Rate limiter middleware
│   ├── security/                  # Headers, CORS, HSTS
│   └── audit/                     # Security event logging
├── db/
│   ├── migrations/                # goose migrations
│   └── queries/                   # sqlc input SQL files
├── deploy/
│   ├── Caddyfile
│   └── scanvault.service          # systemd unit
└── scripts/
    └── rotate-keys.sh
```

Every `internal/*` package exports a single `Service` or `Handler` interface. `main.go` constructs them in the right order and injects dependencies manually — no DI framework.

---

## 3. Database Schema (evolves across phases)

All tables use `BIGSERIAL` IDs internally but expose `UUID v7` (time-ordered UUIDs) externally so clients can't enumerate records.

### Phase 1 (accounts)

```sql
CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    email           CITEXT NOT NULL UNIQUE,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    auth_hash       TEXT NOT NULL,              -- Argon2id(password)
    auth_params     JSONB NOT NULL,             -- Argon2id params used (for rotation)
    wrapped_key     BYTEA NOT NULL,             -- Client's K_master, wrapped with K_encrypt
    kdf_salt        BYTEA NOT NULL,             -- Salt for client-side KDF
    kdf_params      JSONB NOT NULL,             -- Client-side Argon2id params
    status          TEXT NOT NULL DEFAULT 'active'
                      CHECK (status IN ('active', 'suspended', 'pending_deletion')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_email ON accounts (email);
CREATE INDEX idx_accounts_status ON accounts (status);

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash      BYTEA NOT NULL UNIQUE,      -- SHA-256 of the opaque token
    device_label    TEXT,                        -- "Pixel 6a", user-editable
    last_used_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_account ON refresh_tokens (account_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE email_verification_tokens (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash      BYTEA NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Phase 2 (vault)

```sql
CREATE TABLE vault_documents (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE,       -- Client-generated, not server
    account_id      BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    encrypted_metadata BYTEA NOT NULL,          -- Title, folder, tags — all encrypted
    metadata_nonce  BYTEA NOT NULL,
    version         BIGINT NOT NULL DEFAULT 1,  -- Monotonic version for conflict detection
    page_count      INT NOT NULL DEFAULT 0,     -- Server sees count, not contents
    total_size_bytes BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ                 -- Soft delete, purged after 30 days
);

CREATE INDEX idx_vault_documents_account ON vault_documents (account_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_vault_documents_account_updated ON vault_documents (account_id, updated_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE vault_pages (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE,
    document_id     BIGINT NOT NULL REFERENCES vault_documents(id) ON DELETE CASCADE,
    page_index      INT NOT NULL,
    r2_key          TEXT NOT NULL,              -- "vault/<account_uuid>/<doc_uuid>/<page_uuid>.enc"
    encrypted_size  BIGINT NOT NULL,
    checksum        BYTEA NOT NULL,             -- SHA-256 of the ciphertext
    version         BIGINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_vault_pages_document ON vault_pages (document_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_vault_pages_document_index ON vault_pages (document_id, page_index) WHERE deleted_at IS NULL;
```

### Phase 3 (quotas + audit)

```sql
CREATE TABLE account_quotas (
    account_id      BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    storage_bytes_used    BIGINT NOT NULL DEFAULT 0,
    storage_bytes_limit   BIGINT NOT NULL DEFAULT 524288000,  -- 500 MB free tier
    documents_count       INT NOT NULL DEFAULT 0,
    documents_limit       INT NOT NULL DEFAULT 1000,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_events (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    event_type      TEXT NOT NULL,  -- "login.success", "login.failure", "password.change", etc.
    ip_hash         BYTEA,          -- HMAC-SHA256 of IP, not plaintext
    user_agent      TEXT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_events_account ON audit_events (account_id, created_at DESC);
CREATE INDEX idx_audit_events_type ON audit_events (event_type, created_at DESC);
```

### Phase 4 (abuse prevention)

```sql
CREATE TABLE rate_limit_buckets (
    key             TEXT PRIMARY KEY,           -- "ip:1.2.3.4:login" or "account:uuid:upload"
    tokens          FLOAT NOT NULL,
    last_refill     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE blocked_ips (
    ip_hash         BYTEA PRIMARY KEY,
    reason          TEXT NOT NULL,
    blocked_until   TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 4. Security Requirements (apply to every phase)

These are non-negotiable. Every phase must satisfy all of these before it is considered done.

1. **TLS 1.3 only.** Caddy configured to reject TLS 1.2 and below. HSTS with `max-age=31536000; includeSubDomains; preload`.
2. **Zero-knowledge encryption of user data.** The server has no ability to decrypt any document, title, folder, or OCR text. Ever.
3. **Argon2id** for passwords (time=3, memory=64MB, threads=4, length=32, random 16-byte salt).
4. **Paseto v4 local tokens** (not JWT) for sessions. 15-minute access token lifetime, 30-day refresh token with rotation on use.
5. **Refresh token rotation**: every refresh mints a new refresh token and revokes the old one. Detecting reuse of a revoked refresh token triggers immediate logout of all sessions for that account.
6. **Rate limiting** on every endpoint. Stricter on auth endpoints (5 login attempts per IP per 15 minutes, then exponential backoff).
7. **CAPTCHA on signup and password reset** (hCaptcha or Cloudflare Turnstile — both have free tiers). Add in Phase 2 if not Phase 1.
8. **CORS** restricted to the Android app's expected origins only (plus localhost for dev). The app itself doesn't need CORS since it's not a browser, but the web admin panel will (Phase 5).
9. **SQL injection impossible** because sqlc generates parameterized queries from typed inputs. Never use `fmt.Sprintf` on SQL.
10. **No direct user input in file paths.** R2 keys are generated server-side from UUIDs, never from client-provided strings.
11. **Pre-signed upload URLs** expire in 5 minutes. Clients request a pre-signed URL, upload directly to R2, then notify the backend.
12. **Upload size limit** enforced both by pre-signed URL conditions AND by a post-upload size check. Max 50 MB per page, max 500 MB per account (configurable per quota tier).
13. **Content-type enforcement.** Pre-signed URLs require `Content-Type: application/octet-stream` — no HTML, no executables.
14. **Request body size limit** at the reverse proxy level (Caddy) — 1 MB for API calls (actual image uploads go direct to R2, bypassing our API).
15. **Secure headers middleware**: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy: ()`, `Content-Security-Policy: default-src 'none'`.
16. **Audit logging** for every security-relevant event: login success/failure, password change, new device, account deletion, rate limit trip.
17. **IP addresses are hashed** with HMAC-SHA256 (key stored in env) before being written to the audit table. We can still detect repeat offenders without storing raw IPs.
18. **Dependency scanning** via `govulncheck` in CI. Any known vulnerability in a direct dependency blocks the build.
19. **Static analysis** via `gosec`, `staticcheck`, `errcheck` in CI. Zero warnings in CI.
20. **Secrets in env vars**, never in code, never in git. `.env.example` checked in, `.env` gitignored. Production uses systemd environment files with `0600` perms.
21. **Principle of least privilege** on Postgres: the app connects as a user that has `SELECT/INSERT/UPDATE/DELETE` on its tables but no `DROP`, no `CREATE`, no superuser.
22. **Backup encryption.** Nightly `pg_dump` is piped through `age` encryption before upload to R2. The age public key is in env; the private key is held by Nikhil offline.
23. **Deletion is real.** When a user deletes their account, within 30 days: all Postgres rows hard-deleted, all R2 objects removed, all audit logs for that account anonymized. Compliance with GDPR Article 17.
24. **No PII in error messages** returned to the client. "Invalid credentials" not "user foo@bar.com does not exist." Same response time for both cases (constant-time comparison on auth hash).
25. **Idempotency keys** on state-changing endpoints (upload, delete) so a retry from the mobile client doesn't cause duplicates.

---

## 5. API Design Conventions

- Base URL: `https://api.scanvault.app/v1`
- All requests/responses are JSON except document blob uploads (which go to R2 directly as raw bytes)
- Authentication: `Authorization: Bearer <paseto-token>`
- Timestamps: RFC3339 UTC only
- IDs in responses are UUIDs (strings), never integer primary keys
- Errors: consistent envelope
  ```json
  { "error": { "code": "auth.invalid_credentials", "message": "Invalid email or password" } }
  ```
- Pagination: cursor-based (`?after=<uuid>&limit=50`), not offset
- Versioning: URL path (`/v1/`), never headers
- Idempotency: `Idempotency-Key` header required on POST/PUT/DELETE

---

## 6. The Five Phases (interconnected)

Each phase produces a **deployable increment** of the backend. At the end of every phase, the staging environment runs the full feature set up to that phase, and the matching Android app phase can integrate against it.

---

### Phase 1 — Foundations, Accounts & Authentication (Weeks 1–2)

**Goal:** A running Go service that can create accounts, log in, issue tokens, verify email, and nothing else. No vault yet. This phase has to be rock-solid because everything else depends on it.

**Frontend handshake:** None. The Android app is in Phase 1 (offline scanning). The backend exists only to be ready for Phase 4 of the Android app. But we start early because authentication is the longest tail of bugs.

#### Deliverables

1. **Project scaffold**
   - Go module initialized
   - Directory structure as in section 2
   - `Makefile` with `run`, `test`, `lint`, `migrate`, `sqlc`, `build`
   - `.golangci.yml` with strict config (errcheck, gosec, gocritic, revive, gofumpt, etc.)
   - `.env.example` with every required var documented
   - `README.md` with setup instructions
   - `goose` migrations directory wired up

2. **Config loader** (`internal/config`)
   - Reads from env vars using `github.com/caarlos0/env/v10`
   - Validates on startup — missing required vars → crash immediately with clear message
   - Separate configs for dev / staging / prod

3. **Postgres setup**
   - Migration 0001: create `accounts`, `refresh_tokens`, `email_verification_tokens`
   - sqlc generates typed query functions
   - Connection pool via pgxpool, sensible defaults (max 25 conns)
   - Health check query on startup

4. **HTTP server** (`cmd/api/main.go`)
   - chi router
   - Middleware chain: RequestID → RealIP → Logger → Recoverer → Timeout → SecureHeaders → CORS
   - Graceful shutdown on SIGTERM (drain connections, close DB pool)
   - `/health` endpoint (liveness)
   - `/ready` endpoint (readiness — checks DB)

5. **Account creation** (`POST /v1/accounts`)
   - Input: `email`, `auth_hash`, `auth_params`, `wrapped_key`, `kdf_salt`, `kdf_params`
   - **Critical:** the client sends an already-hashed password (the Argon2id hash that also serves as the auth key derivation). The server hashes it *again* with its own Argon2id params before storing. This means the server never sees the user's actual password.
   - Email validation (RFC 5322-ish, enough to block obvious garbage)
   - Uniqueness check on email
   - Generates and sends verification email via Postmark
   - Returns 201 with the account UUID
   - Idempotent: same email + same payload = same result within a 1-minute window

6. **Email verification** (`GET /v1/accounts/verify?token=...`)
   - Validates token hash against `email_verification_tokens`
   - Marks account as verified
   - Token is one-time-use, expires in 24 hours

7. **Login** (`POST /v1/sessions`)
   - Input: `email`, `auth_hash`
   - Fetch account → server-side Argon2id verify
   - Constant-time comparison
   - Same response latency for "user not found" and "wrong password" (run Argon2id even for non-existent users against a dummy hash)
   - On success: generate access token (Paseto v4 local, 15 min) + refresh token (32-byte random, hashed in DB)
   - Response includes: `access_token`, `refresh_token`, `wrapped_key`, `kdf_salt`, `kdf_params` — everything the client needs to derive its own encryption keys locally

8. **Token refresh** (`POST /v1/sessions/refresh`)
   - Input: `refresh_token`
   - Find by hash → check not revoked → check not expired
   - Rotate: revoke the old token, mint a new pair
   - Detect replay: if a revoked token is presented, revoke ALL refresh tokens for that account and force re-login

9. **Logout** (`DELETE /v1/sessions`)
   - Revokes the current refresh token
   - Access tokens are stateless and expire naturally in 15 min

10. **Get current account** (`GET /v1/accounts/me`)
    - Requires auth
    - Returns: UUID, email, email_verified, created_at, status — no secret material

11. **Change password** (`POST /v1/accounts/me/password`)
    - Input: `current_auth_hash`, `new_auth_hash`, `new_auth_params`, `new_wrapped_key`, `new_kdf_salt`, `new_kdf_params`
    - Verifies current password, updates all auth and key wrapping material atomically in a transaction
    - Revokes all refresh tokens for the account → forces re-login on all devices

12. **Auth middleware**
    - Extracts `Authorization: Bearer <token>`
    - Verifies Paseto signature + expiry
    - Loads account from DB (cached in context)
    - Rejects suspended accounts

13. **Rate limiting** (in-memory for Phase 1, Redis in Phase 3)
    - `/v1/sessions` (login): 5 attempts per IP per 15 min
    - `/v1/accounts` (signup): 3 per IP per hour
    - All other endpoints: 100 per account per minute

14. **Security headers middleware**
    - All the headers in section 4, item 15
    - HSTS header added by Caddy, not the app (belt and braces)

15. **Structured logging**
    - `slog` with JSON handler
    - Every request logged with request_id, method, path, status, duration
    - Never log auth_hash, wrapped_key, or any field that could be secret material

16. **Tests**
    - Unit tests for `auth`, `accounts`
    - Integration tests using a real Postgres (via `testcontainers-go` or docker-compose)
    - Test coverage target: 80% for `internal/auth` and `internal/accounts`

17. **Local dev environment**
    - `docker-compose.yml` with Postgres
    - `make dev` brings everything up

18. **Deploy to staging**
    - Hetzner/DO VPS provisioned
    - Caddy installed and configured for automatic HTTPS
    - systemd unit
    - Postgres installed locally on the same VPS (cheap MVP setup)
    - Nightly `pg_dump` cron job + age encryption + R2 upload

#### Acceptance criteria

- [ ] `curl` can create an account, verify the email (via link), log in, refresh the token, and log out
- [ ] Changing the password invalidates all existing sessions across all devices
- [ ] Wrong password returns 401 in constant time (measured with `hyperfine`)
- [ ] 6 login attempts in 15 min from one IP returns 429
- [ ] All tests pass in CI
- [ ] `gosec` and `govulncheck` report zero issues
- [ ] Staging environment is live on HTTPS with a real domain
- [ ] `curl https://api-staging.scanvault.app/health` returns 200

---

### Phase 2 — Vault Schema & Encrypted Blob Upload (Weeks 3–4)

**Goal:** The server can accept, store, list, and serve E2E-encrypted document blobs. The client is still fully responsible for all encryption. This phase is mostly about correctness of the upload/download plumbing and R2 integration.

**Frontend handshake:** None yet. Android app is in Phases 2–3 (offline library). Backend continues to build the sync foundation in parallel.

#### Deliverables

1. **R2 integration** (`internal/vault/storage.go`)
   - aws-sdk-go-v2 with R2 endpoint and credentials from env
   - Functions: `GeneratePresignedPutURL`, `GeneratePresignedGetURL`, `DeleteObject`, `HeadObject`
   - Key format: `vault/{account_uuid}/{doc_uuid}/{page_uuid}.enc`
   - Pre-signed URLs expire in 5 minutes
   - Pre-signed PUT URLs enforce max content-length via R2 conditions

2. **Vault migrations** (migration 0002)
   - Creates `vault_documents` and `vault_pages` tables as in section 3

3. **Create document** (`POST /v1/vault/documents`)
   - Input: `uuid` (client-generated), `encrypted_metadata` (base64), `metadata_nonce` (base64), `page_count`
   - Validates the UUID is v7 and not already used by this account
   - Creates a row with `version = 1`
   - Returns the created document

4. **List documents** (`GET /v1/vault/documents?after=<uuid>&limit=50`)
   - Cursor pagination
   - Returns only documents owned by the authenticated account
   - Excludes soft-deleted
   - Returns: `uuid`, `encrypted_metadata`, `metadata_nonce`, `version`, `page_count`, `total_size_bytes`, `updated_at`
   - Server never sees or processes the metadata — it's an opaque blob

5. **Get document** (`GET /v1/vault/documents/{uuid}`)
   - Returns the document + an array of page manifests (uuid, page_index, encrypted_size, checksum, version)
   - Does NOT return the actual image data — client must request pre-signed GET URLs separately

6. **Update document metadata** (`PUT /v1/vault/documents/{uuid}`)
   - Input: `encrypted_metadata`, `metadata_nonce`, `expected_version`
   - Optimistic concurrency control: if `expected_version` doesn't match the current version, return 409 Conflict with the current version and metadata
   - On success: increments version, updates `updated_at`

7. **Delete document** (`DELETE /v1/vault/documents/{uuid}`)
   - Soft delete: set `deleted_at = NOW()`
   - A background job purges soft-deleted rows and their R2 objects after 30 days
   - Client can immediately create a new document with the same UUID (rare, but supported)

8. **Request page upload URL** (`POST /v1/vault/documents/{doc_uuid}/pages/upload-url`)
   - Input: `page_uuid`, `page_index`, `encrypted_size`, `checksum`
   - Validates: size < 50 MB, account is within quota, page_index not already taken
   - Creates a "pending" row in `vault_pages`
   - Generates a pre-signed R2 PUT URL (5 min expiry)
   - Returns the URL and the pending page UUID

9. **Confirm page upload** (`POST /v1/vault/documents/{doc_uuid}/pages/{page_uuid}/confirm`)
   - Client calls this after successfully uploading to R2
   - Server does a HeadObject on R2 to verify the blob exists and matches the expected size
   - Marks the page row as confirmed and updates `total_size_bytes` on the document
   - If HeadObject fails: delete the row, return error

10. **Get page download URL** (`GET /v1/vault/documents/{doc_uuid}/pages/{page_uuid}/download-url`)
    - Generates a pre-signed R2 GET URL (5 min expiry)
    - Returns the URL
    - Client downloads directly from R2

11. **Delete page** (`DELETE /v1/vault/documents/{doc_uuid}/pages/{page_uuid}`)
    - Soft-delete the row
    - Background purge job handles R2 deletion later

12. **Background purge job** (goroutine with ticker)
    - Runs every hour
    - Finds rows where `deleted_at < NOW() - 30 days`
    - Deletes from R2 first, then hard-deletes from Postgres
    - Logs all actions to audit table

13. **Quota enforcement** (`internal/vault/service.go`)
    - Every upload URL request checks `account_quotas`
    - If over limit: 402 Payment Required (even though we don't sell anything yet — semantically correct)
    - Default quota: 500 MB, 1000 documents

14. **Sync manifest endpoint** (`GET /v1/vault/manifest?since=<iso-timestamp>`)
    - Returns a compact list of all documents changed since the given timestamp
    - Format: `{ uuid, version, updated_at, deleted }` per document
    - Used by the client to figure out what to pull
    - Cursor pagination if the result is large

15. **Storage metrics** (internal, not exposed)
    - Prometheus gauges: total accounts, total documents, total bytes stored, total R2 API calls
    - Counter: uploads per minute, downloads per minute

#### Acceptance criteria

- [ ] A scripted E2E test: create account → log in → create document → get upload URL → PUT encrypted blob to R2 → confirm → list documents → download URL → GET blob from R2 → delete document → verify R2 cleanup
- [ ] Quota is enforced: the 1001st document creation returns 402
- [ ] Pre-signed URLs cannot be reused after expiry (verified)
- [ ] Optimistic concurrency: two simultaneous updates to the same document → one succeeds, one gets 409
- [ ] Soft-deleted documents disappear from `GET /v1/vault/documents` immediately
- [ ] Background purge job actually purges (tested with a 5-second retention override in dev)
- [ ] All tests pass, gosec clean

---

### Phase 3 — Sync Engine, Conflict Resolution & Abuse Prevention (Weeks 5–6)

**Goal:** Make sync robust. Handle conflicts gracefully. Prevent abuse. This is where the backend becomes production-ready for real users.

**Frontend handshake:** Near the end of Phase 3, the Android app starts Phase 4 (optional sync). The first real integration testing happens.

#### Deliverables

1. **Conflict resolution protocol**
   - Documented as a spec in the repo
   - Rule: last-write-wins based on server timestamp, with both versions preserved for the user to reconcile
   - When a 409 happens, client downloads the server version, creates a local conflict copy, and lets the user pick
   - Server helps by retaining the previous encrypted metadata for 7 days in a separate table

2. **Conflict backup table** (migration 0003)
   ```sql
   CREATE TABLE vault_metadata_history (
       id BIGSERIAL PRIMARY KEY,
       document_id BIGINT NOT NULL REFERENCES vault_documents(id) ON DELETE CASCADE,
       version BIGINT NOT NULL,
       encrypted_metadata BYTEA NOT NULL,
       metadata_nonce BYTEA NOT NULL,
       replaced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
       expires_at TIMESTAMPTZ NOT NULL
   );
   ```
   - Every metadata update writes the old version here with a 7-day expiry
   - Background job purges expired entries

3. **Batch operations** (`POST /v1/vault/batch`)
   - Clients can submit a batch of create/update/delete operations in a single request
   - Executed in a single Postgres transaction
   - Returns per-operation results (some may succeed while others fail with 409)
   - Max 100 operations per batch
   - Idempotency key required

4. **Redis rate limiter**
   - Replace in-memory limiter with Redis-backed (`go-redis/redis/v9`)
   - Token bucket algorithm
   - Graceful fallback to allow-all if Redis is unreachable (log loudly)
   - Redis runs on the same VPS for MVP (cheap, upgrade later)

5. **CAPTCHA on signup** (hCaptcha or Turnstile)
   - Client must submit a CAPTCHA token with signup
   - Backend verifies with the CAPTCHA provider
   - Skippable in dev mode via env flag

6. **Account lockout after brute force**
   - 10 failed logins in 1 hour → account is locked for 1 hour
   - Notification email sent
   - Lockout reset on successful password reset

7. **Password reset flow**
   - `POST /v1/accounts/reset-request` → sends an email with a time-limited (1 hour) token
   - `POST /v1/accounts/reset-confirm` → client provides new auth_hash and new wrapped_key
   - **Critical:** password reset does NOT recover the user's data. It resets the account access, but since the old password was the only thing that could unwrap `K_master`, the user loses all existing encrypted data. The UI must warn about this explicitly. This is a fundamental consequence of zero-knowledge — we cannot recover what we cannot read.
   - Alternative flow: if the user remembers their old password, they can log in and do a normal password change (which preserves data)

8. **Audit event logging**
   - Every security-relevant action writes to `audit_events`
   - Events: `account.created`, `account.login.success`, `account.login.failure`, `account.locked`, `account.password.changed`, `account.password.reset`, `account.deleted`, `vault.document.created`, `vault.document.deleted`, `quota.exceeded`, `rate_limit.exceeded`
   - Rolling 90-day retention (older events purged)

9. **Account activity endpoint** (`GET /v1/accounts/me/activity`)
   - Returns recent audit events for the current account
   - User can see: active sessions, recent logins, device labels
   - Transparent to the user — builds trust

10. **Session listing & revocation** (`GET /v1/accounts/me/sessions`, `DELETE /v1/accounts/me/sessions/{id}`)
    - User sees all active refresh tokens
    - Can revoke any individually ("log out this device")
    - Or revoke all ("log out everywhere")

11. **Delete account flow**
    - `POST /v1/accounts/me/delete-request` → sends confirmation email
    - User clicks link → `POST /v1/accounts/me/delete-confirm` with token
    - Account marked `status = 'pending_deletion'`
    - After 7-day grace period (so users can undo accidental deletions), a background job actually purges: Postgres rows, R2 objects, audit logs
    - Compliance with GDPR Article 17

12. **Abuse signals**
    - New account from a new IP + large initial upload → rate limit more aggressively
    - Many failed logins from the same IP → temporarily block the IP (`blocked_ips` table)
    - Logging of all abuse decisions for manual review

#### Acceptance criteria

- [ ] Concurrent clients can sync to the same account without corrupting data
- [ ] Password reset correctly wipes the vault (no decryptable data remains)
- [ ] User can log out a specific device and that device's refresh token immediately fails
- [ ] Account deletion actually removes all data within the grace period
- [ ] Rate limiter survives Redis going down (degrades gracefully)
- [ ] Audit log contains entries for all tested security events
- [ ] CAPTCHA blocks automated signup attempts
- [ ] Integration test with the Android app Phase 4 client succeeds end-to-end on staging

---

### Phase 4 — Observability, Performance & Backups (Weeks 7–8)

**Goal:** Make it possible to operate the service reliably. Know when something is broken before users do. Scale to 10k users on a single VPS. Never lose data.

**Frontend handshake:** Android app is in Phase 4 (sync). Backend must be stable enough for real user testing by end of this phase.

#### Deliverables

1. **Prometheus metrics exposition** (`/metrics` endpoint, internal-only)
   - HTTP request count, duration histogram, error rate by endpoint
   - DB connection pool utilization
   - R2 API call count and error rate
   - Auth events (login success/failure counters)
   - Quota utilization gauges
   - Custom business metrics: new signups per hour, active accounts (DAU via refresh token activity)

2. **Grafana dashboards**
   - Self-hosted Grafana on the same VPS
   - Panels: request rate, p50/p95/p99 latency, error rate, DB pool, R2 errors, active accounts, storage used
   - Alerts: error rate > 1%, p99 latency > 2s, DB connections > 80%, disk > 80%

3. **Structured error handling**
   - Consistent error envelope enforced by middleware
   - Errors logged with context (request_id, account_id if available, stack trace)
   - User-facing error messages are generic; detailed errors only in server logs
   - Sentry integration (`getsentry/sentry-go`) with aggressive PII scrubbing (email, IP, tokens all stripped)

4. **Health checks (deep)**
   - `/health` → basic liveness (just returns 200)
   - `/ready` → checks: Postgres reachable, R2 reachable, Redis reachable (if configured)
   - Returns individual component status in the response body

5. **Database performance**
   - `EXPLAIN ANALYZE` all hot queries, add missing indexes
   - Add partial indexes for `WHERE deleted_at IS NULL`
   - Connection pool tuning: measure under load
   - Slow query logging enabled (> 100ms)
   - `pgBadger` weekly report script

6. **Caching layer**
   - In-process LRU cache (`hashicorp/golang-lru/v2`) for:
     - Account lookups by ID (during auth, hit on every request)
     - Sync manifest results (short TTL, 5 seconds)
   - Cache invalidation on writes

7. **Backup strategy**
   - **Postgres:** nightly `pg_dump` → pipe through `age` encryption → upload to R2 → 30-day retention
   - **Postgres WAL archiving** to R2 for point-in-time recovery (optional, if we can afford the disk)
   - **R2 vault data:** enable R2 versioning → provides 30-day soft-delete safety net
   - **Restore drill:** monthly scripted test that restores the backup to a staging DB and verifies row counts

8. **Load testing**
   - `k6` or `vegeta` scripts simulating realistic load:
     - 100 concurrent users listing documents
     - 50 concurrent uploads
     - Auth churn: 10 logins per second
   - Baseline metrics recorded
   - Anything that falls over under 500 concurrent users is fixed before launch

9. **DoS protection**
   - Caddy has a request rate limit layer in front of the app (100 req/sec per IP)
   - Cloudflare in front of Caddy (free tier) for DDoS absorption and WAF

10. **Log aggregation**
    - `slog` writes JSON to stdout
    - `systemd-journald` captures it
    - For MVP: read via `journalctl`
    - Upgrade path (Phase 5 if needed): Loki + Grafana

11. **Secret rotation procedure**
    - Document how to rotate: Paseto signing key, DB password, R2 credentials, IP hash HMAC key
    - `scripts/rotate-keys.sh` does the mechanical steps
    - IP hash rotation is tricky (invalidates old audit correlations) — document the tradeoff

12. **Deployment automation**
    - GitHub Actions on tag push: build binary → SCP to staging → run migrations → reload systemd → smoke test
    - Production deployment is manual (`make deploy-prod`) for safety — requires explicit confirmation
    - Rollback procedure documented: keep the previous binary, `systemctl start scanvault@previous`

13. **Runbook**
    - `RUNBOOK.md` in the repo
    - How to: restart the service, check logs, restore from backup, rotate secrets, handle a compromised account, respond to an abuse report

#### Acceptance criteria

- [ ] Grafana dashboard shows live metrics from staging
- [ ] Load test: 500 concurrent users, p99 latency < 1 second, error rate < 0.1%
- [ ] Backup restore drill passes: a fresh staging DB rebuilt from last night's backup and all data present
- [ ] All alerts have been triggered at least once in staging to verify they work
- [ ] Runbook is complete enough that someone else could recover the service with it

---

### Phase 5 — Production Hardening & Launch (Weeks 9–11)

**Goal:** Ship to production. Survive the first 1000 users. Have the operational muscle to scale without falling over.

**Frontend handshake:** Android app Phase 5 (polish + launch). Backend + frontend ship together.

#### Deliverables

1. **Production environment**
   - Dedicated VPS (Hetzner CX22 or CX32 — 8 EUR or 15 EUR/month)
   - Separate from staging
   - Postgres 16 with tuned `postgresql.conf` (`shared_buffers = 25% of RAM`, `effective_cache_size = 75% of RAM`, `work_mem = 16MB`)
   - Separate R2 bucket for production (never share with staging)
   - Separate Postmark server for production email
   - Separate Sentry project
   - Real domain with DNS configured
   - Caddy serving HTTPS with Let's Encrypt auto-renewal

2. **SSL/TLS configuration**
   - TLS 1.3 only (reject 1.2)
   - Strong cipher suites only
   - OCSP stapling
   - HSTS preload submitted
   - Verified with `ssllabs.com` — target A+ grade

3. **Penetration testing**
   - Self-run `nuclei` and `nikto` against the staging environment
   - Manual pentest checklist:
     - Can I register an account with an existing email? (should fail cleanly)
     - Can I log in with a canceled refresh token? (should fail)
     - Can I access another account's documents by UUID guessing? (should fail with 404, not 403)
     - Can I upload a file larger than the limit? (should fail at R2 level)
     - Can I bypass rate limiting by rotating IPs? (should still hit account-level limits)
     - SQL injection attempts on every input field (should be impossible via sqlc, but verify)
     - Can I cause the server to leak memory via pathological inputs? (fuzz-test the JSON decoder)
   - Fix every finding before launch

4. **Privacy policy & ToS**
   - Hosted at `/privacy` and `/terms` (static files served by Caddy)
   - Reviewed for GDPR, CCPA basics
   - "Data safety" section matches what the Android app declares in Play Console

5. **DMCA / abuse contact**
   - `abuse@scanvault.app` email forwarding set up
   - Documented response process
   - **Important:** because of E2E encryption, we literally cannot review reported content. Our only remediation is account suspension based on pattern-of-use signals (quota abuse, known-bad IPs, etc.), and we document this in the ToS.

6. **Cost controls**
   - R2 API call monitoring (R2 charges per operation, not egress)
   - Postgres disk usage alerts at 60%, 80%, 90%
   - Postmark sending limits configured
   - **Kill switch:** env var `MAX_SIGNUPS_PER_DAY` that automatically disables new signups if hit. Prevents runaway costs from an abuse wave.

7. **Account export**
   - `POST /v1/accounts/me/export` → queues a background job
   - Job packages: encrypted metadata JSON + all encrypted blobs as a tarball
   - Uploads to a user-specific R2 location with a pre-signed GET URL valid for 48 hours
   - Emails the user the link when ready
   - GDPR Article 20 (data portability) compliance

8. **Admin CLI** (`cmd/admin`)
   - Separate binary, not exposed via HTTP
   - Commands: `suspend-account`, `unlock-account`, `delete-account`, `quota-set`, `audit-search`
   - Requires SSH access to the server — no web admin panel in MVP (reduces attack surface)
   - Every action logged to audit table with a special `admin.*` event type

9. **Incident response playbook**
   - Documented procedures for:
     - DB corruption: restore from backup, assess data loss, notify affected users
     - Compromised secret: rotate, force re-login of all users, audit what was exposed
     - R2 outage: degrade gracefully, queue operations, resume when R2 returns
     - Mass abuse signup: enable the kill switch, investigate pattern, patch, resume
     - Security disclosure: triage, fix, publish advisory, credit the reporter

10. **Final security review**
    - Full `gosec` and `govulncheck` pass
    - `go mod audit` clean
    - Dependency tree reviewed — every dependency justified
    - Manual code review of all auth-related code
    - Verify no `TODO` or `FIXME` in security-critical paths

11. **Monitoring alerts (tuned)**
    - Alerts go to Nikhil's personal Telegram bot (free, reliable)
    - Critical: service down, DB down, disk full, 5xx rate > 1%
    - Warning: high latency, high error rate, high signup rate, quota near limit
    - Info: daily summary at 09:00 Nepal time

12. **Launch checklist**
    - [ ] Production environment fully provisioned
    - [ ] DNS cut over
    - [ ] SSL verified (ssllabs A+)
    - [ ] Backups running nightly, restore drill passed
    - [ ] Monitoring + alerts tested
    - [ ] Privacy policy and ToS published
    - [ ] Abuse contact active
    - [ ] Kill switch tested
    - [ ] Load test passed on production hardware
    - [ ] Admin CLI works
    - [ ] Incident playbook reviewed
    - [ ] Staging environment remains intact for post-launch testing

#### Acceptance criteria

- [ ] All launch checklist items checked off
- [ ] Production runs for 48 hours in shadow mode (staff-only accounts) with zero alerts
- [ ] SSL Labs grade A+
- [ ] Penetration test findings all resolved
- [ ] The Android app Phase 4/5 sync flow works end-to-end against production
- [ ] Backups verified twice (two consecutive nights, both restored successfully)
- [ ] First 10 real external testers successfully sync documents across two devices

---

## 7. Phase Interconnection Map

```
BACKEND Phase 1 (accounts + auth)
   │
   │   → API live on staging with /v1/accounts, /v1/sessions
   │   → FRONTEND is still in Phase 1 (no integration yet)
   │
   ▼
BACKEND Phase 2 (vault + R2)
   │
   │   → Encrypted blob upload/download working
   │   → FRONTEND is in Phase 2-3 (still no integration)
   │
   ▼
BACKEND Phase 3 (sync + abuse prevention)   ←── FIRST INTEGRATION POINT
   │                                            FRONTEND Phase 4 starts integrating
   │   → Full sync protocol frozen
   │   → Contract testing between FE and BE
   │
   ▼
BACKEND Phase 4 (observability + backups)   ←── FRONTEND Phase 4 deep testing
   │
   │   → Metrics, alerts, backups, load tested
   │   → Real user flows exercised on staging
   │
   ▼
BACKEND Phase 5 (production hardening)      ←── FRONTEND Phase 5 launch
   │
   │   → Prod environment live
   │   → Ship both together
```

**Key discipline:** the API contract is frozen at the end of Backend Phase 3. After that, only additive changes. Breaking changes require a `/v2/` path.

---

## 8. Cost Breakdown (realistic MVP, first 1000 users)

| Item                          | Cost/month    |
|-------------------------------|---------------|
| Hetzner CX22 VPS (staging)    | €4            |
| Hetzner CX32 VPS (prod)       | €8            |
| Cloudflare R2 storage (50 GB) | $0.75         |
| Cloudflare R2 operations      | ~$1           |
| Postmark (starter)            | $15           |
| Domain name                   | $12/year      |
| Sentry (dev tier)             | $0            |
| Grafana self-hosted           | $0            |
| Cloudflare DNS + CDN          | $0            |
| **Total**                     | **~$30/mo**   |

Well within a student-founder budget. Scales to about 10k active users on this setup before needing a bigger DB.

---

## 9. What We Deliberately Do NOT Build in MVP

- Admin web panel (use SSH + CLI instead)
- Team / organization accounts
- Payment processing (ads-only monetization in MVP; subscriptions are a v2 feature)
- Social features (sharing between users)
- Server-side OCR (stays on-device — private and free)
- Server-side PDF processing (same)
- Real-time collaboration
- WebSocket live sync (polling via manifest is fine for MVP)
- Multi-region deployment
- Read replicas
- Kubernetes / autoscaling
- OAuth / social login
- SMS / phone auth
- Hardware security modules (rely on Cloudflare + Let's Encrypt for key management)
- Third-party SSO (Google, Apple)

---

## 10. Definition of Done (every phase)

A phase is not done until:

1. All acceptance criteria in this doc pass, measured on staging
2. `golangci-lint`, `gosec`, `govulncheck` all clean in CI
3. Unit test coverage ≥ 75% on `internal/*`
4. Integration test coverage for every HTTP endpoint
5. Migrations run cleanly forward AND backward
6. The matching frontend phase can run against this backend without errors
7. Runbook updated with any new operational procedures
8. At least one backup + restore drill passed in this phase
9. Staging has been running the new code for 48 hours with no errors
