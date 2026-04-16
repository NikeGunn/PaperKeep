from __future__ import annotations

import base64
from typing import Annotated

from fastapi import APIRouter, Body, File, HTTPException, UploadFile
from pydantic import BaseModel

from src.models.responses import ClassifyResponse
from src.services.classifier import ClassificationResult, DocumentClassifier

router = APIRouter()

# Module-level singleton — lazy init on first request
_classifier: DocumentClassifier | None = None


def _get_classifier() -> DocumentClassifier:
    global _classifier
    if _classifier is None:
        _classifier = DocumentClassifier()
    return _classifier


class ClassifyJSONRequest(BaseModel):
    image_base64: str


def _build_response(result: ClassificationResult) -> ClassifyResponse:
    return ClassifyResponse(type=result.type, confidence=result.confidence)


@router.post("/classify", response_model=ClassifyResponse)
async def classify_multipart(
    image: Annotated[UploadFile | None, File()] = None,
) -> ClassifyResponse:
    """Classify a document image. Accepts multipart/form-data with 'image' field."""
    if image is None:
        raise HTTPException(status_code=422, detail="'image' field is required")

    image_bytes = await image.read()
    if not image_bytes:
        raise HTTPException(status_code=422, detail="Empty image")

    result = _get_classifier().classify(image_bytes)
    return _build_response(result)


@router.post("/classify/json", response_model=ClassifyResponse)
async def classify_json(
    body: Annotated[ClassifyJSONRequest, Body()],
) -> ClassifyResponse:
    """Classify a document image. Accepts JSON with 'image_base64' field."""
    try:
        image_bytes = base64.b64decode(body.image_base64)
    except Exception as exc:
        raise HTTPException(status_code=422, detail="Invalid base64 in 'image_base64'") from exc

    if not image_bytes:
        raise HTTPException(status_code=422, detail="Empty image_base64")

    result = _get_classifier().classify(image_bytes)
    return _build_response(result)
