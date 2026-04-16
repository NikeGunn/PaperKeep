from __future__ import annotations

import json
import logging
from typing import Any

from src.services.ocr_service import OCRService
from src.utils.s3 import download_from_s3, upload_to_s3

logger = logging.getLogger("scanvault.intelligence.worker.ocr")


async def process_ocr_task(ctx: dict, task_data: dict[str, Any]) -> dict[str, Any]:
    """Process an async OCR task from the Redis queue.

    task_data shape:
        task_id: str
        task_type: "ocr.enhance" | "ocr.table_extract" | "ocr.layout_analysis"
        input.s3_key: str
        input.params: dict
    """
    task_id = task_data["task_id"]
    task_type = task_data["task_type"]
    s3_key = task_data["input"]["s3_key"]
    params = task_data["input"].get("params", {})

    logger.info("Processing OCR task", extra={"task_id": task_id, "type": task_type})

    image_bytes = download_from_s3(s3_key)
    service = OCRService()

    languages = params.get("languages", ["en"])
    result = await service.extract_text_async(image_bytes, languages=languages)

    output = {
        "text": result.text,
        "blocks": result.blocks,
        "confidence": result.confidence,
        "language": "en" if result.text else "unknown",
    }

    output_key = s3_key.replace("/input.", "/output.").rsplit(".", 1)[0] + ".json"
    upload_to_s3(output_key, json.dumps(output).encode(), content_type="application/json")

    return {
        "task_id": task_id,
        "status": "completed",
        "output_s3_key": output_key,
        "metadata": {
            "confidence": result.confidence,
        },
    }
