from __future__ import annotations

import asyncio
import secrets
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import redis.asyncio as redis

from app.config import settings
from app.errors import ServiceBusyError

client = redis.from_url(settings.redis_url, decode_responses=True)

_RELEASE = """
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('del', KEYS[1])
else
  return 0
end
"""

_EXTEND = """
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('expire', KEYS[1], ARGV[2])
else
  return 0
end
"""


@asynccontextmanager
async def tenant_lock(tenant_id: str) -> AsyncIterator[None]:
    key = f"medrag:index-lock:{tenant_id}"
    token = secrets.token_urlsafe(24)
    deadline = asyncio.get_running_loop().time() + settings.lock_timeout_seconds

    while not await client.set(key, token, nx=True, ex=settings.lock_timeout_seconds):
        if asyncio.get_running_loop().time() >= deadline:
            raise ServiceBusyError("Timed out acquiring tenant index lock")
        await asyncio.sleep(0.2)

    async def heartbeat() -> None:
        while True:
            await asyncio.sleep(max(1, settings.lock_timeout_seconds // 3))
            extended = await client.eval(
                _EXTEND,
                1,
                key,
                token,
                settings.lock_timeout_seconds,
            )
            if not extended:
                return

    task = asyncio.create_task(heartbeat())
    try:
        yield
    finally:
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass
        await client.eval(_RELEASE, 1, key, token)
