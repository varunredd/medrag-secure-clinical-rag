from __future__ import annotations

import asyncio
from dataclasses import dataclass

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

from app.config import settings


@dataclass(frozen=True)
class StoredObject:
    body: bytes
    etag: str


class ObjectStorage:
    def __init__(self) -> None:
        kwargs: dict[str, object] = {
            "region_name": settings.s3_region,
            "config": Config(
                signature_version="s3v4",
                retries={"max_attempts": 3, "mode": "adaptive"},
            ),
        }
        if settings.s3_endpoint:
            kwargs["endpoint_url"] = settings.s3_endpoint
        if not settings.s3_use_default_credentials:
            kwargs["aws_access_key_id"] = settings.s3_access_key
            kwargs["aws_secret_access_key"] = settings.s3_secret_key
        self.client = boto3.client("s3", **kwargs)

    async def get(self, bucket: str, key: str) -> StoredObject:
        def operation() -> StoredObject:
            response = self.client.get_object(Bucket=bucket, Key=key)
            return StoredObject(
                response["Body"].read(),
                response.get("ETag", "").strip('"'),
            )

        return await asyncio.to_thread(operation)

    async def get_optional(self, bucket: str, key: str) -> StoredObject | None:
        try:
            return await self.get(bucket, key)
        except ClientError as error:
            if error.response.get("Error", {}).get("Code") in {
                "NoSuchKey",
                "404",
                "NotFound",
            }:
                return None
            raise

    async def put(
        self,
        bucket: str,
        key: str,
        body: bytes,
        content_type: str = "application/octet-stream",
    ) -> str:
        def operation() -> str:
            kwargs: dict[str, object] = {
                "Bucket": bucket,
                "Key": key,
                "Body": body,
                "ContentType": content_type,
            }
            if settings.s3_sse_algorithm:
                kwargs["ServerSideEncryption"] = settings.s3_sse_algorithm
                if settings.s3_kms_key_id:
                    kwargs["SSEKMSKeyId"] = settings.s3_kms_key_id
            response = self.client.put_object(**kwargs)
            return response.get("ETag", "").strip('"')

        return await asyncio.to_thread(operation)

    async def delete(self, bucket: str, key: str) -> None:
        await asyncio.to_thread(
            self.client.delete_object,
            Bucket=bucket,
            Key=key,
        )


storage = ObjectStorage()
