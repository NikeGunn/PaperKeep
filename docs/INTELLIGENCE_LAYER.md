# ScanVault — Python Intelligence Layer

**Purpose:** A modular, Python-based AI/ML service layer that augments the Go backend with server-side intelligence capabilities — OCR post-processing, document enhancement, structured data extraction, summarization, and future LLM integration — without touching or redesigning any existing Go or Kotlin code.

---

## 0. Gap Analysis — Why This Layer Exists

The current architecture has a deliberate split:

| Capability | Where it runs today | Limitation |
|---|---|---|
| OCR | On-device (ML Kit) | Limited to what ML Kit supports; no post-processing, no layout analysis, no table extraction |
| Edge detection | On-device (OpenCV) | Good for real-time, but server-side can do heavier processing on uploaded images |
| Document enhancement | On-device (OpenCV + TFLite) | Constrained by phone GPU/CPU; no access to large models |
| Summarization | Nowhere | Not in any phase — users can't get a summary of a 20-page contract |
| Structured extraction | Partial (receipt regex) | Only basic regex on receipts; no invoice parsing, no form field extraction |
| Document classification | On-device (TFLite) | Small model, limited categories |
| LLM "ask your doc" | Explicitly deferred to v2 | No infrastructure to support it |
| Batch processing | Nowhere | No way to re-OCR 500 documents with a better model |
| Multi-language OCR enhancement | On-device only | Some scripts (Tibetan, ancient Devanagari) aren't well-served by ML Kit |

**The intelligence layer fills every row in this table** with a Python service that the Go backend can call asynchronously. The Android app remains fully functional offline — the intelligence layer is a **cloud enhancement**, never a dependency.

### Design Principle: Additive, Never Required

- The Android app works perfectly without the intelligence layer (Phases 1-3 are 100% offline)
- The Go backend works perfectly without it (sync, auth, vault are self-contained)
- The intelligence layer is an **optional accelerator** that users who have accounts and sync enabled can benefit from
- If the intelligence layer is down, the system degrades gracefully — users just don't get server-side AI features

---

## 1. Architecture Overview

```
                                    ┌─────────────────────────────────┐
                                    │        Android App (Kotlin)      │
                                    │  On-device: ML Kit, OpenCV,      │
                                    │  TFLite (unchanged)              │
                                    └──────────────┬──────────────────┘
                                                   │ HTTPS (Ktor)
                                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        Go Backend (chi + pgx)                        │
│                                                                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ │
│  │ accounts │ │  vault   │ │   sync   │ │  audit   │ │intelligence│ │
│  │          │ │          │ │          │ │          │ │  proxy     │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └─────┬─────┘ │
│                                                              │       │
└──────────────────────────────────────────────────────────────┼───────┘
                                                               │
                              ┌─────────────────────────────────┘
                              │  Redis (task queue)
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   Python Intelligence Layer (FastAPI)                 │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │  OCR Service  │  │  Vision Svc  │  │   AI Service  │              │
│  │              │  │              │  │              │               │
│  │ - Tesseract  │  │ - OpenCV     │  │ - Summarize  │              │
│  │ - PaddleOCR  │  │ - Enhancement│  │ - Extract    │              │
│  │ - Layout     │  │ - Dewarp     │  │ - Classify   │              │
│  │   analysis   │  │ - Cleanup    │  │ - LLM proxy  │              │
│  │ - Table      │  │ - Super-res  │  │ - Embeddings │              │
│  │   extraction │  │              │  │              │               │
│  └──────────────┘  └──────────────┘  └──────────────┘               │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐                                 │
│  │  Worker Pool │  │  Model Store │                                 │
│  │  (Celery/    │  │  (cached     │                                 │
│  │   ARQ)       │  │   on disk)   │                                 │
│  └──────────────┘  └──────────────┘                                 │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │   Object Storage  │
                    │   (Cloudflare R2) │
                    └──────────────────┘
```

### Key Design Decisions

