from __future__ import annotations

import asyncio
import logging
from typing import Any

import numpy as np

from src.services.model_manager import ModelManager
from src.utils.image import bytes_to_cv2

logger = logging.getLogger("scanvault.intelligence.ocr")


class OCRService:
    """Enhanced OCR using PaddleOCR with optional layout analysis."""

    def __init__(self, model_manager: ModelManager) -> None:
        self._models = model_manager

    async def recognize(
        self,
        image_bytes: bytes,
        languages: list[str] | None = None,
        detect_tables: bool = False,
        detect_layout: bool = False,
    ) -> dict[str, Any]:
        """Run enhanced OCR on an image.

        Returns dict with keys: text, confidence, language, layout (optional).
        """
        ocr_model = await self._models.get_ocr_model(languages)
        img = bytes_to_cv2(image_bytes)

        # PaddleOCR inference runs in a thread to avoid blocking the event loop
        result = await asyncio.to_thread(ocr_model.ocr, img, cls=True)

        text_blocks: list[str] = []
        confidences: list[float] = []
        layout_blocks: list[dict[str, Any]] = []

        if result and result[0]:
            for line in result[0]:
                bbox_points = line[0]  # [[x1,y1],[x2,y2],[x3,y3],[x4,y4]]
                text = line[1][0]
                conf = float(line[1][1])
                text_blocks.append(text)
                confidences.append(conf)

                if detect_layout:
                    x_coords = [p[0] for p in bbox_points]
                    y_coords = [p[1] for p in bbox_points]
                    layout_blocks.append({
                        "type": "paragraph",
                        "text": text,
                        "bbox": {
                            "x": float(min(x_coords)),
                            "y": float(min(y_coords)),
                            "w": float(max(x_coords) - min(x_coords)),
                            "h": float(max(y_coords) - min(y_coords)),
                        },
                        "confidence": conf,
                    })

        avg_confidence = float(np.mean(confidences)) if confidences else 0.0

        result_dict: dict[str, Any] = {
            "text": "\n".join(text_blocks),
            "confidence": round(avg_confidence, 3),
            "language": (languages or ["en"])[0],
        }

        if detect_layout and layout_blocks:
            result_dict["layout"] = layout_blocks

        return result_dict
