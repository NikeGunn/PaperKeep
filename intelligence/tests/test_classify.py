from __future__ import annotations

import io

import numpy as np
import pytest


def _make_test_image(width: int = 640, height: int = 480) -> bytes:
    """Generate a simple test image as JPEG bytes."""
    import cv2

    img = np.random.randint(0, 255, (height, width, 3), dtype=np.uint8)
    _, buf = cv2.imencode(".jpg", img)
    return buf.tobytes()


def test_classify_returns_200(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/classify",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
    )
    assert response.status_code == 200
    data = response.json()
    assert "document_type" in data
    assert "confidence" in data
    assert "processing_time_ms" in data
    assert 0.0 <= data["confidence"] <= 1.0


def test_classify_invalid_image(client):
    response = client.post(
        "/api/v1/classify",
        files={"image": ("test.jpg", io.BytesIO(b"not an image"), "image/jpeg")},
    )
    assert response.status_code == 500 or response.status_code == 422
