from __future__ import annotations

import cv2
import numpy as np


def bytes_to_cv2(image_bytes: bytes) -> np.ndarray:
    """Convert raw image bytes to an OpenCV BGR numpy array."""
    arr = np.frombuffer(image_bytes, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Could not decode image bytes")
    return img


def cv2_to_jpeg_bytes(img: np.ndarray, quality: int = 95) -> bytes:
    """Convert an OpenCV BGR image to JPEG bytes."""
    ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, quality])
    if not ok:
        raise ValueError("Failed to encode image to JPEG")
    return buf.tobytes()
