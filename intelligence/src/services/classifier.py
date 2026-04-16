from __future__ import annotations

import logging
from dataclasses import dataclass

logger = logging.getLogger("scanvault.intelligence.classifier")

_VALID_TYPES = {"document", "receipt", "id_card", "whiteboard", "other"}


@dataclass
class ClassificationResult:
    type: str  # document | receipt | id_card | whiteboard | other
    confidence: float  # 0.0 - 1.0


class DocumentClassifier:
    """Heuristic document classifier with lazy ML model loading.

    On first call to classify(), the ML model is initialised (if available).
    Falls back to aspect-ratio + brightness heuristics when no model is loaded.
    """

    def __init__(self) -> None:
        self._model = None  # type: ignore[assignment]
        self._model_loaded = False

    def _ensure_model(self) -> None:
        """Lazy-load the ML model on first classify() call."""
        if self._model_loaded:
            return
        self._model_loaded = True
        # Stub: real model loading would go here.
        # Returning None means heuristic fallback is used.
        self._model = None
        logger.info("DocumentClassifier initialised (heuristic fallback mode)")

    def classify(self, image_bytes: bytes) -> ClassificationResult:
        """Classify a document image.

        Returns ClassificationResult(type="other", confidence=0.0) for invalid/
        corrupted images so callers always receive a safe default.
        """
        self._ensure_model()

        if self._model is not None:
            # Future: delegate to the ML model.
            return self._ml_classify(image_bytes)

        return self._heuristic_classify(image_bytes)

    # ------------------------------------------------------------------
    # Private helpers
    # ------------------------------------------------------------------

    def _ml_classify(self, image_bytes: bytes) -> ClassificationResult:  # pragma: no cover
        """Placeholder for real ML model inference."""
        raise NotImplementedError("ML model not yet integrated")

    @staticmethod
    def _heuristic_classify(image_bytes: bytes) -> ClassificationResult:
        """Aspect-ratio + brightness heuristic fallback."""
        try:
            import cv2
            import numpy as np

            arr = np.frombuffer(image_bytes, dtype=np.uint8)
            img = cv2.imdecode(arr, cv2.IMREAD_COLOR)

            if img is None:
                return ClassificationResult(type="other", confidence=0.0)

            h, w = img.shape[:2]
            if h == 0 or w == 0:
                return ClassificationResult(type="other", confidence=0.0)

            aspect = w / h
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            mean_brightness = float(np.mean(gray))

            # Very tall/narrow → receipt
            if aspect > 2.0 or aspect < 0.3:
                return ClassificationResult(type="receipt", confidence=0.6)

            # Portrait roughly A4-ish → document
            if 0.55 < aspect < 0.80 and h > w:
                return ClassificationResult(type="document", confidence=0.55)

            # Landscape with high brightness → whiteboard
            if aspect > 1.3 and mean_brightness > 200:
                return ClassificationResult(type="whiteboard", confidence=0.5)

            # Near-1:1.6 landscape → id_card
            if 1.4 < aspect < 1.8:
                return ClassificationResult(type="id_card", confidence=0.45)

            return ClassificationResult(type="other", confidence=0.3)

        except Exception:
            logger.exception("Heuristic classification failed")
            return ClassificationResult(type="other", confidence=0.0)
