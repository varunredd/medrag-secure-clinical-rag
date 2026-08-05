from __future__ import annotations

import logging
import re
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, status
from fastapi.responses import ORJSONResponse
from prometheus_fastapi_instrumentator import Instrumentator
from sqlalchemy import text

from app.db import engine
from app.embeddings import dimension
from app.errors import DocumentConflictError, PermanentProcessingError, ServiceBusyError
from app.locks import client as redis_client
from app.routes import router
from app.config import settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
log = logging.getLogger("medrag.ai")


@asynccontextmanager
async def lifespan(_: FastAPI):
    async with engine.connect() as connection:
        await connection.execute(text("SELECT 1"))
    await redis_client.ping()
    model_dimension = await dimension()
    if model_dimension <= 0:
        raise RuntimeError("Embedding model returned an invalid vector dimension")
    log.info("AI service initialized embedding_dimension=%s", model_dimension)
    yield
    await redis_client.aclose()
    await engine.dispose()


app = FastAPI(
    title="MedRAG AI Internal API",
    version="0.1.0",
    docs_url=None if settings.environment.lower() in {"prod", "production"} else "/docs",
    default_response_class=ORJSONResponse,
    lifespan=lifespan,
)
app.include_router(router)
Instrumentator(excluded_handlers=["/health", "/metrics"]).instrument(app).expose(
    app,
    include_in_schema=False,
)


@app.middleware("http")
async def request_context(request: Request, call_next):
    header_request_id = request.headers.get("X-Request-ID")
    request_id = (
        header_request_id
        if header_request_id
        and re.fullmatch(r"[A-Za-z0-9._-]{1,100}", header_request_id)
        else str(uuid.uuid4())
    )
    request.state.request_id = request_id
    start = time.perf_counter()
    try:
        response = await call_next(request)
    except Exception as exc:
        log.warning(
            "request failed request_id=%s method=%s route=%s error_type=%s",
            request_id,
            request.method,
            request.url.path,
            type(exc).__name__,
        )
        raise
    response.headers["X-Request-ID"] = request_id
    log.info(
        "request completed request_id=%s method=%s route=%s status=%s duration_ms=%.1f",
        request_id,
        request.method,
        request.url.path,
        response.status_code,
        (time.perf_counter() - start) * 1000,
    )
    return response


@app.get("/health", include_in_schema=False)
async def health() -> dict[str, str | bool]:
    return {
        "status": "ok",
        "service": "medrag-ai",
        "embeddingModel": settings.embedding_model,
        "platformPrivateModelConfigured": bool(settings.llm_base_url and settings.llm_model),
        "tenantVaultResolverConfigured": bool(settings.vault_addr),
    }


@app.exception_handler(DocumentConflictError)
async def document_conflict(request: Request, exc: DocumentConflictError) -> ORJSONResponse:
    return problem(request, status.HTTP_409_CONFLICT, "DOCUMENT_CONFLICT", str(exc))


@app.exception_handler(PermanentProcessingError)
async def invalid_document(request: Request, exc: PermanentProcessingError) -> ORJSONResponse:
    return problem(
        request,
        status.HTTP_422_UNPROCESSABLE_ENTITY,
        "DOCUMENT_PROCESSING_REJECTED",
        str(exc),
    )


@app.exception_handler(ServiceBusyError)
async def service_busy(request: Request, exc: ServiceBusyError) -> ORJSONResponse:
    return problem(
        request,
        status.HTTP_503_SERVICE_UNAVAILABLE,
        "AI_SERVICE_BUSY",
        "AI service is temporarily busy",
    )


@app.exception_handler(Exception)
async def unhandled_error(request: Request, exc: Exception) -> ORJSONResponse:
    request_id = getattr(request.state, "request_id", "unknown")
    log.error(
        "unhandled error request_id=%s error_type=%s",
        request_id,
        type(exc).__name__,
    )
    return problem(
        request,
        status.HTTP_500_INTERNAL_SERVER_ERROR,
        "INTERNAL_AI_ERROR",
        "Internal AI service error",
    )


def problem(
    request: Request,
    http_status: int,
    code: str,
    detail: str,
) -> ORJSONResponse:
    return ORJSONResponse(
        status_code=http_status,
        content={
            "type": "about:blank",
            "title": "AI service error",
            "status": http_status,
            "detail": detail,
            "code": code,
            "requestId": getattr(request.state, "request_id", "unknown"),
        },
    )
