from __future__ import annotations

import time

from fastapi import APIRouter, Depends, Query, UploadFile
from fastapi.responses import Response

from src.deps import get_model_manager
from src.services.model_manager import ModelManager
from src.services.vision_service import VisionService

router = APIRouter()


@router.post("/enhance")
async def enhance_image(
    image: UploadFile,
    operations: str = Query(
        default="denoise,sharpen,balance",
        description="Comma-separated: denoise, sharpen, balance, super_resolve",
    ),
    quality: str = Query(default="high", pattern="^(low|medium|high)$"),
    model_manager: ModelManager = Depends(get_model_manager),
) -> Response:
    """Apply server-side image enhancement. Returns the enhanced image bytes."""
    start = time.monotonic()
    image_bytes = await image.read()
    ops = [op.strip() for op in operations.split(",")]

    service = VisionService(model_manager)
    enhanced_bytes = await service.enhance(image_bytes, operations=ops, quality=quality)

    elapsed_ms = int((time.monotonic() - start) * 1000)
    return Response(
        content=enhanced_bytes,
        media_type="image/jpeg",
        headers={"X-Processing-Time-Ms": str(elapsed_ms)},
    )