1. **Go remains the API gateway.** The Android app never talks to Python directly. Go's `internal/intelligence` package acts as a thin proxy — it enqueues jobs via Redis and exposes status endpoints.

2. **Async by default.** Document processing takes seconds to minutes. The Go backend publishes a task to Redis, returns a `202 Accepted` with a `task_id`, and the client polls or receives a push notification when done.

3. **Sync for lightweight ops.** Classification (< 100ms) and simple text extraction use direct HTTP/gRPC calls from Go to Python for immediate response.

4. **Zero-knowledge preserved.** The intelligence layer processes documents that the **user has explicitly opted into server-side processing for**. The Go backend decrypts nothing — the client sends a separate, purpose-specific encrypted payload OR opts into server-side processing by uploading a plaintext copy to a temporary processing bucket. The user's vault remains E2E encrypted.

5. **Stateless Python services.** No database in Python. State lives in Redis (task queue) and Postgres (via Go). Python reads from R2, processes, writes results back to R2 or returns via Redis.

---

## 2. Service Boundaries & Responsibilities

### 2.1 Go Backend — `internal/intelligence` (new package)

**Responsibility:** Proxy between the Android client and the Python layer. Handles auth, quota enforcement, task lifecycle, and result storage.

```
internal/intelligence/
├── handler.go       # HTTP handlers: submit job, poll status, get result
├── service.go       # Business logic: validate, enqueue, quota check
├── queue.go         # Redis publisher (enqueue tasks)
├── models.go        # Task types, status enums, request/response structs
└── repository.go    # sqlc queries for intelligence_tasks table
```

**New DB table (migration):**

```sql
CREATE TABLE intelligence_tasks (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    account_id      BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    task_type       TEXT NOT NULL CHECK (task_type IN (
        'ocr.enhance', 'ocr.table_extract', 'ocr.layout_analysis',
        'vision.enhance', 'vision.dewarp', 'vision.super_resolve', 'vision.cleanup',
        'ai.summarize', 'ai.extract_fields', 'ai.classify', 'ai.embed'
    )),
    status          TEXT NOT NULL DEFAULT 'pending' CHECK (status IN (
        'pending', 'processing', 'completed', 'failed', 'cancelled'
    )),
    input_r2_key    TEXT NOT NULL,           -- Where the input blob lives in R2
    output_r2_key   TEXT,                    -- Where the result blob lives in R2
    result_metadata JSONB,                   -- Structured result (extracted fields, etc.)
    error_message   TEXT,
    priority        INT NOT NULL DEFAULT 5,  -- 1 = highest, 10 = lowest
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ NOT NULL     -- Auto-cleanup after 24h
);

CREATE INDEX idx_intelligence_tasks_account ON intelligence_tasks (account_id, created_at DESC);
CREATE INDEX idx_intelligence_tasks_status ON intelligence_tasks (status) WHERE status IN ('pending', 'processing');
```

### 2.2 Python OCR Service

**Responsibility:** Advanced OCR beyond what ML Kit provides on-device.

| Capability | Library | Use Case |
|---|---|---|
| High-accuracy OCR | PaddleOCR | Better than ML Kit for complex layouts, tables, mixed scripts |
| Layout analysis | LayoutParser + Detectron2 | Detect headers, paragraphs, tables, figures, captions |
| Table extraction | PaddleOCR table model / img2table | Extract tables as structured JSON or CSV |
| Handwriting OCR | TrOCR (HuggingFace) | Enhanced handwriting recognition |
| Multi-script OCR | PaddleOCR multi-lang | Tibetan, ancient Devanagari, rare scripts ML Kit doesn't cover |

### 2.3 Python Vision Service

**Responsibility:** Heavy image processing that's too expensive for a phone.

