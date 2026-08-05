from __future__ import annotations
import uuid
from pydantic import BaseModel, Field, field_validator, model_validator
from app.config import settings

class IngestionRequest(BaseModel):
    tenant_id: str = Field(alias="tenantId", pattern=r"^[A-Za-z0-9_-]{2,120}$")
    document_id: uuid.UUID = Field(alias="documentId")
    object_key: str = Field(alias="objectKey", min_length=10, max_length=500)
    content_type: str = Field(alias="contentType", max_length=150)
    sha256: str = Field(pattern=r"^[a-f0-9]{64}$")
    model_config = {"populate_by_name": True}

class PurgeRequest(BaseModel):
    tenant_id: str = Field(alias="tenantId", pattern=r"^[A-Za-z0-9_-]{2,120}$")
    document_id: uuid.UUID = Field(alias="documentId")
    model_config = {"populate_by_name": True}

class QueryRequest(BaseModel):
    tenant_id: str = Field(alias="tenantId", pattern=r"^[A-Za-z0-9_-]{2,120}$")
    question: str = Field(min_length=3, max_length=settings.query_max_chars)
    document_ids: list[uuid.UUID] = Field(alias="documentIds", min_length=1, max_length=20)
    top_k: int = Field(default=8, alias="topK", ge=1, le=settings.max_top_k)
    llm_mode: str = Field(default="PLATFORM_PRIVATE", alias="llmMode", max_length=40)
    llm_endpoint_ref: str | None = Field(default=None, alias="llmEndpointRef", max_length=500)
    llm_secret_ref: str | None = Field(default=None, alias="llmSecretRef", max_length=500)
    llm_model: str | None = Field(default=None, alias="llmModel", max_length=255)
    model_config = {"populate_by_name": True}
    @field_validator("question")
    @classmethod
    def normalized_question(cls, value: str) -> str:
        return " ".join(value.split())

    @model_validator(mode="after")
    def validate_generation_policy(self) -> "QueryRequest":
        allowed = {"EXTRACTIVE", "PLATFORM_PRIVATE", "PRIVATE_OPENAI_COMPATIBLE"}
        self.llm_mode = self.llm_mode.upper().strip()
        if self.llm_mode not in allowed:
            raise ValueError("Unsupported generation policy")
        if self.llm_mode == "PRIVATE_OPENAI_COMPATIBLE":
            refs = (self.llm_endpoint_ref, self.llm_secret_ref)
            if not all(ref and ref.startswith("vault://") for ref in refs) or not self.llm_model:
                raise ValueError("Tenant-private mode requires vault references and model")
        return self

class Citation(BaseModel):
    document_id: uuid.UUID = Field(alias="documentId")
    page: int
    chunk_ordinal: int = Field(alias="chunkOrdinal")
    excerpt: str
    score: float
    model_config = {"populate_by_name": True, "serialize_by_alias": True}

class QueryResponse(BaseModel):
    answer: str
    citations: list[Citation]
    confidence: float
    embedding_model: str = Field(alias="embeddingModel")
    generation_model: str = Field(alias="generationModel")
    disclaimer: str
    model_config = {"populate_by_name": True, "serialize_by_alias": True}
