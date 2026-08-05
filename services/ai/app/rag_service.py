from __future__ import annotations

import asyncio
import hashlib
import uuid

import numpy as np
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.chunking import chunk_pages
from app.config import settings
from app.crypto import EncryptedValue, crypto
from app.embeddings import encode
from app.errors import DocumentConflictError, PermanentProcessingError
from app.extraction import extract
from app.index_store import build_index, load_index, save_index
from app.llm import GenerationPolicy, summarize
from app.locks import tenant_lock
from app.models import AiDocument, ClinicalChunk
from app.schemas import Citation, QueryResponse
from app.storage import storage

DISCLAIMER = (
    "AI-generated summary for clinician review. Verify all statements against "
    "the source record before clinical use."
)


def text_aad(tenant: str, document_id: uuid.UUID, ordinal: int) -> bytes:
    return f"chunk-text-v1:{tenant}:{document_id}:{ordinal}".encode()


def embedding_aad(tenant: str, document_id: uuid.UUID, ordinal: int) -> bytes:
    return f"chunk-embedding-v1:{tenant}:{document_id}:{ordinal}".encode()


def encrypt_vector(
    vector: np.ndarray,
    tenant: str,
    document_id: uuid.UUID,
    ordinal: int,
) -> EncryptedValue:
    return crypto.encrypt(
        np.asarray(vector, dtype=np.float32).tobytes(),
        embedding_aad(tenant, document_id, ordinal),
    )


def decrypt_vector(chunk: ClinicalChunk) -> np.ndarray:
    raw = crypto.decrypt(
        EncryptedValue(chunk.embedding_ciphertext, chunk.embedding_nonce),
        embedding_aad(chunk.tenant_id, chunk.document_id, chunk.ordinal),
    )
    return np.frombuffer(raw, dtype=np.float32)


def decrypt_text(chunk: ClinicalChunk) -> str:
    raw = crypto.decrypt(
        EncryptedValue(chunk.text_ciphertext, chunk.text_nonce),
        text_aad(chunk.tenant_id, chunk.document_id, chunk.ordinal),
    )
    return raw.decode("utf-8")


async def rebuild_index(session: AsyncSession, tenant_id: str) -> None:
    rows = (
        await session.execute(
            select(ClinicalChunk)
            .where(
                ClinicalChunk.tenant_id == tenant_id,
                ClinicalChunk.active.is_(True),
            )
            .order_by(ClinicalChunk.id)
        )
    ).scalars().all()

    if rows:
        vectors = np.stack([decrypt_vector(chunk) for chunk in rows]).astype(np.float32)
        ids = np.asarray([chunk.id for chunk in rows], dtype=np.int64)
        index = build_index(vectors, ids)
    else:
        probe = await encode(["dimension probe"])
        dimension = probe.shape[1]
        index = build_index(
            np.empty((0, dimension), dtype=np.float32),
            np.empty((0,), dtype=np.int64),
        )
    await save_index(tenant_id, index)


async def ingest_document(
    session: AsyncSession,
    tenant_id: str,
    document_id: uuid.UUID,
    object_key: str,
    content_type: str,
    sha256: str,
) -> None:
    expected_prefix = f"tenants/{tenant_id}/documents/{document_id}/"
    if not object_key.startswith(expected_prefix):
        raise PermanentProcessingError("Object key is outside the document tenant namespace")

    source = await storage.get(settings.document_bucket, object_key)
    if hashlib.sha256(source.body).hexdigest() != sha256:
        raise PermanentProcessingError("Source digest mismatch")

    pages = extract(source.body, content_type)
    chunks = chunk_pages(pages, settings.chunk_chars, settings.chunk_overlap_chars)
    vectors = await encode([chunk.text for chunk in chunks])

    async with tenant_lock(tenant_id):
        existing = await session.get(AiDocument, document_id)
        if existing and existing.tenant_id != tenant_id:
            raise PermanentProcessingError("Document tenant conflict")
        if existing and existing.status == "DELETED":
            raise DocumentConflictError("Document has been permanently tombstoned")

        if existing is None:
            session.add(
                AiDocument(
                    id=document_id,
                    tenant_id=tenant_id,
                    content_type=content_type,
                    source_sha256=sha256,
                    embedding_model=settings.embedding_model,
                    status="PROCESSING",
                )
            )
        else:
            existing.content_type = content_type
            existing.source_sha256 = sha256
            existing.embedding_model = settings.embedding_model
            existing.status = "PROCESSING"

        await session.execute(
            delete(ClinicalChunk).where(
                ClinicalChunk.tenant_id == tenant_id,
                ClinicalChunk.document_id == document_id,
            )
        )
        for chunk, vector in zip(chunks, vectors, strict=True):
            encrypted_text = crypto.encrypt(
                chunk.text.encode(),
                text_aad(tenant_id, document_id, chunk.ordinal),
            )
            encrypted_embedding = encrypt_vector(
                vector,
                tenant_id,
                document_id,
                chunk.ordinal,
            )
            session.add(
                ClinicalChunk(
                    tenant_id=tenant_id,
                    document_id=document_id,
                    page=chunk.page,
                    ordinal=chunk.ordinal,
                    text_ciphertext=encrypted_text.ciphertext,
                    text_nonce=encrypted_text.nonce,
                    embedding_ciphertext=encrypted_embedding.ciphertext,
                    embedding_nonce=encrypted_embedding.nonce,
                    content_sha256=hashlib.sha256(chunk.text.encode()).hexdigest(),
                    active=True,
                )
            )

        await session.flush()
        await session.commit()
        await rebuild_index(session, tenant_id)

        document = await session.get(AiDocument, document_id)
        if document is None or document.status == "DELETED":
            raise DocumentConflictError("Document was deleted during ingestion")
        document.status = "READY"
        await session.commit()