| Capability | Library | Use Case |
|---|---|---|
| Document enhancement | OpenCV + custom pipeline | Better than on-device: denoise, sharpen, balance |
| Super-resolution | Real-ESRGAN (lightweight) | Upscale blurry captures to readable quality |
| Advanced dewarp | DewarpNet / DocTr | Better book dewarp than the on-device TFLite model |
| Background removal | rembg (U2-Net) | Clean document extraction from messy backgrounds |
| Stain/shadow removal | OpenCV inpainting pipeline | Remove coffee stains, finger shadows, fold marks |
| Image restoration | NAFNet | Fix damaged/old document scans |

### 2.4 Python AI Service

**Responsibility:** Higher-level intelligence — understanding document content.

| Capability | Library/Model | Use Case |
|---|---|---|
| Summarization | Hugging Face (BART/T5 small) or LiteLLM | "Summarize this 20-page contract in 3 bullet points" |
| Field extraction | Donut / LayoutLMv3 | Extract invoice fields, receipt totals, form data as structured JSON |
| Document classification | LayoutLMv3 fine-tuned | More categories than on-device: invoice, contract, letter, medical record, tax form, etc. |
| Semantic search embeddings | sentence-transformers | Generate embeddings for vector search across documents |
| LLM proxy | LiteLLM | Future "ask your document" via Claude/GPT, with the user's decrypted text sent client-side |

---

## 3. API Contracts — Go <-> Python

### 3.1 Async Path (Redis Queue)

**Queue name:** `scanvault:intelligence:tasks`

**Message format (JSON in Redis):**

```json
{
  "task_id": "uuid-v7",
  "task_type": "ocr.enhance",
  "account_id": "uuid",
  "input": {
    "r2_key": "processing/<account_uuid>/<task_uuid>/input.bin",
    "content_type": "image/jpeg",
    "params": {
      "languages": ["en", "ne"],
      "output_format": "structured_json",
      "enable_table_detection": true
    }
  },
  "callback": {
    "type": "redis_pubsub",
    "channel": "scanvault:intelligence:results"
  },
  "created_at": "2026-04-10T12:00:00Z",
  "priority": 5
}
```

**Result message (published to Redis result channel):**

```json
{
  "task_id": "uuid-v7",
  "status": "completed",
  "output": {
    "r2_key": "processing/<account_uuid>/<task_uuid>/output.json",
    "metadata": {
      "ocr_text": "... (truncated, full in R2)",
      "confidence": 0.94,
      "language_detected": "en",
      "tables_found": 2,
      "processing_time_ms": 1842
    }
  },
  "completed_at": "2026-04-10T12:00:02Z"
}
```

### 3.2 Sync Path (REST — for lightweight operations)

**Base URL:** `http://intelligence:8100/api/v1` (internal, never exposed to internet)

#### POST /api/v1/classify

Classify a document image. Fast (< 200ms).

```
Request:
  Content-Type: multipart/form-data
  Body: image file (JPEG/PNG, max 10 MB)

Response 200:
{
  "document_type": "receipt",
  "confidence": 0.92,
  "sub_type": "restaurant_receipt",
  "suggested_filter": "bw_adaptive",
  "suggested_aspect": "tall",
  "processing_time_ms": 87
}
```

#### POST /api/v1/extract-fields

Extract structured fields from a classified document. Medium (< 2s).

```
Request:
{
  "image_b64": "...",
  "document_type": "receipt",
  "fields": ["merchant", "date", "total", "currency", "items"]
}

Response 200:
{
  "fields": {
    "merchant": {"value": "Bhat-Bhateni", "confidence": 0.95, "bbox": [x,y,w,h]},
    "date": {"value": "2026-04-10", "confidence": 0.88, "bbox": [x,y,w,h]},
    "total": {"value": "2450.00", "confidence": 0.97, "bbox": [x,y,w,h]},
    "currency": {"value": "NPR", "confidence": 0.90},
    "items": [
      {"name": "Rice 5kg", "amount": "800.00", "confidence": 0.85}
    ]
  },
  "processing_time_ms": 1240
}
```

#### POST /api/v1/ocr/enhanced

