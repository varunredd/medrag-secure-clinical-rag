from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass

import faiss
import numpy as np

from app.config import settings
from app.crypto import EncryptedValue, crypto
from app.locks import client as redis_client
from app.storage import storage


@dataclass
class CacheEntry:
    etag: str
    expires: float
    index: faiss.Index


_cache: dict[str, CacheEntry] = {}


def index_key(tenant_id: str) -> str:
    return f"tenants/{tenant_id}/faiss/index.bin.enc"


def version_key(tenant_id: str) -> str:
    return f"medrag:index-version:{tenant_id}"


def aad(tenant_id: str) -> bytes:
    return f"medrag-faiss-v1:{tenant_id}".encode()


def empty_index(dimension: int) -> faiss.Index:
    return faiss.IndexIDMap2(faiss.IndexFlatIP(dimension))


async def save_index(tenant_id: str, index: faiss.Index) -> None:
    serialized = await asyncio.to_thread(faiss.serialize_index, index)
    encrypted = crypto.encrypt(serialized.tobytes(), aad(tenant_id))
    blob = encrypted.nonce + encrypted.ciphertext
    etag = await storage.put(settings.index_bucket, index_key(tenant_id), blob)
    await redis_client.set(version_key(tenant_id), etag)
    _cache[tenant_id] = CacheEntry(
        etag,
        time.monotonic() + settings.index_cache_seconds,
        index,
    )


async def load_index(tenant_id: str, dimension: int) -> faiss.Index:
    cached = _cache.get(tenant_id)
    current_etag = await redis_client.get(version_key(tenant_id))
    if (
        cached
        and cached.expires > time.monotonic()
        and current_etag
        and cached.etag == current_etag
    ):
        return cached.index

    stored = await storage.get_optional(settings.index_bucket, index_key(tenant_id))
    if stored is None:
        return empty_index(dimension)
    if cached and cached.etag == stored.etag:
        cached.expires = time.monotonic() + settings.index_cache_seconds
        return cached.index

    raw = crypto.decrypt(
        EncryptedValue(stored.body[12:], stored.body[:12]),
        aad(tenant_id),
    )
    index = await asyncio.to_thread(
        faiss.deserialize_index,
        np.frombuffer(raw, dtype=np.uint8),
    )
    _cache[tenant_id] = CacheEntry(
        stored.etag,
        time.monotonic() + settings.index_cache_seconds,
        index,
    )
    if current_etag != stored.etag:
        await redis_client.set(version_key(tenant_id), stored.etag)
    return index


def build_index(vectors: np.ndarray, ids: np.ndarray) -> faiss.Index:
    if vectors.ndim != 2:
        raise ValueError("Vectors must be a 2D matrix")
    index = empty_index(vectors.shape[1])
    if len(ids):
        index.add_with_ids(
            np.ascontiguousarray(vectors, dtype=np.float32),
            np.ascontiguousarray(ids, dtype=np.int64),
        )
    return index
