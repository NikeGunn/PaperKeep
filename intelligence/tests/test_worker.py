from __future__ import annotations

# Task 3C.5 — ARQ Worker tests (mocked Redis)
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

# --- WorkerSettings ---


def test_worker_settings_has_required_attributes():
    """WorkerSettings must expose functions, redis_settings, and queue_name."""
    from src.workers.worker_main import WorkerSettings

    assert hasattr(WorkerSettings, "functions")
    assert hasattr(WorkerSettings, "redis_settings")
    assert hasattr(WorkerSettings, "queue_name")
    assert hasattr(WorkerSettings, "on_startup")
    assert hasattr(WorkerSettings, "on_shutdown")


def test_worker_settings_functions_list():
    """All three task functions must be registered."""
    from src.workers.worker_main import WorkerSettings

    names = {f.__name__ for f in WorkerSettings.functions}
    assert "process_ocr_task" in names
    assert "process_vision_task" in names
    assert "process_ai_task" in names


def test_worker_settings_queue_name():
    """Queue name must match config."""
    from src.config import settings
    from src.workers.worker_main import WorkerSettings

    assert WorkerSettings.queue_name == settings.task_queue_name


def test_worker_settings_job_timeout():
    """Job timeout must match config."""
    from src.config import settings
    from src.workers.worker_main import WorkerSettings

    assert WorkerSettings.job_timeout == settings.max_processing_time_seconds


# --- startup / shutdown ---


@pytest.mark.asyncio
async def test_worker_startup_sets_redis_url():
    """startup() should store redis_url in ctx."""
    from src.workers.worker_main import startup

    ctx: dict = {}
    await startup(ctx)
    assert "redis_url" in ctx


@pytest.mark.asyncio
async def test_worker_shutdown_does_not_raise():
    """shutdown() must not raise even with empty ctx."""
    from src.workers.worker_main import shutdown

    await shutdown({})  # should complete without error


# --- process_ocr_task ---


@pytest.mark.asyncio
async def test_process_ocr_task_completed_status():
    """process_ocr_task should return status=completed on success."""
    from src.workers import ocr_tasks

    task_data = {
        "task_id": "test-ocr-123",
        "task_type": "ocr.enhance",
        "input": {
            "s3_key": "processing/account/test-ocr-123/input.jpg",
            "params": {"languages": ["en"]},
        },
    }

    fake_image = b"\xff\xd8\xff\xe0" + b"\x00" * 100  # minimal fake JPEG header

    with (
        patch.object(ocr_tasks, "download_from_s3", return_value=fake_image),
        patch.object(ocr_tasks, "upload_to_s3"),
    ):
        # Patch the OCRService to avoid real model loading
        from src.services.ocr_service import OCRResult

        mock_svc = MagicMock()
        mock_svc.extract_text_async = AsyncMock(
            return_value=OCRResult(
                text="Hello World",
                blocks=[],
                confidence=0.9,
            )
        )
        # Patch service instantiation
        with patch("src.workers.ocr_tasks.OCRService", return_value=mock_svc):
            # The task uses ctx for model_manager — pass empty ctx
            result = await ocr_tasks.process_ocr_task({}, task_data)

    assert result["task_id"] == "test-ocr-123"
    assert result["status"] == "completed"
    assert "output_s3_key" in result


@pytest.mark.asyncio
async def test_process_vision_task_completed_status():
    """process_vision_task should return status=completed on success."""
    from src.workers import vision_tasks

    task_data = {
        "task_id": "test-vision-456",
        "task_type": "vision.enhance",
        "input": {
            "s3_key": "processing/account/test-vision-456/input.jpg",
            "params": {"quality": "high"},
        },
    }

    import cv2
    import numpy as np

    img = np.ones((100, 100, 3), dtype=np.uint8) * 200
    _, buf = cv2.imencode(".jpg", img)
    fake_image = buf.tobytes()

    with (
        patch.object(vision_tasks, "download_from_s3", return_value=fake_image),
        patch.object(vision_tasks, "upload_to_s3"),
    ):
        result = await vision_tasks.process_vision_task({}, task_data)

    assert result["task_id"] == "test-vision-456"
    assert result["status"] == "completed"
    assert "output_s3_key" in result


@pytest.mark.asyncio
async def test_process_ai_task_unknown_type_returns_failed():
    """Unknown task_type should return failed, not raise."""
    from src.workers import ai_tasks

    task_data = {
        "task_id": "test-ai-789",
        "task_type": "ai.unknown_operation",
        "input": {
            "s3_key": "processing/account/test-ai-789/input.txt",
            "params": {},
        },
    }

    with patch.object(ai_tasks, "download_from_s3", return_value=b"text"):
        result = await ai_tasks.process_ai_task({}, task_data)

    assert result["status"] == "failed"
    assert result["task_id"] == "test-ai-789"
