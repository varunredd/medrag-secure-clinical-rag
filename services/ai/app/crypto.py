from __future__ import annotations
import os
from dataclasses import dataclass
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from app.config import settings

@dataclass(frozen=True)
class EncryptedValue:
    ciphertext: bytes
    nonce: bytes

class CryptoService:
    def __init__(self, key: bytes = settings.encryption_key): self._aes = AESGCM(key)
    def encrypt(self, plaintext: bytes, aad: bytes) -> EncryptedValue:
        nonce = os.urandom(12)
        return EncryptedValue(self._aes.encrypt(nonce, plaintext, aad), nonce)
    def decrypt(self, value: EncryptedValue, aad: bytes) -> bytes:
        return self._aes.decrypt(value.nonce, value.ciphertext, aad)

crypto = CryptoService()