Enhanced OCR with layout analysis. Async-recommended for large docs, but sync supported for single pages.

```
Request:
  Content-Type: multipart/form-data
  Body: image file
  Query: ?languages=en,ne&detect_tables=true&detect_layout=true

Response 200:
{
  "text": "Full extracted text...",
  "confidence": 0.94,
  "language": "en",
  "layout": {
    "blocks": [
      {"type": "title", "text": "Invoice #1234", "bbox": [x,y,w,h], "confidence": 0.97},
      {"type": "paragraph", "text": "...", "bbox": [x,y,w,h], "confidence": 0.93},
      {"type": "table", "rows": [["Item", "Qty", "Price"], ["Widget", "5", "$10"]], "bbox": [x,y,w,h]}
    ]
  },
  "processing_time_ms": 2100
}
```

#### POST /api/v1/vision/enhance

Apply server-side image enhancement.

```
Request:
  Content-Type: multipart/form-data
  Body: image file
  Query: ?operations=denoise,sharpen,balance&quality=high

Response 200:
  Content-Type: image/jpeg
  Body: enhanced image bytes
  X-Processing-Time-Ms: 540
```

#### POST /api/v1/ai/summarize

Summarize OCR text.

```
Request:
{
  "text": "Full document text...",
  "max_length": 200,
  "style": "bullet_points",
  "language": "en"
}

Response 200:
{
  "summary": "- Key point 1\n- Key point 2\n- Key point 3",
  "model_used": "bart-large-cnn",
  "processing_time_ms": 890
}
```

#### GET /api/v1/health

```
Response 200:
{
  "status": "healthy",
  "models_loaded": ["paddleocr", "layoutlm", "realesrgan", "bart-cnn"],
  "gpu_available": false,
  "worker_count": 4,
  "queue_depth": 12,
  "uptime_seconds": 86400
}
```

### 3.3 Go Client for Python Services

New file in the Go backend:

```go
// internal/intelligence/client.go

type IntelligenceClient interface {
    // Sync operations (direct HTTP)
    Classify(ctx context.Context, image []byte) (*ClassifyResult, error)
    ExtractFields(ctx context.Context, image []byte, docType string, fields []string) (*ExtractionResult, error)
    EnhanceImage(ctx context.Context, image []byte, ops []string) ([]byte, error)

    // Async operations (Redis queue)
    SubmitOCRTask(ctx context.Context, task OCRTaskInput) (taskID string, error)
    SubmitVisionTask(ctx context.Context, task VisionTaskInput) (taskID string, error)
    SubmitAITask(ctx context.Context, task AITaskInput) (taskID string, error)

    // Task management
    GetTaskStatus(ctx context.Context, taskID string) (*TaskStatus, error)
    CancelTask(ctx context.Context, taskID string) error
}
```

---

## 4. Go Backend — New API Endpoints

These endpoints are added to the existing Go backend. The Android app calls these; Go proxies to Python.

```
# Intelligence endpoints (all require auth)

POST   /v1/intelligence/classify              → sync, returns classification
POST   /v1/intelligence/extract               → sync, returns structured fields
POST   /v1/intelligence/enhance               → sync, returns enhanced image

POST   /v1/intelligence/tasks                 → async, enqueues a task, returns task_id
GET    /v1/intelligence/tasks/{id}            → poll task status + result
GET    /v1/intelligence/tasks                 → list recent tasks for account
DELETE /v1/intelligence/tasks/{id}            → cancel a pending/processing task

# Rate limits (per account):
#   sync endpoints:  20 requests/minute
#   async submit:    100 tasks/hour
#   task poll:       60 requests/minute
```

---

## 5. Security & Privacy

### 5.1 Processing Bucket (Ephemeral)

The user's vault is E2E encrypted. For server-side intelligence:

