from __future__ import annotations

import asyncio
import logging
from typing import Any

from src.services.model_manager import ModelManager
from src.utils.image import bytes_to_cv2

logger = logging.getLogger("scanvault.intelligence.classify")

# Document type to suggested processing parameters
_TYPE_SUGGESTIONS: dict[str, dict[str, str]] = {
    "receipt": {"filter": "bw_adaptive", "aspect": "tall"},
    "id_card": {"filter": "magic_color", "aspect": "landscape_4x3"},
    "business_card": {"filter": "magic_color", "aspect": "landscape_16x9"},
    "a4_document": {"filter": "bw_adaptive", "aspect": "portrait_a4"},
    "whiteboard": {"filter": "whiteboard_enhance", "aspect": "landscape_16x9"},
    "book_page": {"filter": "grayscale", "aspect": "portrait_a4"},
    "photo": {"filter": "original", "aspect": "auto"},
    "unknown": {"filter": "auto", "aspect": "auto"},
}


class ClassifyService:
    """Document type classification from image content."""

    def __init__(self, model_manager: ModelManager) -> None:
        self._models = model_manager

    async def classify(self, image_bytes: bytes) -> dict[str, Any]:
        """Classify a document image and return type + suggestions.

        Returns dict with keys: type, confidence, sub_type, filter, aspect.
        """
        img = bytes_to_cv2(image_bytes)
        classifier = await self._models.get_classifier()

        if classifier is None:
            # Fallback: heuristic-based classification using image properties
            result = await asyncio.to_thread(self._heuristic_classify, img)
        else:
            result = await asyncio.to_thread(classifier, img)

        doc_type = result.get("type", "unknown")
        suggestions = _TYPE_SUGGESTIONS.get(doc_type, _TYPE_SUGGESTIONS["unknown"])

        return {
            "type": doc_type,
            "confidence": result.get("confidence", 0.0),
            "sub_type": result.get("sub_type"),
            "filter": suggestions["filter"],
            "aspect": suggestions["aspect"],
        }

    @staticmethod
    def _heuristic_classify(img: Any) -> dict[str, Any]:
        """Simple heuristic classification based on aspect ratio and content.

        This is a temporary fallback until the ML classifier is loaded.
        """
        import cv2
        import numpy as np

        h, w = img.shape[:2]
        aspect = w / h

        # Convert to grayscale for analysis
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        mean_brightness = float(np.mean(gray))

        # Heuristic rules
        if aspect > 2.0 or aspect < 0.3:
            return {"type": "receipt", "confidence": 0.6, "sub_type": "long_receipt"}
        if 0.55 < aspect < 0.75 and h > w:
            return {"type": "a4_document", "confidence": 0.5}
        if 1.4 < aspect < 1.8:
            if mean_brightness > 200:
                return {"type": "whiteboard", "confidence": 0.4}
            return {"type": "id_card", "confidence": 0.4}
        if 1.5 < aspect < 1.7:
            return {"type": "business_card", "confidence": 0.4}

        return {"type": "unknown", "confidence": 0.3}
