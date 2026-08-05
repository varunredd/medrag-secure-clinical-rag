from __future__ import annotations

import asyncio
from functools import lru_cache

import numpy as np
from sentence_transformers import SentenceTransformer

from app.config import settings

_encode_lock = asyncio.Lock()


@lru_cache(maxsize=1)
def model() -> SentenceTransformer:
    return SentenceTransformer(
        settings.embedding_model,
        device=settings.embedding_device,
    )


async def encode(texts: list[str]) -> np.ndarray:
    if not texts:
        return np.empty((0, 0), dtype=np.float32)
    async with _encode_lock:
        vectors = await asyncio.to_thread(
            model().encode,
            texts,
            batch_size=settings.embedding_batch_size,
            normalize_embeddings=True,
            show_progress_bar=False,
            convert_to_numpy=True,
        )
    return np.asarray(vectors, dtype=np.float32)


async def dimension() -> int:
    async with _encode_lock:
        value = await asyncio.to_thread(model().get_sentence_embedding_dimension)
    return int(value or 0)
