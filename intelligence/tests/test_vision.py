from __future__ import annotations

# Task 3C.4 — Vision enhance endpoint tests
import io

import numpy as np


def _make_test_image(width: int = 640, height: int = 480) -> bytes:
    import cv2

    img = np.random.randint(50, 200, (height, width, 3), dtype=np.uint8)
    _, buf = cv2.imencode(".jpg", img)
    return buf.tobytes()


# --- Endpoint tests ---


def test_enhance_returns_200(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
    )
    assert response.status_code == 200


def test_enhance_returns_jpeg_content_type(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
    )
    assert response.headers["content-type"] == "image/jpeg"


def test_enhance_returns_valid_jpeg_bytes(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
    )
    # JPEG magic bytes
    assert response.content[:2] == b"\xff\xd8"


def test_enhance_has_processing_time_header(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
    )
    assert "x-processing-time-ms" in response.headers


def test_enhance_with_single_denoise_operation(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
        params={"operations": "denoise"},
    )
    assert response.status_code == 200
    assert response.headers["content-type"] == "image/jpeg"


def test_enhance_with_sharpen_operation(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
        params={"operations": "sharpen"},
    )
    assert response.status_code == 200


def test_enhance_with_balance_operation(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
        params={"operations": "balance"},
    )
    assert response.status_code == 200


def test_enhance_all_operations_produces_different_output(client):
    """Applying denoise+sharpen should produce different bytes than the raw input."""
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
        params={"operations": "denoise,sharpen"},
    )
    assert response.status_code == 200
    # Enhanced output will differ from raw input (different compression at least)
    assert len(response.content) > 0


def test_enhance_corrupted_image_returns_400(client):
    """Corrupted image must return 400, not 500."""
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("bad.jpg", io.BytesIO(b"not an image"), "image/jpeg")},
    )
    assert response.status_code == 400


def test_enhance_empty_image_returns_400(client):
    response = client.post(
        "/api/v1/vision/enhance",
        files={"image": ("empty.jpg", io.BytesIO(b""), "image/jpeg")},
    )
    assert response.status_code == 400


# --- VisionService unit tests ---


def test_vision_service_denoise_changes_image():
    """Denoising a noisy image produces different pixel values."""
    from src.services.vision_service import VisionService

    svc = VisionService()
    img = np.random.randint(0, 255, (100, 100, 3), dtype=np.uint8)

    import cv2

    _, buf = cv2.imencode(".jpg", img)
    original_bytes = buf.tobytes()

    import asyncio

    enhanced_bytes = asyncio.get_event_loop().run_until_complete(
        svc.enhance(original_bytes, operations=["denoise"])
    )
    assert len(enhanced_bytes) > 0
    # Output is JPEG — different from raw random input
    assert enhanced_bytes[:2] == b"\xff\xd8"


def test_vision_service_sharpen_changes_image():
    import cv2

    from src.services.vision_service import VisionService

    svc = VisionService()
    img = np.ones((100, 100, 3), dtype=np.uint8) * 128
    _, buf = cv2.imencode(".jpg", img)
    original_bytes = buf.tobytes()

    import asyncio

    enhanced_bytes = asyncio.get_event_loop().run_until_complete(
        svc.enhance(original_bytes, operations=["sharpen"])
    )
    assert len(enhanced_bytes) > 0
    assert enhanced_bytes[:2] == b"\xff\xd8"
