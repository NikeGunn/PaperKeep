from __future__ import annotations

import asyncio
import logging
import time
from typing import Any

from src.config import Settings

logger = logging.getLogger("scanvault.intelligence.models")


class ModelManager:
    """Lazy-loads ML models on first use. Thread-safe via asyncio.Lock.

    Models are kept in memory after loading and evicted after idle_timeout_minutes
    of inactivity to free RAM on a constrained VPS.
    """

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._models: dict[str, Any] = {}
        self._last_used: dict[str, float] = {}
        self._locks: dict[str, asyncio.Lock] = {}
        self._global_lock = asyncio.Lock()

    @property
    def gpu_available(self) -> bool:
        if not self._settings.enable_gpu:
            return False
        try:
            import torch

            return torch.cuda.is_available()
        except ImportError:
            return False

    def loaded_model_names(self) -> list[str]:
        return list(self._models.keys())

    async def _get_lock(self, name: str) -> asyncio.Lock:
        async with self._global_lock:
            if name not in self._locks:
                self._locks[name] = asyncio.Lock()
            return self._locks[name]

    async def get_ocr_model(self, languages: list[str] | None = None) -> Any:
        """Load PaddleOCR model for the specified languages."""
        lang_key = ",".join(sorted(languages or ["en"]))
        model_name = f"paddleocr:{lang_key}"

        lock = await self._get_lock(model_name)
        async with lock:
            if model_name in self._models:
                self._last_used[model_name] = time.monotonic()
                return self._models[model_name]

            logger.info("Loading PaddleOCR model", extra={"languages": lang_key})
            # Actual loading runs in a thread to avoid blocking the event loop
            model = await asyncio.to_thread(self._load_paddleocr, languages or ["en"])
            self._models[model_name] = model
            self._last_used[model_name] = time.monotonic()
            return model

    async def get_classifier(self) -> Any:
        """Load document classification model."""
        model_name = "classifier"
        lock = await self._get_lock(model_name)
        async with lock:
            if model_name in self._models:
                self._last_used[model_name] = time.monotonic()
                return self._models[model_name]

            logger.info("Loading document classifier")
            model = await asyncio.to_thread(self._load_classifier)
            self._models[model_name] = model
            self._last_used[model_name] = time.monotonic()
            return model

    async def get_summarizer(self) -> Any:
        """Load summarization model (BART-large-CNN)."""
        model_name = "summarizer"
        lock = await self._get_lock(model_name)
        async with lock:
            if model_name in self._models:
                self._last_used[model_name] = time.monotonic()
                return self._models[model_name]

            logger.info("Loading summarization model")
            model = await asyncio.to_thread(self._load_summarizer)
            self._models[model_name] = model
            self._last_used[model_name] = time.monotonic()
            return model

    def unload_unused(self, idle_minutes: int = 30) -> None:
        """Free memory for models not used recently."""
        cutoff = time.monotonic() - (idle_minutes * 60)
        to_remove = [name for name, last in self._last_used.items() if last < cutoff]
        for name in to_remove:
            logger.info("Unloading idle model", extra={"model": name})
            del self._models[name]
            del self._last_used[name]

    # --- Private model loaders (run in thread pool) ---

    @staticmethod
    def _load_paddleocr(languages: list[str]) -> Any:
        from paddleocr import PaddleOCR

        lang = languages[0] if languages else "en"
        return PaddleOCR(use_angle_cls=True, lang=lang, show_log=False)

    @staticmethod
    def _load_classifier() -> Any:
        # Stub: replace with actual model loading
        # Options: LayoutLMv3 fine-tuned classifier or a simple CNN
        logger.info("Classifier model stub loaded — replace with real model")
        return None

    @staticmethod
    def _load_summarizer() -> Any:
        from transformers import pipeline

        return pipeline("summarization", model="facebook/bart-large-cnn", device=-1)
