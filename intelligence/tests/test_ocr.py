from __future__ import annotations

# Task 3C.3 — Enhanced OCR endpoint tests
import io
from unittest.mock import MagicMock, patch

import numpy as np


def _make_test_image(width: int = 800, height: int = 600) -> bytes:
    """Generate a simple test image as JPEG bytes."""
    import cv2

    img = np.ones((height, width, 3), dtype=np.uint8) * 255
    cv2.rectangle(img, (50, 50), (750, 80), (0, 0, 0), -1)
    cv2.rectangle(img, (50, 100), (600, 130), (0, 0, 0), -1)
    _, buf = cv2.imencode(".jpg", img)
    return buf.tobytes()


def _make_large_image_bytes() -> bytes:
    """Return fake bytes exceeding the 50 MB limit."""
    return b"X" * (51 * 1024 * 1024)


# --- OCR endpoint ---


def test_ocr_returns_200_with_mocked_reader(client):
    """Mock EasyOCR so tests run without downloading models."""
    from src.services import ocr_service

    with patch.object(ocr_service, "_MAX_IMAGE_BYTES", 100 * 1024 * 1024):

        def mock_ensure(self, languages=None):
            self._reader_loaded = True
            mock_reader = MagicMock()
            # readtext returns (bbox, text, confidence) tuples
            mock_reader.readtext.return_value = [
                ([[10, 10], [100, 10], [100, 30], [10, 30]], "Hello World", 0.95)
            ]
            self._reader = mock_reader

        with patch.object(ocr_service.OCRService, "_ensure_reader", mock_ensure):
            image_bytes = _make_test_image()
            response = client.post(
                "/api/v1/ocr/",
                files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
            )
            assert response.status_code == 200
            data = response.json()
            assert "text" in data
            assert "blocks" in data
            assert "language" in data
            assert "confidence" in data


def test_ocr_response_fields_present(client):
    """Even with no OCR (reader=None), response has correct shape."""
    from src.services import ocr_service

    def mock_ensure(self, languages=None):
        self._reader_loaded = True
        self._reader = None  # triggers empty-result path

    with patch.object(ocr_service.OCRService, "_ensure_reader", mock_ensure):
        image_bytes = _make_test_image()
        response = client.post(
            "/api/v1/ocr/",
            files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
        )
        assert response.status_code == 200
        data = response.json()
        assert "text" in data
        assert "blocks" in data
        assert isinstance(data["blocks"], list)
        assert "language" in data
        assert "confidence" in data


def test_ocr_empty_image_returns_400(client):
    """Empty image file should return 400."""
    response = client.post(
        "/api/v1/ocr/",
        files={"image": ("empty.jpg", io.BytesIO(b""), "image/jpeg")},
    )
    assert response.status_code == 400


def test_ocr_corrupted_image_returns_400(client):
    """Corrupted bytes should return 400, not crash with 500."""
    response = client.post(
        "/api/v1/ocr/",
        files={"image": ("bad.jpg", io.BytesIO(b"not an image at all"), "image/jpeg")},
    )
    assert response.status_code == 400


def test_ocr_large_image_returns_413(client):
    """Images over 50 MB must return 413."""
    big_bytes = _make_large_image_bytes()
    response = client.post(
        "/api/v1/ocr/",
        files={"image": ("big.jpg", io.BytesIO(big_bytes), "image/jpeg")},
    )
    assert response.status_code == 413


def test_ocr_confidence_in_range(client):
    """Confidence must always be between 0.0 and 1.0."""
    from src.services import ocr_service

    def mock_ensure(self, languages=None):
        self._reader_loaded = True
        self._reader = None

    with patch.object(ocr_service.OCRService, "_ensure_reader", mock_ensure):
        image_bytes = _make_test_image()
        response = client.post(
            "/api/v1/ocr/",
            files={"image": ("test.jpg", io.BytesIO(image_bytes), "image/jpeg")},
        )
        data = response.json()
        assert 0.0 <= data["confidence"] <= 1.0


def test_ocr_lazy_loading_no_crash_on_first_call():
    """OCRService must initialise without crashing when easyocr is absent."""
    from src.services.ocr_service import OCRService

    svc = OCRService()
    assert svc._reader_loaded is False  # not yet loaded
    svc._ensure_reader()  # triggers lazy load attempt
    assert svc._reader_loaded is True  # now loaded (or gracefully set to None)


# --- OCRService unit tests ---


def test_ocr_service_extract_text_returns_empty_for_corrupted():
    """extract_text should return empty OCRResult for corrupt input, not raise."""
    from src.services.ocr_service import OCRService

    svc = OCRService()
    svc._reader_loaded = True
    svc._reader = None  # simulate no reader loaded
    result = svc.extract_text(b"garbage bytes that are not an image")
    assert result.text == ""
    assert result.blocks == []
    assert result.confidence == 0.0


def test_ocr_service_returns_empty_for_blank_image():
    """extract_text must not error on a valid but text-free image."""
    import cv2

    from src.services.ocr_service import OCRService

    # Fully white image — no text content
    img = np.ones((200, 200, 3), dtype=np.uint8) * 255
    _, buf = cv2.imencode(".jpg", img)
    image_bytes = buf.tobytes()

    svc = OCRService()
    svc._reader_loaded = True
    svc._reader = None  # no reader → empty result
    result = svc.extract_text(image_bytes)
    assert result.text == ""
    assert result.confidence == 0.0
