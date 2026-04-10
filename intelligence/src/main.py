from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI
from prometheus_client import make_asgi_app

from src.config import settings
from src.routers import ai, classify, health, ocr, vision

logger = logging.getLogger("scanvault.intelligence")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    logger.info(
        "Intelligence layer starting",
        extra={"host": settings.host, "port": settings.port},
    )
    yield
    logger.info("Intelligence layer shutting down")


app = FastAPI(
    title="ScanVault Intelligence",
    version="0.1.0",
    docs_url="/docs" if settings.debug else None,
    redoc_url=None,
    lifespan=lifespan,
)

# Prometheus metrics endpoint
metrics_app = make_asgi_app()
app.mount("/metrics", metrics_app)

# Register routers
app.include_router(health.router, tags=["health"])
app.include_router(classify.router, prefix="/api/v1", tags=["classify"])
app.include_router(ocr.router, prefix="/api/v1/ocr", tags=["ocr"])
app.include_router(vision.router, prefix="/api/v1/vision", tags=["vision"])

if settings.enable_ai:
    app.include_router(ai.router, prefix="/api/v1/ai", tags=["ai"])
