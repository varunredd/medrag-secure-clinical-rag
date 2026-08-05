from __future__ import annotations
from typing import Annotated
from fastapi import APIRouter, Depends, Response, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.db import get_session
from app.rag_service import ingest_document, purge_document, query_documents
from app.llm import GenerationPolicy
from app.schemas import IngestionRequest, PurgeRequest, QueryRequest, QueryResponse
from app.security import InternalPrincipal, assert_tenant, require_scope

router=APIRouter(prefix="/internal/v1",tags=["internal"])

@router.post("/ingestions",status_code=status.HTTP_204_NO_CONTENT)
async def ingest(req:IngestionRequest,principal:Annotated[InternalPrincipal,Depends(require_scope("ai:ingest"))],session:Annotated[AsyncSession,Depends(get_session)])->Response:
    assert_tenant(principal,req.tenant_id); await ingest_document(session,req.tenant_id,req.document_id,req.object_key,req.content_type,req.sha256); return Response(status_code=204)

@router.post("/documents/purge",status_code=status.HTTP_204_NO_CONTENT)
async def purge(req:PurgeRequest,principal:Annotated[InternalPrincipal,Depends(require_scope("ai:purge"))],session:Annotated[AsyncSession,Depends(get_session)])->Response:
    assert_tenant(principal,req.tenant_id); await purge_document(session,req.tenant_id,req.document_id); return Response(status_code=204)

@router.post("/query",response_model=QueryResponse,response_model_by_alias=True)
async def query(req:QueryRequest,principal:Annotated[InternalPrincipal,Depends(require_scope("ai:query"))],session:Annotated[AsyncSession,Depends(get_session)])->QueryResponse:
    assert_tenant(principal,req.tenant_id); return await query_documents(
        session,
        req.tenant_id,
        req.question,
        req.document_ids,
        req.top_k,
        GenerationPolicy(
            mode=req.llm_mode,
            endpoint_ref=req.llm_endpoint_ref,
            secret_ref=req.llm_secret_ref,
            model=req.llm_model,
        ),
    )
