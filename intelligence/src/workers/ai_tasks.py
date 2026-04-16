from __future__ import annotations

import json
import logging
from typing import Any

from src.utils.s3 import download_from_s3, upload_to_s3

logger = logging.getLogger("scanvault.intelligence.worker.ai")


async def process_ai_task(ctx: dict, task_data: dict[str, Any]) -> dict[str, Any]:
    """Process an async AI task (summarization, extraction, embedding).

    Requires ctx["model_manager"] to be set by the worker startup hook.
    """
    task_id = task_data["task_id"]
    task_type = task_data["task_type"]
    s3_key = task_data["input"]["s3_key"]
    params = task_data["input"].get("params", {})

    logger.info("Processing AI task", extra={"task_id": task_id, "type": task_type})

    if task_type not in ("ai.summarize", "ai.extract_fields"):
        return {
            "task_id": task_id,
            "status": "failed",
            "error": f"Unknown task type: {task_type}",
        }

    # Lazy-import AIService + ModelManager so tests can mock them
    from src.config import settings
    from src.services.ai_service import AIService
    from src.services.model_manager import ModelManager

    model_manager = ctx.get("model_manager") or ModelManager(settings=settings)
    service = AIService(model_manager)

    if task_type == "ai.summarize":
        text_bytes = download_from_s3(s3_key)
        text = text_bytes.decode("utf-8")
        result = await service.summarize(
            text=text,
            max_length=int(params.get("max_length", 200)),
            style=str(params.get("style", "bullet_points")),
            language=str(params.get("language", "en")),
        )
    else:  # ai.extract_fields
        import base64

        image_bytes = download_from_s3(s3_key)
        result = await service.extract_fields(
            image_b64=base64.b64encode(image_bytes).decode(),
            document_type=str(params.get("document_type", "unknown")),
            fields=list(params.get("fields", [])),
        )

    output_key = s3_key.replace("/input.", "/output.").rsplit(".", 1)[0] + ".json"
    upload_to_s3(output_key, json.dumps(result).encode(), content_type="application/json")

    return {
        "task_id": task_id,
        "status": "completed",
        "output_s3_key": output_key,
        "metadata": result,
    }
