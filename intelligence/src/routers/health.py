from __future__ import annotations

import time

from fastapi import APIRouter

from src.models.responses import HealthResponse

router = APIRouter()

_start_time = time.monotonic()
_VERSION = "0.1.0"


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """Health check endpoint. Returns service status, version, and uptime."""
    return HealthResponse(
        status="healthy",
        version=_VERSION,
        uptime_seconds=round(time.monotonic() - _start_time, 3),
    )