1. **User opts in per-document** — a toggle in the Android app: "Enhance with cloud AI"
2. The Android app **decrypts the page locally** and uploads the plaintext image to a **separate, ephemeral R2 prefix**: `processing/<account_uuid>/<task_uuid>/`
3. The Python service reads from this prefix, processes, writes the result to the same prefix
4. The Go backend returns the result to the client
5. A **TTL cleanup job** deletes everything in `processing/` after 1 hour — no plaintext persists on the server

### 5.2 Rules

- Processing data is **never** mixed with vault data
- Processing prefix has a separate R2 lifecycle rule: auto-delete after 1 hour
- No processing data is ever written to Postgres (only task metadata — no content)
- The intelligence layer has **read-only** access to R2 processing prefix + write access to output prefix
- Python services have **no access** to the vault prefix, no access to Postgres, no access to auth tokens
- All Go-to-Python communication is on an internal network (Docker bridge or localhost), never exposed
- Rate limits prevent abuse of the processing pipeline

### 5.3 User Consent Flow

```
Android App:
  1. User taps "Enhance with AI" on a document
  2. Dialog: "This will send a copy of this document to our servers for processing.
             The copy is deleted within 1 hour. Your vault copy stays encrypted."
  3. User confirms → app decrypts locally → uploads to processing bucket → calls Go API
  4. Result returned → app displays enhanced version → user can save to vault (re-encrypted)
```

---

## 6. Folder Structure

```
scanvault/
├── android/                    # (unchanged)
├── backend/                    # (unchanged, plus new internal/intelligence/)
├── intelligence/               # NEW — Python intelligence layer
│   ├── docker/
│   │   ├── Dockerfile          # Multi-stage: slim Python + system deps
│   │   ├── Dockerfile.worker   # Worker image (same base, different entrypoint)
│   │   └── docker-compose.yml  # Local dev: API + worker + Redis
│   ├── src/
│   │   ├── __init__.py
│   │   ├── main.py             # FastAPI app entry point
│   │   ├── config.py           # Settings via pydantic-settings
│   │   ├── deps.py             # Dependency injection
│   │   ├── models/
│   │   │   ├── __init__.py
│   │   │   ├── requests.py     # Pydantic request models
│   │   │   └── responses.py    # Pydantic response models
│   │   ├── routers/
│   │   │   ├── __init__.py
│   │   │   ├── health.py
│   │   │   ├── ocr.py
│   │   │   ├── vision.py
│   │   │   ├── ai.py
│   │   │   └── classify.py
│   │   ├── services/
│   │   │   ├── __init__.py
│   │   │   ├── ocr_service.py       # PaddleOCR, layout analysis, table extraction
│   │   │   ├── vision_service.py    # OpenCV pipelines, super-res, dewarp
│   │   │   ├── ai_service.py        # Summarization, extraction, embeddings
│   │   │   ├── classify_service.py  # Document type classification
│   │   │   └── model_manager.py     # Lazy model loading, GPU/CPU detection
│   │   ├── workers/
│   │   │   ├── __init__.py
│   │   │   ├── worker.py            # ARQ worker entry point
│   │   │   ├── ocr_tasks.py         # Async OCR task handlers
│   │   │   ├── vision_tasks.py      # Async vision task handlers
│   │   │   └── ai_tasks.py          # Async AI task handlers
│   │   └── utils/
│   │       ├── __init__.py
│   │       ├── image.py             # Image loading, format conversion
│   │       ├── r2.py                # R2 client (read input, write output)
│   │       └── metrics.py           # Prometheus metrics
│   ├── tests/
│   │   ├── __init__.py
│   │   ├── conftest.py
│   │   ├── test_ocr.py
│   │   ├── test_vision.py
│   │   ├── test_ai.py
│   │   └── test_classify.py
│   ├── models/                      # Pre-downloaded model weights (gitignored)
│   │   └── .gitkeep
│   ├── pyproject.toml               # Single source of deps (uv/pip)
│   ├── requirements.lock            # Locked deps for reproducible builds
│   ├── Makefile
│   └── README.md
├── ota/                        # (unchanged)
├── scripts/
│   ├── run-intelligence.sh     # NEW — start Python services locally
│   └── ...                     # (existing scripts unchanged)
└── .github/
    └── workflows/
        ├── intelligence-ci.yml # NEW — Python CI pipeline
        └── ...                 # (existing workflows unchanged)
```

