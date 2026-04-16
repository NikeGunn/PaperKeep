from __future__ import annotations

# Task 3C.1 — Health endpoint tests


def test_health_returns_200(client):
    response = client.get("/health")
    assert response.status_code == 200


def test_health_response_has_required_fields(client):
    response = client.get("/health")
    data = response.json()
    assert "status" in data
    assert "version" in data
    assert "uptime_seconds" in data


def test_health_status_is_healthy(client):
    response = client.get("/health")
    data = response.json()
    assert data["status"] == "healthy"


def test_health_version_is_string(client):
    response = client.get("/health")
    data = response.json()
    assert isinstance(data["version"], str)
    assert len(data["version"]) > 0


def test_health_uptime_is_non_negative_float(client):
    response = client.get("/health")
    data = response.json()
    assert isinstance(data["uptime_seconds"], float | int)
    assert data["uptime_seconds"] >= 0.0


def test_app_imports_without_error():
    """Verify the app module can be imported cleanly."""
    import src.main

    assert src.main.app is not None
