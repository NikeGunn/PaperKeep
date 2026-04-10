from __future__ import annotations

import json
import logging
from typing import Any

from src.services.ai_service import AIService
from src.utils.r2 import download_from_r2, upload_to_r2

logger = logging.getLogger("scanvault.intelligence.worker.ai")


async def process_ai_task(ctx: dict, task_data: dict[str, Any]) -> dict[str, Any]:
    """Process an async AI task (summarization, extraction, embedding)."""
    task_id = task_data["task_id"]
    task_type = task_data["task_type"]
    r2_key = task_data["input"]["r2_key"]
    params = task_data["input"].get("params", {})

    logger.info("Processing AI task", extra={"task_id": task_id, "type": task_type})

    model_manager = ctx["model_manager"]
    service = AIService(model_manager)

    if task_type == "ai.summarize":
        text_bytes = download_from_r2(r2_key)
        text = text_bytes.decode("utf-8")
        result = await service.summarize(
            text=text,
            max_length=int(params.get("max_length", 200)),
            style=str(params.get("style", "bullet_points")),
            language=str(params.get("language", "en")),
        )
    elif task_type == "ai.extract_fields":
        import base64

        image_bytes = download_from_r2(r2_key)
        result = await service.extract_fields(
            image_b64=base64.b64encode(image_bytes).decode(),
            document_type=str(params.get("document_type", "unknown")),
            fields=list(params.get("fields", [])),
        )
    else:
        return {"task_id": task_id, "status": "failed", "error": f"Unknown task type: {task_type}"}

    output_key = r2_key.replace("/input.", "/output.").rsplit(".", 1)[0] + ".json"
    upload_to_r2(output_key, json.dumps(result).encode(), content_type="application/json")

    return {
        "task_id": task_id,
        "status": "completed",
        "output_r2_key": output_key,
        "metadata": result,
    }
