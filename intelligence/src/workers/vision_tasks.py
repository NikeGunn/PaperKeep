from __future__ import annotations

import logging
from typing import Any

from src.services.vision_service import VisionService
from src.utils.r2 import download_from_r2, upload_to_r2

logger = logging.getLogger("scanvault.intelligence.worker.vision")


async def process_vision_task(ctx: dict, task_data: dict[str, Any]) -> dict[str, Any]:
    """Process an async vision enhancement task."""
    task_id = task_data["task_id"]
    task_type = task_data["task_type"]
    r2_key = task_data["input"]["r2_key"]
    params = task_data["input"].get("params", {})

    logger.info("Processing vision task", extra={"task_id": task_id, "type": task_type})

    image_bytes = download_from_r2(r2_key)
    model_manager = ctx["model_manager"]
    service = VisionService(model_manager)

    # Map task_type to operations
    ops_map: dict[str, list[str]] = {
        "vision.enhance": ["denoise", "sharpen", "balance"],
        "vision.dewarp": ["dewarp"],
        "vision.super_resolve": ["super_resolve"],
        "vision.cleanup": ["denoise", "balance"],
    }
    operations = ops_map.get(task_type, ["denoise", "sharpen", "balance"])
    quality = str(params.get("quality", "high"))

    enhanced_bytes = await service.enhance(image_bytes, operations=operations, quality=quality)

    # Write result to R2
    output_key = r2_key.replace("/input.", "/output.").rsplit(".", 1)[0] + ".jpg"
    upload_to_r2(output_key, enhanced_bytes, content_type="image/jpeg")

    return {
        "task_id": task_id,
        "status": "completed",
        "output_r2_key": output_key,
        "metadata": {
            "output_size_bytes": len(enhanced_bytes),
            "operations_applied": operations,
        },
    }
