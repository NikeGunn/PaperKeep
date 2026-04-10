from __future__ import annotations

from prometheus_client import Counter, Gauge, Histogram

# Request counters
REQUESTS_TOTAL = Counter(
    "intelligence_requests_total",
    "Total intelligence API requests",
    ["service", "endpoint", "status"],
)

# Processing duration
PROCESSING_SECONDS = Histogram(
    "intelligence_processing_seconds",
    "Processing time in seconds",
    ["service", "operation"],
    buckets=(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 120.0),
)

# Model state
MODELS_LOADED = Gauge(
    "intelligence_models_loaded",
    "Number of currently loaded models",
)

# Queue depth
QUEUE_DEPTH = Gauge(
    "intelligence_queue_depth",
    "Number of pending tasks in the queue",
    ["queue"],
)

# Memory usage
MEMORY_USAGE = Gauge(
    "intelligence_memory_usage_bytes",
    "Memory usage of the intelligence service",
)