async def purge_document(
    session: AsyncSession,
    tenant_id: str,
    document_id: uuid.UUID,
) -> None:
    async with tenant_lock(tenant_id):
        document = await session.get(AiDocument, document_id)
        if document is not None and document.tenant_id != tenant_id:
            raise PermanentProcessingError("Document tenant conflict")

        await session.execute(
            delete(ClinicalChunk).where(
                ClinicalChunk.tenant_id == tenant_id,
                ClinicalChunk.document_id == document_id,
            )
        )
        if document is not None:
            document.status = "DELETED"
        else:
            session.add(
                AiDocument(
                    id=document_id,
                    tenant_id=tenant_id,
                    content_type="application/octet-stream",
                    source_sha256="0" * 64,
                    embedding_model=settings.embedding_model,
                    status="DELETED",
                )
            )

        await session.flush()
        await session.commit()
        await rebuild_index(session, tenant_id)


async def query_documents(
    session: AsyncSession,
    tenant_id: str,
    question: str,
    document_ids: list[uuid.UUID],
    top_k: int,
    generation_policy: GenerationPolicy | None = None,
) -> QueryResponse:
    query_vector = (await encode([question]))[0]

    ranked = await _rank_tenant_chunks(
        session,
        tenant_id,
        query_vector,
        top_k,
        allowed_documents=set(document_ids),
    )

    citations = [
        Citation(
            documentId=chunk.document_id,
            page=chunk.page,
            chunkOrdinal=chunk.ordinal,
            excerpt=(text[:700] + "…") if len(text) > 700 else text,
            score=round(score, 4),
        )
        for chunk, text, score in ranked
    ]
    summary = await summarize(question, citations, generation_policy)
    confidence = (
        0.0
        if not citations
        else max(0.0, min(1.0, float(np.mean([max(0, citation.score) for citation in citations]))))
    )
    return QueryResponse(
        answer=summary.answer,
        citations=citations,
        confidence=round(confidence, 4),
        embeddingModel=settings.embedding_model,
        generationModel=summary.generation_model,
        disclaimer=DISCLAIMER,
    )



async def _rank_tenant_chunks(
    session: AsyncSession,
    tenant_id: str,
    query_vector: np.ndarray,
    top_k: int,
    allowed_documents: set[uuid.UUID],
) -> list[tuple[ClinicalChunk, str, float]]:
    index = await load_index(tenant_id, query_vector.shape[0])
    if index.ntotal == 0:
        return []

    search_k = min(index.ntotal, max(top_k * 4, top_k))
    ranked: list[tuple[ClinicalChunk, str, float]] = []

    while True:
        scores, ids = await asyncio.to_thread(
            index.search,
            query_vector.reshape(1, -1).astype(np.float32),
            search_k,
        )
        candidate_ids = [int(chunk_id) for chunk_id in ids[0] if chunk_id >= 0]
        rows = (
            await session.execute(
                select(ClinicalChunk).where(
                    ClinicalChunk.tenant_id == tenant_id,
                    ClinicalChunk.id.in_(candidate_ids),
                    ClinicalChunk.active.is_(True),
                )
            )
        ).scalars().all()
        by_id = {chunk.id: chunk for chunk in rows}

        ranked = []
        for chunk_id, score in zip(ids[0], scores[0], strict=True):
            chunk = by_id.get(int(chunk_id))
            if chunk is None:
                continue
            if allowed_documents and chunk.document_id not in allowed_documents:
                continue
            ranked.append((chunk, decrypt_text(chunk), float(score)))
            if len(ranked) >= top_k:
                break

        if len(ranked) >= top_k or search_k >= index.ntotal:
            return ranked
        search_k = min(index.ntotal, search_k * 2)
