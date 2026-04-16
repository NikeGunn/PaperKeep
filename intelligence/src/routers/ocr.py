from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, File, HTTPException, UploadFile
from pydantic import BaseModel

from src.config import settings
from src.services.ocr_service import OCRService

router = APIRouter()

_MAX_IMAGE_BYTES = settings.max_image_size_mb * 1024 * 1024

# Module-level singleton for lazy loading
_ocr_service: OCRService | None = None


def _get_ocr_service() -> OCRService:
    global _ocr_service
    if _ocr_service is None:
        _ocr_service = OCRService()
    return _ocr_service


class OCRBlock(BaseModel):
    text: str
    confidence: float
    bbox: dict


class OCRResponse(BaseModel):
    text: str
    blocks: list[OCRBlock]
    language: str
    confidence: float


@router.post("/", response_model=OCRResponse)
async def ocr_image(
    image: Annotated[UploadFile, File()],
) -> OCRResponse:
    """Extract text from a document image using EasyOCR.

    - Accepts multipart/form-data with 'image' field.
    - Returns 413 if image exceeds 50 MB.
    - Returns 400 if image is corrupted / unreadable.
    - Returns empty result for blank images (not an error).
    """
    image_bytes = await image.read()

    if len(image_bytes) > _MAX_IMAGE_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Image too large: max {settings.max_image_size_mb}MB",
        )

    if not image_bytes:
        raise HTTPException(status_code=400, detail="Empty image file")

    # Quick corruption check before passing to OCR
    try:
        import cv2
        import numpy as np

        arr = np.frombuffer(image_bytes, dtype=np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if img is None:
            raise HTTPException(status_code=400, detail="Corrupted or unreadable image")
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Corrupted or unreadable image") from exc

    service = _get_ocr_service()
    result = await service.extract_text_async(image_bytes)

    blocks = [
        OCRBlock(
            text=b["text"],
            confidence=b["confidence"],
            bbox=b["bbox"],
        )
        for b in result.blocks
    ]

    return OCRResponse(
        text=result.text,
        blocks=blocks,
        language="en" if result.text else "unknown",
        confidence=result.confidence,
    )


@router.post("/enhanced")
async def enhanced_ocr(
    image: Annotated[UploadFile, File()],
) -> OCRResponse:
    """Alias for the base OCR endpoint (legacy route)."""
    return await ocr_image(image=image)
