from __future__ import annotations

import json
import logging
from typing import Any

from src.services.ocr_service import OCRService
from src.utils.r2 import download_from_r2, upload_to_r2

logger = logging.getLogger("scanvault.intelligence.worker.ocr")


async def process_ocr_task(ctx: dict, task_data: dict[str, Any]) -> dict[str, Any]:
    """Process an async OCR task from the Redis queue.

    task_data shape:
        task_id: str
        task_type: "ocr.enhance" | "ocr.table_extract" | "ocr.layout_analysis"
        input.r2_key: str
        input.params: dict
    """
    task_id = task_data["task_id"]
    task_type = task_data["task_type"]
    r2_key = task_data["input"]["r2_key"]
    params = task_data["input"].get("params", {})

    logger.info("Processing OCR task", extra={"task_id": task_id, "type": task_type})

    image_bytes = download_from_r2(r2_key)
    model_manager = ctx["model_manager"]
    service = OCRService(model_manager)

    languages = params.get("languages", ["en"])
    detect_tables = task_type == "ocr.table_extract" or params.get("enable_table_detection", False)
    detect_layout = task_type == "ocr.layout_analysis" or params.get("enable_layout_analysis", False)

    result = await service.recognize(
        image_bytes,
        languages=languages,
        detect_tables=detect_tables,
        detect_layout=detect_layout,
    )

    # Write result to R2
    output_key = r2_key.replace("/input.", "/output.").rsplit(".", 1)[0] + ".json"
    upload_to_r2(output_key, json.dumps(result).encode(), content_type="application/json")

    return {
        "task_id": task_id,
        "status": "completed",
        "output_r2_key": output_key,
        "metadata": {
            "confidence": result["confidence"],
            "language": result["language"],
        },
    }
