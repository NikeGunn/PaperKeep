from __future__ import annotations

# Task 3C.2 — Classification endpoint tests
import base64
import io

import numpy as np


def _make_test_image(width: int = 640, height: int = 480) -> bytes:
    """Generate a simple test image as JPEG bytes."""
    import cv2

    img = np.random.randint(50, 200, (height, width, 3), dtype=np.uint8)
    _, buf = cv2.imencode(".jpg", img)
    return buf.tobytes()


def _make_portrait_image() -> bytes:
    """A4-portrait-shaped image (likely classified as 'document')."""
    import cv2

    # width/height = ~0.7 → A4 portrait
    img = np.ones((1000, 700, 3), dtype=np.uint8) * 200
    _, buf = cv2.imencode(".jpg", img)
    return buf.tobytes()


# --- Multipart classify ---


def test_classify_returns_200(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/classify",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
    )
    assert response.status_code == 200


def test_classify_response_has_type_and_confidence(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/classify",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
    )
    data = response.json()
    assert "type" in data
    assert "confidence" in data


def test_classify_confidence_in_range(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/classify",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
    )
    data = response.json()
    assert 0.0 <= data["confidence"] <= 1.0


def test_classify_type_is_valid_string(client):
    image_bytes = _make_test_image()
    response = client.post(
        "/api/v1/classify",
        files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
    )
    data = response.json()
    valid_types = {"document", "receipt", "id_card", "whiteboard", "other", "unknown"}
    assert data["type"] in valid_types


def test_classify_corrupted_image_returns_not_500(client):
    """Corrupted image must NOT crash the service (return graceful result, not 500)."""
    response = client.post(
        "/api/v1/classify",
        files={"image": ("bad.jpg", io.BytesIO(b"not an image"), "image/jpeg")},
    )
    # Classifier falls back gracefully — returns 200 with type='other' or 422
    assert response.status_code in (200, 400, 422)
    if response.status_code == 200:
        data = response.json()
        assert "type" in data
        assert "confidence" in data


def test_classify_missing_image_field_returns_422(client):
    response = client.post("/api/v1/classify")
    assert response.status_code == 422


def test_classify_empty_image_returns_error(client):
    response = client.post(
        "/api/v1/classify",
        files={"image": ("empty.jpg", io.BytesIO(b""), "image/jpeg")},
    )
    assert response.status_code in (400, 422)


# --- JSON classify ---


def test_classify_json_returns_200(client):
    image_bytes = _make_test_image()
    b64 = base64.b64encode(image_bytes).decode()
    response = client.post(
        "/api/v1/classify/json",
        json={"image_base64": b64},
    )
    assert response.status_code == 200
    data = response.json()
    assert "type" in data
    assert "confidence" in data
    assert 0.0 <= data["confidence"] <= 1.0


def test_classify_json_invalid_base64_returns_422(client):
    response = client.post(
        "/api/v1/classify/json",
        json={"image_base64": "!!! not base64 !!!"},
    )
    assert response.status_code == 422


# --- Lazy loading ---


def test_classify_lazy_loading_does_not_crash_on_first_call(client):
    """Classifier must initialise on first request without raising."""
    from src.routers.classify import _get_classifier

    classifier = _get_classifier()
    assert classifier is not None
    assert classifier._model_loaded is True
