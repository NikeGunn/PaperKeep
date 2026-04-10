from __future__ import annotations

import io

import numpy as np
import pytest


def _make_test_image(width: int = 640, height: int = 480) -> bytes:
    import cv2

    img = np.random.randint(50, 200, (height, width, 3), dtype=np.uint8)
    _, buf = cv2.imencode(".jpg", img)
    return buf.tobytes()


def test_enhance_returns_jpeg(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
        params={"operations": "denoise,sharpen,balance", "quality": "high"},
    )
    assert response.status_code == 200
    assert response.headers["content-type"] == "image/jpeg"
    assert "x-processing-time-ms" in response.headers
    # Verify it's a valid JPEG
    assert response.content[:2] == b"\xff\xd8"


def test_enhance_with_single_operation(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
        params={"operations": "denoise", "quality": "medium"},
    )
    assert response.status_code == 200
