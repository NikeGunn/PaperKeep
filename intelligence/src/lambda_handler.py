from __future__ import annotations

from mangum import Mangum

from src.main import app

# Mangum wraps the FastAPI ASGI app for AWS Lambda + API Gateway
handler = Mangum(app, lifespan="off")
