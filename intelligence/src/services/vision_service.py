from __future__ import annotations

import asyncio
import logging

import cv2
import numpy as np

from src.utils.image import bytes_to_cv2, cv2_to_jpeg_bytes

logger = logging.getLogger("scanvault.intelligence.vision")


class VisionService:
    """Server-side image enhancement pipelines using OpenCV.

    Pure OpenCV implementation — no ML model required.
    Lazy-instantiated; safe to construct at import time.
    """

    async def enhance(
        self,
        image_bytes: bytes,
        operations: list[str] | None = None,
    ) -> bytes:
        """Apply a chain of enhancement operations and return JPEG bytes."""
        if operations is None:
            operations = ["denoise", "sharpen", "balance"]
        img = bytes_to_cv2(image_bytes)
        result = await asyncio.to_thread(self._apply_pipeline, img, operations)
        return cv2_to_jpeg_bytes(result, quality=90)

    def _apply_pipeline(self, img: np.ndarray, operations: list[str]) -> np.ndarray:
        """Apply operations sequentially."""
        for op in operations:
            match op:
                case "denoise":
                    img = self._denoise(img)
                case "sharpen":
                    img = self._sharpen(img)
                case "balance":
                    img = self._white_balance(img)
                case "super_resolve":
                    img = self._super_resolve(img)
                case _:
                    logger.warning("Unknown vision operation", extra={"op": op})
        return img

    @staticmethod
    def _denoise(img: np.ndarray) -> np.ndarray:
        """Non-local means denoising."""
        return cv2.fastNlMeansDenoisingColored(img, None, 10, 10, 7, 21)  # type: ignore[return-value]

    @staticmethod
    def _sharpen(img: np.ndarray) -> np.ndarray:
        """Unsharp mask sharpening."""
        gaussian = cv2.GaussianBlur(img, (0, 0), 3)
        return cv2.addWeighted(img, 1.5, gaussian, -0.5, 0)

    @staticmethod
    def _white_balance(img: np.ndarray) -> np.ndarray:
        """CLAHE-based adaptive white balance + contrast enhancement."""
        lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
        l_channel, a, b = cv2.split(lab)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        l_channel = clahe.apply(l_channel)
        lab = cv2.merge([l_channel, a, b])
        return cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)

    @staticmethod
    def _super_resolve(img: np.ndarray) -> np.ndarray:
        """Bicubic upscale (stub for Real-ESRGAN)."""
        h, w = img.shape[:2]
        return cv2.resize(img, (w * 2, h * 2), interpolation=cv2.INTER_CUBIC)
