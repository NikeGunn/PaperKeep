from __future__ import annotations

import logging
from typing import Any

from src.services.vision_service import VisionService
from src.utils.s3 import download_from_s3, upload_to_s3

logger = logging.getLogger("scanvault.intelligence.worker.vision")


async def process_vision_task(ctx: dict, task_data: dict[str, Any]) -> dict[str, Any]:
    """Process an async vision enhancement task."""
    task_id = task_data["task_id"]
    task_type = task_data["task_type"]
    s3_key = task_data["input"]["s3_key"]
    _params = task_data["input"].get("params", {})

    logger.info("Processing vision task", extra={"task_id": task_id, "type": task_type})

    image_bytes = download_from_s3(s3_key)
    service = VisionService()

    # Map task_type to operations
    ops_map: dict[str, list[str]] = {
        "vision.enhance": ["denoise", "sharpen", "balance"],
        "vision.dewarp": ["denoise", "balance"],
        "vision.super_resolve": ["super_resolve"],
        "vision.cleanup": ["denoise", "balance"],
    }
    operations = ops_map.get(task_type, ["denoise", "sharpen", "balance"])

    enhanced_bytes = await service.enhance(image_bytes, operations=operations)

    output_key = s3_key.replace("/input.", "/output.").rsplit(".", 1)[0] + ".jpg"
    upload_to_s3(output_key, enhanced_bytes, content_type="image/jpeg")

    return {
        "task_id": task_id,
        "status": "completed",
        "output_s3_key": output_key,
        "metadata": {
            "output_size_bytes": len(enhanced_bytes),
            "operations_applied": operations,
        },
    }
