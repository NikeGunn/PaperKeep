from __future__ import annotations

import logging
from typing import ClassVar

from arq import create_pool
from arq.connections import RedisSettings

from src.config import settings
from src.workers.ai_tasks import process_ai_task
from src.workers.ocr_tasks import process_ocr_task
from src.workers.vision_tasks import process_vision_task

logger = logging.getLogger("scanvault.intelligence.worker")


async def startup(ctx: dict) -> None:
    """Called when the worker starts. Initialize shared resources."""
    from src.services.model_manager import ModelManager

    ctx["model_manager"] = ModelManager(settings=settings)
    ctx["redis"] = await create_pool(RedisSettings.from_dsn(settings.redis_url))
    logger.info("Worker started")


async def shutdown(ctx: dict) -> None:
    """Called when the worker shuts down. Cleanup."""
    logger.info("Worker shutting down")


class WorkerSettings:
    """ARQ worker configuration."""

    functions: ClassVar = [process_ocr_task, process_vision_task, process_ai_task]
    on_startup = startup
    on_shutdown = shutdown
    redis_settings = RedisSettings.from_dsn(settings.redis_url)
    max_jobs = 4
    job_timeout = 300  # 5 minutes max per task
    queue_name = settings.task_queue_name
