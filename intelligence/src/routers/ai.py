from __future__ import annotations

import time

from fastapi import APIRouter, Depends

from src.deps import get_model_manager
from src.models.requests import ExtractFieldsRequest, SummarizeRequest
from src.models.responses import ExtractFieldsResponse, SummarizeResponse
from src.services.ai_service import AIService
from src.services.model_manager import ModelManager

router = APIRouter()


@router.post("/summarize", response_model=SummarizeResponse)
async def summarize(
    request: SummarizeRequest,
    model_manager: ModelManager = Depends(get_model_manager),
) -> SummarizeResponse:
    """Summarize document text."""
    start = time.monotonic()

    service = AIService(model_manager)
    result = await service.summarize(
        text=request.text,
        max_length=request.max_length,
        style=request.style,
        language=request.language,
    )

    elapsed_ms = int((time.monotonic() - start) * 1000)
    return SummarizeResponse(
        summary=result["summary"],
        model_used=result["model"],
        processing_time_ms=elapsed_ms,
    )


@router.post("/extract-fields", response_model=ExtractFieldsResponse)
async def extract_fields(
    request: ExtractFieldsRequest,
    model_manager: ModelManager = Depends(get_model_manager),
) -> ExtractFieldsResponse:
    """Extract structured fields from a document image."""
    start = time.monotonic()

    service = AIService(model_manager)
    result = await service.extract_fields(
        image_b64=request.image_b64,
        document_type=request.document_type,
        fields=request.fields,
    )

    elapsed_ms = int((time.monotonic() - start) * 1000)
    return ExtractFieldsResponse(
        fields=result["fields"],
        processing_time_ms=elapsed_ms,
    )
