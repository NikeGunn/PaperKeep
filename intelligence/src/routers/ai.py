from __future__ import annotations

import time
from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from src.services.model_manager import ModelManager

router = APIRouter()


def _get_model_manager() -> ModelManager:
    from src.config import settings

    return ModelManager(settings=settings)


class SummarizeRequest(BaseModel):
    text: str
    max_length: int = 200
    style: str = "bullet_points"
    language: str = "en"


class SummarizeResponse(BaseModel):
    summary: str
    model_used: str
    processing_time_ms: int


@router.post("/summarize", response_model=SummarizeResponse)
async def summarize(
    request: SummarizeRequest,
    model_manager: Annotated[ModelManager, Depends(_get_model_manager)],
) -> SummarizeResponse:
    """Summarize document text."""
    from src.services.ai_service import AIService

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
