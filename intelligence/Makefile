.PHONY: dev test lint type-check format docker-up docker-down clean

# Local development (requires Python 3.12+ and dependencies installed)
dev:
	uvicorn src.main:app --host 0.0.0.0 --port 8100 --reload

# Run tests (skips integration tests that need model downloads)
test:
	pytest tests/ -v --tb=short -m "not skip"

# Run all tests including integration
test-all:
	pytest tests/ -v --tb=short

# Lint
lint:
	ruff check src/ tests/
	ruff format --check src/ tests/

# Type check
type-check:
	pyright src/

# Format
format:
	ruff check --fix src/ tests/
	ruff format src/ tests/

# Docker
docker-up:
	docker compose -f docker/docker-compose.yml up --build -d

docker-down:
	docker compose -f docker/docker-compose.yml down

# Worker (local, without Docker)
worker:
	arq src.workers.worker.WorkerSettings

# Clean
clean:
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete 2>/dev/null || true
	rm -rf .pytest_cache .ruff_cache
