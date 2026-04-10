from __future__ import annotations

import io

import numpy as np
import pytest


def _make_test_image(width: int = 800, height: int = 600) -> bytes:
    """Generate a simple white test image as JPEG bytes."""
    import cv2

    img = np.ones((height, width, 3), dtype=np.uint8) * 255
    # Draw some black text-like rectangles to simulate content
    cv2.rectangle(img, (50, 50), (750, 80), (0, 0, 0), -1)
    cv2.rectangle(img, (50, 100), (600, 130), (0, 0, 0), -1)
    _, buf = cv2.imencode(".jpg", img)
    return buf.tobytes()


@pytest.mark.skip(reason="Requires PaddleOCR model download — run in integration tests")
def test_enhanced_ocr_returns_200(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/ocr/enhanced",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
        params={"languages": "en", "detect_tables": False, "detect_layout": False},
    )
    assert response.status_code == 200
    data = response.json()
    assert "text" in data
    assert "confidence" in data
    assert "processing_time_ms" in data
