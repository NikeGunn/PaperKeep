from __future__ import annotations

import time

from fastapi import APIRouter, Depends

from src.deps import get_model_manager
from src.models.responses import HealthResponse
from src.services.model_manager import ModelManager

router = APIRouter()

_start_time = time.monotonic()


@router.get("/api/v1/health", response_model=HealthResponse)
async def health(model_manager: ModelManager = Depends(get_model_manager)) -> HealthResponse:
    return HealthResponse(
        status="healthy",
        models_loaded=model_manager.loaded_model_names(),
        gpu_available=model_manager.gpu_available,
        worker_count=1,  # Updated by worker registration
        queue_depth=0,  # Updated by Redis query
        uptime_seconds=time.monotonic() - _start_time,
    )
