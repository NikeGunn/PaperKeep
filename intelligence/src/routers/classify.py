from __future__ import annotations

import time

from fastapi import APIRouter, Depends, UploadFile

from src.deps import get_model_manager
from src.models.responses import ClassifyResponse
from src.services.classify_service import ClassifyService
from src.services.model_manager import ModelManager

router = APIRouter()


@router.post("/classify", response_model=ClassifyResponse)
async def classify_document(
    image: UploadFile,
    model_manager: ModelManager = Depends(get_model_manager),
) -> ClassifyResponse:
    """Classify a document image into type categories. Fast (< 200ms target)."""
    start = time.monotonic()
    image_bytes = await image.read()

    service = ClassifyService(model_manager)
    result = await service.classify(image_bytes)

    elapsed_ms = int((time.monotonic() - start) * 1000)
    return ClassifyResponse(
        document_type=result["type"],
        confidence=result["confidence"],
        sub_type=result.get("sub_type"),
        suggested_filter=result.get("filter"),
        suggested_aspect=result.get("aspect"),
        processing_time_ms=elapsed_ms,
    )
