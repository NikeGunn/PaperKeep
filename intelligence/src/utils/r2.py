from __future__ import annotations

import logging

import boto3
from botocore.config import Config

from src.config import settings

logger = logging.getLogger("scanvault.intelligence.r2")


def get_r2_client():
    """Create an S3-compatible client for Cloudflare R2."""
    return boto3.client(
        "s3",
        endpoint_url=settings.r2_endpoint,
        aws_access_key_id=settings.r2_access_key,
        aws_secret_access_key=settings.r2_secret_key,
        region_name=settings.r2_region,
        config=Config(retries={"max_attempts": 3, "mode": "adaptive"}),
    )


def download_from_r2(r2_key: str) -> bytes:
    """Download a blob from the processing bucket."""
    client = get_r2_client()
    response = client.get_object(Bucket=settings.r2_bucket, Key=r2_key)
    return response["Body"].read()


def upload_to_r2(r2_key: str, data: bytes, content_type: str = "application/octet-stream") -> None:
    """Upload a result blob to the processing bucket."""
    client = get_r2_client()
    client.put_object(
        Bucket=settings.r2_bucket,
        Key=r2_key,
        Body=data,
        ContentType=content_type,
    )
    logger.info("Uploaded to R2", extra={"key": r2_key, "size": len(data)})
