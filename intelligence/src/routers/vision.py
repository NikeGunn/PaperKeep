from __future__ import annotations

import time
from typing import Annotated

from fastapi import APIRouter, File, HTTPException, Query, UploadFile
from fastapi.responses import Response

from src.services.vision_service import VisionService

router = APIRouter()

# Module-level singleton (no model manager needed for pure OpenCV ops)
_vision_service: VisionService | None = None


def _get_vision_service() -> VisionService:
    global _vision_service
    if _vision_service is None:
        _vision_service = VisionService()
    return _vision_service


@router.post("/enhance")
async def enhance_image(
    image: Annotated[UploadFile, File()],
    operations: str = Query(
        default="denoise,sharpen,balance",
        description="Comma-separated: denoise, sharpen, balance",
    ),
) -> Response:
    """Apply server-side image enhancement. Returns the enhanced image as JPEG bytes.

    - Returns 400 if image is corrupted / unreadable.
    """
    image_bytes = await image.read()

    if not image_bytes:
        raise HTTPException(status_code=400, detail="Empty image file")

    # Validate the image is decodable before processing
    try:
        import cv2
        import numpy as np

        arr = np.frombuffer(image_bytes, dtype=np.uint8)
        img_check = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if img_check is None:
            raise HTTPException(status_code=400, detail="Corrupted or unreadable image")
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Corrupted or unreadable image") from exc

    ops = [op.strip() for op in operations.split(",") if op.strip()]

    start = time.monotonic()
    service = _get_vision_service()
    enhanced_bytes = await service.enhance(image_bytes, operations=ops)

    elapsed_ms = int((time.monotonic() - start) * 1000)
    return Response(
        content=enhanced_bytes,
        media_type="image/jpeg",
        headers={"X-Processing-Time-Ms": str(elapsed_ms)},
    )
