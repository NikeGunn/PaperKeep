from __future__ import annotations

import time

from fastapi import APIRouter, Depends, Query, UploadFile

from src.deps import get_model_manager
from src.models.responses import EnhancedOCRResponse
from src.services.model_manager import ModelManager
from src.services.ocr_service import OCRService

router = APIRouter()


@router.post("/enhanced", response_model=EnhancedOCRResponse)
async def enhanced_ocr(
    image: UploadFile,
    languages: str = Query(default="en", description="Comma-separated language codes"),
    detect_tables: bool = Query(default=False),
    detect_layout: bool = Query(default=False),
    model_manager: ModelManager = Depends(get_model_manager),
) -> EnhancedOCRResponse:
    """Enhanced OCR with optional layout analysis and table detection."""
    start = time.monotonic()
    image_bytes = await image.read()
    lang_list = [lang.strip() for lang in languages.split(",")]

    service = OCRService(model_manager)
    result = await service.recognize(
        image_bytes,
        languages=lang_list,
        detect_tables=detect_tables,
        detect_layout=detect_layout,
    )

    elapsed_ms = int((time.monotonic() - start) * 1000)
    return EnhancedOCRResponse(
        text=result["text"],
        confidence=result["confidence"],
        language=result["language"],
        layout=result.get("layout"),
        processing_time_ms=elapsed_ms,
    )
