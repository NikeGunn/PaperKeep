from __future__ import annotations

from functools import lru_cache

from src.config import Settings, settings
from src.services.model_manager import ModelManager


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return settings


@lru_cache(maxsize=1)
def get_model_manager() -> ModelManager:
    return ModelManager(settings=get_settings())
