from __future__ import annotations

import logging
from typing import ClassVar

from arq.connections import RedisSettings

from src.config import settings
from src.workers.ai_tasks import process_ai_task
from src.workers.ocr_tasks import process_ocr_task
from src.workers.vision_tasks import process_vision_task

logger = logging.getLogger("scanvault.intelligence.worker")

# Redis key prefix for task status hashes
TASK_STATUS_PREFIX = "scanvault:intelligence:task:"


async def startup(ctx: dict) -> None:
    """Called when the worker starts. Initialize shared resources."""
    logger.info("Worker started, connecting to Redis")
    ctx["redis_url"] = settings.redis_url


async def shutdown(ctx: dict) -> None:
    """Called when the worker shuts down. Cleanup."""
    logger.info("Worker shutting down")


class WorkerSettings:
    """ARQ worker configuration for the ScanVault intelligence queue."""

    functions: ClassVar = [process_ocr_task, process_vision_task, process_ai_task]
    on_startup = startup
    on_shutdown = shutdown
    redis_settings = RedisSettings.from_dsn(settings.redis_url)
    max_jobs = 4
    job_timeout = settings.max_processing_time_seconds
    keep_result = 3600  # Keep results for 1 hour
    queue_name = settings.task_queue_name
