from __future__ import annotations

# Task 3C.6 — S3 utility tests (all mocked — no real AWS calls)
from unittest.mock import MagicMock, patch

import pytest


def _make_mock_client(body: bytes = b"image data") -> MagicMock:
    """Return a fully-mocked boto3 S3 client."""
    client = MagicMock()
    # Simulate get_object response
    mock_body = MagicMock()
    mock_body.read.return_value = body
    client.get_object.return_value = {"Body": mock_body}
    return client


# --- get_s3_client ---


def test_get_s3_client_returns_client():
    """get_s3_client must return a boto3 client without making real calls."""
    import src.utils.s3 as s3_mod

    # Reset singleton so this test is isolated
    s3_mod._client = None
    with patch("boto3.client") as mock_boto:
        mock_boto.return_value = MagicMock()
        client = s3_mod.get_s3_client()
        assert client is not None
        mock_boto.assert_called_once()
    s3_mod._client = None  # clean up singleton


def test_get_s3_client_no_endpoint_url():
    """Client must NOT use a custom endpoint_url (standard AWS S3)."""
    import src.utils.s3 as s3_mod

    s3_mod._client = None
    with patch("boto3.client") as mock_boto:
        mock_boto.return_value = MagicMock()
        s3_mod.get_s3_client()
        call_kwargs = mock_boto.call_args
        # endpoint_url must NOT be in the kwargs
        assert "endpoint_url" not in (call_kwargs.kwargs or {})
    s3_mod._client = None


def test_get_s3_client_singleton():
    """Calling get_s3_client twice should return the same client object."""
    import src.utils.s3 as s3_mod

    s3_mod._client = None
    with patch("boto3.client") as mock_boto:
        mock_boto.return_value = MagicMock()
        c1 = s3_mod.get_s3_client()
        c2 = s3_mod.get_s3_client()
        assert c1 is c2
        assert mock_boto.call_count == 1
    s3_mod._client = None


# --- download_from_s3 ---


def test_download_from_s3_returns_bytes():
    """download_from_s3 should call get_object and return the body bytes."""
    import src.utils.s3 as s3_mod

    mock_client = _make_mock_client(b"hello world")
    s3_mod._client = mock_client

    result = s3_mod.download_from_s3("processing/account/task/input.jpg")

    assert result == b"hello world"
    mock_client.get_object.assert_called_once_with(
        Bucket=s3_mod.settings.s3_bucket_name,
        Key="processing/account/task/input.jpg",
    )
    s3_mod._client = None


def test_download_from_s3_missing_key_raises():
    """A missing key should propagate the boto3 ClientError."""
    from botocore.exceptions import ClientError

    import src.utils.s3 as s3_mod

    mock_client = MagicMock()
    mock_client.get_object.side_effect = ClientError(
        {"Error": {"Code": "NoSuchKey", "Message": "The specified key does not exist."}},
        "GetObject",
    )
    s3_mod._client = mock_client

    with pytest.raises(ClientError):
        s3_mod.download_from_s3("processing/missing/key.jpg")
    s3_mod._client = None


# --- upload_to_s3 ---


def test_upload_to_s3_calls_put_object():
    """upload_to_s3 should call put_object with correct args."""
    import src.utils.s3 as s3_mod

    mock_client = MagicMock()
    s3_mod._client = mock_client

    s3_mod.upload_to_s3("processing/task/output.json", b'{"result": 1}', "application/json")

    mock_client.put_object.assert_called_once_with(
        Bucket=s3_mod.settings.s3_bucket_name,
        Key="processing/task/output.json",
        Body=b'{"result": 1}',
        ContentType="application/json",
    )
    s3_mod._client = None


def test_upload_to_s3_default_content_type():
    """Default content type should be application/octet-stream."""
    import src.utils.s3 as s3_mod

    mock_client = MagicMock()
    s3_mod._client = mock_client

    s3_mod.upload_to_s3("processing/task/output.bin", b"\x00\x01\x02")

    call_kwargs = mock_client.put_object.call_args.kwargs
    assert call_kwargs["ContentType"] == "application/octet-stream"
    s3_mod._client = None


# --- s3_key_exists ---


def test_s3_key_exists_returns_true_when_found():
    import src.utils.s3 as s3_mod

    mock_client = MagicMock()
    mock_client.head_object.return_value = {}
    s3_mod._client = mock_client

    assert s3_mod.s3_key_exists("processing/task/input.jpg") is True
    s3_mod._client = None


def test_s3_key_exists_returns_false_when_missing():
    from botocore.exceptions import ClientError

    import src.utils.s3 as s3_mod

    mock_client = MagicMock()
    error_response = {"Error": {"Code": "404", "Message": "Not Found"}}
    # s3.py now catches botocore.exceptions.ClientError directly (not client.exceptions)
    mock_client.head_object.side_effect = ClientError(error_response, "HeadObject")
    s3_mod._client = mock_client

    assert s3_mod.s3_key_exists("processing/task/missing.jpg") is False
    s3_mod._client = None
