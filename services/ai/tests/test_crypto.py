import os
from app.crypto import CryptoService

def test_aes_gcm_round_trip_and_aad_binding():
    service=CryptoService(os.urandom(32)); value=service.encrypt(b"clinical text",b"tenant-a")
    assert service.decrypt(value,b"tenant-a")==b"clinical text"
    try: service.decrypt(value,b"tenant-b")
    except Exception: pass
    else: raise AssertionError("AAD mismatch should fail")