---

## 7. Technology Choices

| Component | Choice | Rationale |
|---|---|---|
| Framework | FastAPI | Async, fast, Pydantic validation, OpenAPI docs free |
| Task queue | ARQ (async Redis queue) | Lightweight, native asyncio, perfect for small scale |
| Redis client | redis-py (async) | Standard, well-maintained |
| OCR | PaddleOCR | Best open-source OCR, beats Tesseract on accuracy, supports 80+ languages |
| Layout analysis | PaddleOCR layout model | Integrated with OCR, detects tables/figures/text blocks |
| Image processing | OpenCV + Pillow | Industry standard, no licensing issues |
| Super-resolution | Real-ESRGAN (x2 model) | Small model (~7 MB), excellent quality, runs on CPU |
| Summarization | BART-large-CNN (HuggingFace) | Good quality, runs on CPU, ~1.6 GB model |
| Field extraction | Donut (HuggingFace) | End-to-end, no OCR needed for structured extraction |
| Embeddings | all-MiniLM-L6-v2 | 80 MB, fast, good quality for semantic search |
| LLM proxy | LiteLLM | Future: route to Claude/GPT/local models via unified API |
| Config | pydantic-settings | Type-safe env var loading, matches FastAPI ecosystem |
| Testing | pytest + pytest-asyncio | Standard Python testing |
| Linting | ruff | Fast, replaces flake8+isort+black |
| Type checking | pyright | Strict, fast |
| Package manager | uv | Fast, reproducible, lock file support |

### Model Loading Strategy

Models are loaded **lazily on first request**, not at startup. This keeps cold start fast and memory low when not all services are needed.

```python
class ModelManager:
    """Lazy-loads models on first use. Thread-safe via asyncio.Lock."""

    async def get_ocr_model(self, languages: list[str]) -> PaddleOCR: ...
    async def get_layout_model(self) -> LayoutModel: ...
    async def get_classifier(self) -> DocumentClassifier: ...
    async def get_summarizer(self) -> Pipeline: ...
    async def get_super_res(self) -> RealESRGAN: ...

    def unload_unused(self, idle_minutes: int = 30) -> None:
        """Free memory for models not used recently."""
```

---

## 8. Deployment Architecture

### MVP (Single VPS — aligns with existing backend)

```
Hetzner CX22 (or CX32 if RAM is tight)
├── Caddy (reverse proxy, TLS)
│   ├── api.scanvault.app → Go binary :8080
│   └── (intelligence is internal-only, not exposed via Caddy)
├── Go API binary (systemd)
├── Python Intelligence API (Docker container, port 8100)
├── Python Worker (Docker container, same image, different entrypoint)
├── Redis (Docker container, port 6379)
├── PostgreSQL 16 (system package)
└── Prometheus + Grafana (existing)
```

**Memory budget on CX22 (4 GB RAM):**
- Go binary: ~50 MB
- PostgreSQL: ~200 MB
- Redis: ~50 MB
- Python API + 1 model loaded: ~800 MB
- Python Worker + 1 model loaded: ~800 MB
- OS + Caddy + Grafana: ~500 MB
- Headroom: ~600 MB

If models are too large, upgrade to CX32 (8 GB) for ~$7/month more.

### Scale-out Path (future)

When the single VPS is insufficient:
1. Move Python services to a separate VPS with GPU (Hetzner CCX or cloud GPU)
2. Scale workers horizontally (just add more Docker containers)
3. Redis stays on the main VPS (or move to managed Redis)
4. No code changes needed — just Docker Compose config

---

## 9. Phasing — When to Build What

The intelligence layer is **Phase 4+** work. It doesn't block Phases 1-3 of the frontend or backend.

