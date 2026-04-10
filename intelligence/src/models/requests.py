from __future__ import annotations

from pydantic import BaseModel, Field


class ClassifyRequest(BaseModel):
    """Request body is multipart/form-data with image file — this is for JSON fallback."""
    image_b64: str | None = None


class ExtractFieldsRequest(BaseModel):
    image_b64: str = Field(..., description="Base64-encoded image")
    document_type: str = Field(..., description="Document type from classification")
    fields: list[str] = Field(
        default_factory=lambda: ["merchant", "date", "total", "currency"],
        description="Fields to extract",
    )


class SummarizeRequest(BaseModel):
    text: str = Field(..., min_length=10, max_length=100_000)
    max_length: int = Field(default=200, ge=50, le=1000)
    style: str = Field(default="bullet_points", pattern="^(bullet_points|paragraph|key_facts)$")
    language: str = Field(default="en", max_length=5)


class AsyncTaskRequest(BaseModel):
    task_type: str = Field(
        ...,
        pattern="^(ocr\\.|vision\\.|ai\\.)",
        description="Task type: ocr.enhance, vision.enhance, ai.summarize, etc.",
    )
    input_r2_key: str = Field(..., description="R2 key for the input blob")
    params: dict[str, str | int | float | bool | list[str]] = Field(default_factory=dict)
    priority: int = Field(default=5, ge=1, le=10)
