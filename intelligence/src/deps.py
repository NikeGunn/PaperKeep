from __future__ import annotations

from functools import lru_cache

from src.config import Settings, settings


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return settings
