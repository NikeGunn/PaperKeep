from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass, field

from fastapi import HTTPException

from src.config import settings

logger = logging.getLogger("scanvault.intelligence.ocr")

_MAX_IMAGE_BYTES = settings.max_image_size_mb * 1024 * 1024


@dataclass
class OCRResult:
    text: str
    blocks: list[dict] = field(default_factory=list)
    confidence: float = 0.0


class OCRService:
    """Enhanced OCR using EasyOCR with lazy model loading."""

    def __init__(self) -> None:
        self._reader = None  # type: ignore[assignment]
        self._reader_loaded = False

    def _ensure_reader(self, languages: list[str] | None = None) -> None:
        """Lazy-load EasyOCR reader on first use."""
        if self._reader_loaded:
            return
        if not settings.enable_ocr:
            self._reader_loaded = True
            self._reader = None
            return
        try:
            import easyocr  # type: ignore[import-untyped]

            lang_list = languages or ["en"]
            logger.info("Loading EasyOCR reader", extra={"languages": lang_list})
            self._reader = easyocr.Reader(lang_list, gpu=settings.enable_gpu, verbose=False)
        except ImportError:
            logger.warning("easyocr not installed — OCR will return empty results")
            self._reader = None
        finally:
            self._reader_loaded = True

    def extract_text(self, image_bytes: bytes, languages: list[str] | None = None) -> OCRResult:
        """Extract text from image bytes.

        Returns OCRResult(text="", blocks=[], confidence=0.0) for corrupt images.
        Raises HTTPException 413 if image exceeds size limit.
        """
        if len(image_bytes) > _MAX_IMAGE_BYTES:
            raise HTTPException(
                status_code=413,
                detail=f"Image too large: max {settings.max_image_size_mb}MB",
            )

        self._ensure_reader(languages)

        if self._reader is None:
            return OCRResult(text="", blocks=[], confidence=0.0)

        try:
            import cv2
            import numpy as np

            arr = np.frombuffer(image_bytes, dtype=np.uint8)
            img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
            if img is None:
                return OCRResult(text="", blocks=[], confidence=0.0)

            raw = self._reader.readtext(img)
            if not raw:
                return OCRResult(text="", blocks=[], confidence=0.0)

            text_parts: list[str] = []
            blocks: list[dict] = []
            confidences: list[float] = []

            for bbox, text, conf in raw:
                text_parts.append(text)
                confidences.append(float(conf))
                xs = [p[0] for p in bbox]
                ys = [p[1] for p in bbox]
                blocks.append(
                    {
                        "text": text,
                        "confidence": round(float(conf), 3),
                        "bbox": {
                            "x": float(min(xs)),
                            "y": float(min(ys)),
                            "w": float(max(xs) - min(xs)),
                            "h": float(max(ys) - min(ys)),
                        },
                    }
                )

            avg_confidence = sum(confidences) / len(confidences) if confidences else 0.0
            return OCRResult(
                text="\n".join(text_parts),
                blocks=blocks,
                confidence=round(avg_confidence, 3),
            )
        except HTTPException:
            raise
        except Exception:
            logger.exception("OCR extraction failed")
            return OCRResult(text="", blocks=[], confidence=0.0)

    async def extract_text_async(
        self, image_bytes: bytes, languages: list[str] | None = None
    ) -> OCRResult:
        """Async wrapper — runs blocking inference in a thread pool."""
        return await asyncio.to_thread(self.extract_text, image_bytes, languages)
