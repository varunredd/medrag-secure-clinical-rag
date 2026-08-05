from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass
from functools import lru_cache
from typing import Annotated, Callable

import jwt
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field, ValidationError

from app.config import settings

bearer = HTTPBearer(auto_error=False)
MAX_INTERNAL_TOKEN_LIFETIME_SECONDS = 90


class TokenPayload(BaseModel):
    iss: str
    aud: str | list[str]
    sub: str = Field(min_length=1, max_length=200)
    exp: int
    iat: int
    nbf: int | None = None
    jti: str = Field(min_length=8, max_length=200)
    tenant_id: str = Field(pattern=r"^[A-Za-z0-9_-]{2,120}$")
    scope: str = Field(min_length=1, max_length=300)
    roles: list[str] = Field(default_factory=list, max_length=20)
    request_id: str = Field(min_length=1, max_length=100)


@dataclass(frozen=True)
class InternalPrincipal:
    actor_id: str
    tenant_id: str
    scopes: frozenset[str]
    roles: frozenset[str]
    request_id: str
    jti: str
    expires_at: int


@lru_cache(maxsize=1)
def jwk_client() -> jwt.PyJWKClient:
    return jwt.PyJWKClient(settings.jwks_url, cache_keys=True, lifespan=300)


def decode_internal_token(token: str) -> InternalPrincipal:
    try:
        key = jwk_client().get_signing_key_from_jwt(token).key
        raw = jwt.decode(
            token,
            key,
            algorithms=["RS256"],
            audience=settings.jwt_audience,
            issuer=settings.jwt_issuer,
            options={
                "require": [
                    "exp",
                    "iat",
                    "iss",
                    "aud",
                    "sub",
                    "jti",
                    "tenant_id",
                    "scope",
                    "request_id",
                ]
            },
            leeway=3,
        )
        payload = TokenPayload.model_validate(raw)
        now = int(time.time())
        if payload.exp - payload.iat > MAX_INTERNAL_TOKEN_LIFETIME_SECONDS:
            raise ValueError("Internal token lifetime exceeds policy")
        if payload.iat > now + 3:
            raise ValueError("Internal token issued in the future")
        return InternalPrincipal(
            actor_id=payload.sub,
            tenant_id=payload.tenant_id,
            scopes=frozenset(payload.scope.split()),
            roles=frozenset(payload.roles),
            request_id=payload.request_id,
            jti=payload.jti,
            expires_at=payload.exp,
        )
    except (jwt.PyJWTError, ValidationError, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal access token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc


def require_scope(scope: str) -> Callable[..., InternalPrincipal]:
    async def dependency(
        request: Request,
        credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
    ) -> InternalPrincipal:
        if credentials is None or credentials.scheme.lower() != "bearer":
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Bearer token required",
            )

        principal = await asyncio.to_thread(
            decode_internal_token,
            credentials.credentials,
        )
        if scope not in principal.scopes:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Insufficient internal scope",
            )
        if request.headers.get("X-Request-ID") != principal.request_id:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Internal request binding mismatch",
            )

        from app.locks import client as redis_client
        from redis import exceptions as redis_exceptions

        ttl = max(1, principal.expires_at - int(time.time()) + 3)
        try:
            first_use = await redis_client.set(
                f"medrag:internal-jti:{principal.jti}",
                "1",
                nx=True,
                ex=ttl,
            )
        except redis_exceptions.RedisError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Internal replay protection unavailable",
            ) from exc
        if not first_use:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Internal token replay detected",
            )

        request.state.principal = principal
        return principal

    return dependency


def assert_tenant(principal: InternalPrincipal, tenant_id: str) -> None:
    if principal.tenant_id != tenant_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Tenant binding mismatch",
        )
