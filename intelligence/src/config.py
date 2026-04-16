from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Intelligence layer configuration. All values from environment variables."""

    model_config = SettingsConfigDict(env_prefix="INTEL_", env_file=".env")

    # Server
    host: str = "0.0.0.0"
    port: int = 8100
    workers: int = 1
    debug: bool = False

    # Redis (task queue)
    redis_url: str = "redis://localhost:6379/0"
    task_queue_name: str = "scanvault:intelligence:tasks"
    result_channel: str = "scanvault:intelligence:results"

    # AWS S3 storage (replaces R2 — standard AWS endpoint, no custom URL)
    s3_bucket_name: str = "scanvault-staging-vault-203a9e83"
    aws_region: str = "ap-south-1"

    # Model paths
    model_cache_dir: str = "./models"

    # Processing limits
    max_image_size_mb: int = 50
    max_processing_time_seconds: int = 120
    processing_ttl_hours: int = 1

    # Feature flags
    enable_ocr: bool = True
    enable_vision: bool = True
    enable_ai: bool = False  # Disabled until Phase 2 of intelligence layer
    enable_gpu: bool = False


settings = Settings()
