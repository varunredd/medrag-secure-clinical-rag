from __future__ import annotations
import uuid
from datetime import datetime, timezone
from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, Identity, LargeBinary, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column
from app.db import Base

def now_utc() -> datetime: return datetime.now(timezone.utc)

class AiDocument(Base):
    __tablename__ = "ai_document"
    __table_args__ = (UniqueConstraint("tenant_id", "id", name="uq_ai_document_tenant_id"),)
    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    tenant_id: Mapped[str] = mapped_column(String(120), nullable=False, index=True)
    content_type: Mapped[str] = mapped_column(String(150), nullable=False)
    source_sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    embedding_model: Mapped[str] = mapped_column(String(255), nullable=False)
    status: Mapped[str] = mapped_column(String(30), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc, onupdate=now_utc, nullable=False)

class ClinicalChunk(Base):
    __tablename__ = "clinical_chunk"
    __table_args__ = (UniqueConstraint("tenant_id", "document_id", "ordinal", name="uq_chunk_document_ordinal"),)
    id: Mapped[int] = mapped_column(BigInteger, Identity(), primary_key=True)
    tenant_id: Mapped[str] = mapped_column(String(120), nullable=False, index=True)
    document_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ai_document.id", ondelete="CASCADE"), nullable=False)
    page: Mapped[int] = mapped_column(nullable=False)
    ordinal: Mapped[int] = mapped_column(nullable=False)
    text_ciphertext: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    text_nonce: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    embedding_ciphertext: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    embedding_nonce: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    content_sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc, nullable=False)
