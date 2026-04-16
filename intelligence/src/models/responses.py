from __future__ import annotations

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str
    version: str = "0.1.0"
    uptime_seconds: float
    # Optional extended fields
    models_loaded: list[str] = Field(default_factory=list)
    gpu_available: bool = False
    worker_count: int = 1
    queue_depth: int = 0


class ClassifyResponse(BaseModel):
    type: str
    confidence: float


class BBox(BaseModel):
    x: float
    y: float
    w: float
    h: float


class ExtractedField(BaseModel):
    value: str
    confidence: float
    bbox: BBox | None = None


class ExtractFieldsResponse(BaseModel):
    fields: dict[str, ExtractedField | list[ExtractedField]]
    processing_time_ms: int


class LayoutBlock(BaseModel):
    type: str = Field(description="title, paragraph, table, figure, caption")
    text: str
    bbox: BBox
    confidence: float
    rows: list[list[str]] | None = None  # For table blocks


class EnhancedOCRResponse(BaseModel):
    text: str
    confidence: float
    language: str
    layout: list[LayoutBlock] | None = None
    processing_time_ms: int


class SummarizeResponse(BaseModel):
    summary: str
    model_used: str
    processing_time_ms: int


class TaskSubmittedResponse(BaseModel):
    task_id: str
    status: str = "pending"


class TaskStatusResponse(BaseModel):
    task_id: str
    status: str
    task_type: str
    output_s3_key: str | None = None
    result_metadata: dict | None = None
    error_message: str | None = None
    created_at: str
    started_at: str | None = None
    completed_at: str | None = None
