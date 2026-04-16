from __future__ import annotations

import logging

import boto3
from botocore.config import Config

from src.config import settings

logger = logging.getLogger("scanvault.intelligence.s3")

_client = None


def get_s3_client():
    """Create a standard AWS S3 client (no custom endpoint)."""
    global _client
    if _client is None:
        _client = boto3.client(
            "s3",
            region_name=settings.aws_region,
            config=Config(retries={"max_attempts": 3, "mode": "adaptive"}),
        )
    return _client


def download_from_s3(s3_key: str) -> bytes:
    """Download a blob from the S3 processing bucket."""
    client = get_s3_client()
    response = client.get_object(Bucket=settings.s3_bucket_name, Key=s3_key)
    return response["Body"].read()


def upload_to_s3(s3_key: str, data: bytes, content_type: str = "application/octet-stream") -> None:
    """Upload a result blob to the S3 processing bucket."""
    client = get_s3_client()
    client.put_object(
        Bucket=settings.s3_bucket_name,
        Key=s3_key,
        Body=data,
        ContentType=content_type,
    )
    logger.info("Uploaded to S3", extra={"key": s3_key, "size": len(data)})


def s3_key_exists(s3_key: str) -> bool:
    """Return True if the S3 key exists in the processing bucket."""
    client = get_s3_client()
    try:
        client.head_object(Bucket=settings.s3_bucket_name, Key=s3_key)
        return True
    except client.exceptions.ClientError:
        return False
