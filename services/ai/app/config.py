from __future__ import annotations
import base64
import ipaddress
from urllib.parse import urlparse
from functools import lru_cache
from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore", case_sensitive=False)
    environment: str = "development"
    database_url: str = "postgresql+asyncpg://medrag_ai:medrag_ai@localhost:5432/medrag_ai"
    redis_url: str = "redis://localhost:6379/0"
    jwks_url: str = "http://localhost:8080/.well-known/internal-jwks.json"
    jwt_issuer: str = "medrag-api"
    jwt_audience: str = "medrag-ai"
    s3_endpoint: str = "http://localhost:9000"
    s3_access_key: str = "medrag"
    s3_secret_key: str = "medrag-password"
    s3_region: str = "us-east-1"
    s3_use_default_credentials: bool = False
    document_bucket: str = "medrag-documents"
    index_bucket: str = "medrag-indexes"
    s3_sse_algorithm: str = ""
    s3_kms_key_id: str = ""
    encryption_key_base64: str = Field(default="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    embedding_model: str = "NeuML/pubmedbert-base-embeddings"
    embedding_device: str = "cpu"
    embedding_batch_size: int = 16
    max_pages: int = 500
    max_extracted_chars: int = 2_000_000
    chunk_chars: int = 1800
    chunk_overlap_chars: int = 250
    query_max_chars: int = 2000
    max_top_k: int = 20
    llm_base_url: str = ""
    llm_api_key: str = "local-only"
    llm_model: str = ""
    llm_allowed_hosts: str = "localhost,127.0.0.1,::1,vllm,tgi,llm"
    llm_timeout_seconds: float = 45.0
    vault_addr: str = ""
    vault_token_file: str = "/vault/secrets/token"
    vault_namespace: str = ""
    vault_timeout_seconds: float = 3.0
    vault_cache_seconds: int = 60
    index_cache_seconds: int = 15
    lock_timeout_seconds: int = 180

    @property
    def encryption_key(self) -> bytes:
        return base64.b64decode(self.encryption_key_base64)

    @model_validator(mode="after")
    def validate_security(self) -> "Settings":
        if len(self.encryption_key) != 32:
            raise ValueError("ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes")
        production = self.environment.lower() in {"production", "prod"}
        if production and self.encryption_key == bytes(32):
            raise ValueError("Default encryption key is forbidden in production")
        if bool(self.llm_base_url) != bool(self.llm_model):
            raise ValueError("LLM_BASE_URL and LLM_MODEL must be configured together")
        if self.llm_base_url:
            parsed = urlparse(self.llm_base_url)
            if parsed.scheme not in {"http", "https"} or not parsed.hostname:
                raise ValueError("LLM_BASE_URL must be an absolute HTTP(S) URL")
            if parsed.username or parsed.password or parsed.query or parsed.fragment:
                raise ValueError("LLM_BASE_URL must not contain credentials, query, or fragment")
            if production and not self.llm_host_allowed(parsed.hostname):
                raise ValueError(
                    "Production LLM host is not private or explicitly allowed by LLM_ALLOWED_HOSTS"
                )
        return self

    def llm_host_allowed(self, hostname: str) -> bool:
        host = hostname.lower().rstrip(".")
        allowed = {
            candidate.strip().lower().rstrip(".")
            for candidate in self.llm_allowed_hosts.split(",")
            if candidate.strip()
        }
        if host in allowed or host.endswith((".internal", ".svc", ".cluster.local")):
            return True
        try:
            address = ipaddress.ip_address(host)
            return address.is_private or address.is_loopback or address.is_link_local
        except ValueError:
            return False

@lru_cache

def get_settings() -> Settings:
    return Settings()

settings = get_settings()
