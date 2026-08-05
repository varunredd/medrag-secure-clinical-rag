from __future__ import annotations

import asyncio
import re
import time
from pathlib import Path
from typing import Any

import httpx

from app.config import settings

_VAULT_REFERENCE = re.compile(
    r"^vault://(?P<path>[A-Za-z0-9][A-Za-z0-9_./-]{0,400})#(?P<field>[A-Za-z0-9_.-]{1,100})$"
)


class SecretResolutionError(RuntimeError):
    """Safe operational error that never includes a resolved secret value."""


def parse_vault_reference(reference: str) -> tuple[str, str]:
    match = _VAULT_REFERENCE.fullmatch(reference.strip())
    if not match:
        raise SecretResolutionError("Invalid vault reference")
    path = match.group("path").strip("/")
    if not path or any(segment in {"", ".", ".."} for segment in path.split("/")):
        raise SecretResolutionError("Unsafe vault path")
    return path, match.group("field")


class VaultSecretResolver:
    def __init__(self) -> None:
        self._cache: dict[str, tuple[float, str]] = {}
        self._lock = asyncio.Lock()

    async def resolve(self, reference: str) -> str:
        cached = self._cache.get(reference)
        now = time.monotonic()
        if cached and cached[0] > now:
            return cached[1]

        async with self._lock:
            cached = self._cache.get(reference)
            now = time.monotonic()
            if cached and cached[0] > now:
                return cached[1]
            value = await self._fetch(reference)
            self._cache[reference] = (
                now + max(1, settings.vault_cache_seconds),
                value,
            )
            return value

    async def _fetch(self, reference: str) -> str:
        if not settings.vault_addr:
            raise SecretResolutionError("Vault resolver is not configured")
        path, field = parse_vault_reference(reference)
        try:
            token = Path(settings.vault_token_file).read_text(encoding="utf-8").strip()
        except OSError as error:
            raise SecretResolutionError("Vault token file is unavailable") from error
        if not token:
            raise SecretResolutionError("Vault token file is empty")

        headers = {"X-Vault-Token": token}
        if settings.vault_namespace:
            headers["X-Vault-Namespace"] = settings.vault_namespace
        try:
            async with httpx.AsyncClient(
                base_url=settings.vault_addr.rstrip("/"),
                timeout=settings.vault_timeout_seconds,
            ) as client:
                response = await client.get(f"/v1/{path}", headers=headers)
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError) as error:
            raise SecretResolutionError("Vault lookup failed") from error

        data: Any = payload.get("data") if isinstance(payload, dict) else None
        if isinstance(data, dict) and isinstance(data.get("data"), dict):
            data = data["data"]  # KV v2
        if not isinstance(data, dict) or not isinstance(data.get(field), str):
            raise SecretResolutionError("Vault field is missing or not text")
        value = data[field].strip()
        if not value:
            raise SecretResolutionError("Vault field is empty")
        return value


vault_resolver = VaultSecretResolver()
