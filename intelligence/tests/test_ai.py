from __future__ import annotations

import pytest


@pytest.mark.skip(reason="Requires model download — run in integration tests")
def test_summarize_returns_200(client):
    response = client.post(
        "/api/v1/ai/summarize",
        json={
            "text": "This is a long document about legal matters. " * 50,
            "max_length": 100,
            "style": "bullet_points",
            "language": "en",
        },
    )
    assert response.status_code == 200
    data = response.json()
    assert "summary" in data
    assert "model_used" in data
