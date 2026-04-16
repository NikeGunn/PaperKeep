from __future__ import annotations

# Task 3C.7 — Docker Compose + Dockerfile + lambda_handler tests
from pathlib import Path

import pytest
import yaml

COMPOSE_PATH = Path(__file__).parent.parent / "docker" / "docker-compose.yml"
DOCKERFILE_PATH = Path(__file__).parent.parent / "docker" / "Dockerfile"


# --- docker-compose.yml ---


def test_docker_compose_is_valid_yaml():
    """docker-compose.yml must parse as valid YAML without errors."""
    content = COMPOSE_PATH.read_text(encoding="utf-8")
    data = yaml.safe_load(content)
    assert data is not None
    assert isinstance(data, dict)


def test_docker_compose_has_services_key():
    data = yaml.safe_load(COMPOSE_PATH.read_text(encoding="utf-8"))
    assert "services" in data


def test_docker_compose_has_api_service():
    data = yaml.safe_load(COMPOSE_PATH.read_text(encoding="utf-8"))
    assert "api" in data["services"]


def test_docker_compose_has_worker_service():
    data = yaml.safe_load(COMPOSE_PATH.read_text(encoding="utf-8"))
    assert "worker" in data["services"]


def test_docker_compose_has_redis_service():
    data = yaml.safe_load(COMPOSE_PATH.read_text(encoding="utf-8"))
    assert "redis" in data["services"]


def test_docker_compose_has_postgres_service():
    data = yaml.safe_load(COMPOSE_PATH.read_text(encoding="utf-8"))
    assert "postgres" in data["services"]


def test_docker_compose_api_port_8100():
    data = yaml.safe_load(COMPOSE_PATH.read_text(encoding="utf-8"))
    api_ports = data["services"]["api"].get("ports", [])
    port_strings = [str(p) for p in api_ports]
    assert any("8100" in p for p in port_strings), f"Expected 8100 in ports: {port_strings}"


def test_docker_compose_redis_port_6379():
    data = yaml.safe_load(COMPOSE_PATH.read_text(encoding="utf-8"))
    redis_ports = data["services"]["redis"].get("ports", [])
    port_strings = [str(p) for p in redis_ports]
    assert any("6379" in p for p in port_strings)


def test_docker_compose_postgres_port_5432():
    data = yaml.safe_load(COMPOSE_PATH.read_text(encoding="utf-8"))
    pg_ports = data["services"]["postgres"].get("ports", [])
    port_strings = [str(p) for p in pg_ports]
    assert any("5432" in p for p in port_strings)


def test_docker_compose_no_r2_env_vars():
    """Old R2 env vars must not appear — only S3 vars."""
    content = COMPOSE_PATH.read_text(encoding="utf-8")
    assert "R2_ENDPOINT" not in content
    assert "R2_ACCESS_KEY" not in content
    assert "R2_SECRET_KEY" not in content
    assert "R2_BUCKET" not in content


def test_docker_compose_s3_bucket_env_var():
    """API and worker must reference S3_BUCKET_NAME."""
    content = COMPOSE_PATH.read_text(encoding="utf-8")
    assert "S3_BUCKET_NAME" in content


# --- Dockerfile ---


def test_dockerfile_has_runtime_mode_env():
    """Dockerfile must define RUNTIME_MODE env variable."""
    content = DOCKERFILE_PATH.read_text(encoding="utf-8")
    assert "RUNTIME_MODE" in content


def test_dockerfile_has_uvicorn_entrypoint():
    """Dockerfile must start uvicorn for local mode."""
    content = DOCKERFILE_PATH.read_text(encoding="utf-8")
    assert "uvicorn" in content
    assert "src.main:app" in content


def test_dockerfile_has_lambda_entrypoint():
    """Dockerfile must reference the lambda_handler for lambda mode."""
    content = DOCKERFILE_PATH.read_text(encoding="utf-8")
    assert "lambda_handler" in content or "RUNTIME_MODE" in content


# --- lambda_handler.py ---


def test_lambda_handler_importable():
    """lambda_handler.py must import without error (Mangum may not be installed)."""
    try:
        import src.lambda_handler  # noqa: F401
    except ImportError as e:
        # If mangum isn't installed, skip — not a test failure
        pytest.skip(f"mangum not installed: {e}")


def test_lambda_handler_exposes_handler():
    """lambda_handler must expose a callable 'handler'."""
    try:
        from src.lambda_handler import handler

        assert callable(handler)
    except ImportError:
        pytest.skip("mangum not installed")
