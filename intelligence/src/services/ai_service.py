from __future__ import annotations

import asyncio
import base64
import logging
from typing import Any

from src.services.model_manager import ModelManager
from src.utils.image import bytes_to_cv2

logger = logging.getLogger("scanvault.intelligence.ai")


class AIService:
    """Higher-level AI: summarization, field extraction, embeddings."""

    def __init__(self, model_manager: ModelManager) -> None:
        self._models = model_manager

    async def summarize(
        self,
        text: str,
        max_length: int = 200,
        style: str = "bullet_points",
        language: str = "en",
    ) -> dict[str, Any]:
        """Summarize document text using a local model."""
        summarizer = await self._models.get_summarizer()

        # Run inference in thread pool
        result = await asyncio.to_thread(
            summarizer,
            text[:4096],  # Model input limit
            max_length=max_length,
            min_length=30,
            do_sample=False,
        )

        raw_summary: str = result[0]["summary_text"]

        # Format based on style
        if style == "bullet_points":
            sentences = [s.strip() for s in raw_summary.split(".") if s.strip()]
            formatted = "\n".join(f"- {s}." for s in sentences[:5])
        elif style == "key_facts":
            sentences = [s.strip() for s in raw_summary.split(".") if s.strip()]
            formatted = "\n".join(f"{i + 1}. {s}." for i, s in enumerate(sentences[:5]))
        else:
            formatted = raw_summary

        return {"summary": formatted, "model": "bart-large-cnn"}

    async def extract_fields(
        self,
        image_b64: str,
        document_type: str,
        fields: list[str],
    ) -> dict[str, Any]:
        """Extract structured fields from a document image.

        Stub implementation — uses OCR + regex heuristics.
        Replace with Donut/LayoutLMv3 for production accuracy.
        """
        image_bytes = base64.b64decode(image_b64)
        _ = bytes_to_cv2(image_bytes)  # Validate image

        # TODO: Replace with Donut or LayoutLMv3 inference
        # For now, return empty fields as a stub
        logger.info(
            "Field extraction stub called",
            extra={"document_type": document_type, "fields": fields},
        )

        extracted: dict[str, Any] = {}
        for field in fields:
            extracted[field] = {
                "value": "",
                "confidence": 0.0,
                "bbox": None,
            }

        return {"fields": extracted}