### Intelligence Phase 1 (parallel with Backend Phase 4)

- Project skeleton, FastAPI app, Docker setup, CI pipeline
- Health endpoint
- Classification endpoint (lightweight, high value)
- Enhanced OCR endpoint (PaddleOCR, single page)
- Redis queue integration with Go backend
- Basic image enhancement pipeline (denoise, sharpen, balance)

### Intelligence Phase 2 (parallel with Backend Phase 5)

- Layout analysis and table extraction
- Structured field extraction (receipts, invoices, IDs)
- Super-resolution for blurry captures
- Async worker pool for batch processing
- Prometheus metrics

### Intelligence Phase 3 (post-launch, v2)

- Summarization (requires LLM or large model)
- Semantic search embeddings
- LLM proxy ("ask your document" feature)
- Advanced dewarp (DocTr model)
- Handwriting OCR (TrOCR)
- Model fine-tuning pipeline for ScanVault-specific documents

---

## 10. Monitoring & Observability

Python services expose Prometheus metrics at `/metrics`:

```
# Counters
intelligence_requests_total{service="ocr",endpoint="/enhanced",status="200"}
intelligence_tasks_total{type="ocr.enhance",status="completed"}
intelligence_tasks_total{type="ocr.enhance",status="failed"}

# Histograms
intelligence_processing_seconds{service="ocr",operation="enhanced"}
intelligence_processing_seconds{service="vision",operation="enhance"}

# Gauges
intelligence_models_loaded{model="paddleocr"}
intelligence_queue_depth{queue="tasks"}
intelligence_memory_usage_bytes
intelligence_gpu_utilization  # if GPU available
```

Grafana dashboard additions:
- Intelligence request rate and latency
- Queue depth and processing time
- Model memory usage
- Error rate by service

---

## 11. Cost Analysis

### MVP (no GPU, CPU-only inference)

| Component | Monthly Cost |
|---|---|
| VPS upgrade CX22 → CX32 (8 GB RAM) | +$7 |
| Redis (shared VPS) | $0 |
| R2 processing storage (ephemeral, <1 GB at any time) | ~$0.02 |
| Total additional | **~$7/month** |

### With GPU (future, when demand justifies)

| Component | Monthly Cost |
|---|---|
| Hetzner CCX33 (8 vCPU, 32 GB, dedicated) | ~$45 |
| Or: RunPod serverless GPU | ~$0.0002/sec of inference |

CPU-only is sufficient for MVP. PaddleOCR runs at ~2 seconds/page on CPU. Super-resolution at ~5 seconds. Acceptable for async processing.

---

## 12. What We Deliberately Do NOT Build

- **Real-time video processing** — phone does this, server doesn't need to
- **Training pipeline** — fine-tuning is a separate project, not part of the API
- **Model marketplace** — one set of curated models, that's it
- **Multi-tenant isolation** — single-tenant MVP, isolation comes with scale
- **GraphQL** — REST is simpler, we have < 10 endpoints
- **gRPC** — added complexity for internal calls that REST handles fine at this scale
- **Kubernetes** — Docker Compose is enough. K8s when we have 100k+ users
- **WebSocket for results** — polling + push notification is simpler and works offline

---

## 13. Integration Checklist

Before the intelligence layer is considered integrated:

- [ ] Go backend has `internal/intelligence/` package with Redis publisher and HTTP client
- [ ] New migration creates `intelligence_tasks` table
- [ ] Android app has a "Cloud AI" toggle per-document (opt-in)
- [ ] Android app shows processing status in the document reader
- [ ] Processing bucket lifecycle rule auto-deletes after 1 hour
- [ ] Python health check is monitored by the Go `/ready` endpoint
- [ ] Rate limits enforced on all intelligence endpoints
- [ ] No plaintext document data persists on the server beyond the 1-hour TTL
- [ ] Consent dialog shown in the Android app before any server-side processing
- [ ] All intelligence features degrade gracefully when the Python layer is unavailable
