import base64

import pytest
from pydantic import ValidationError

from app.config import Settings


def production_key() -> str:
    return base64.b64encode(bytes(range(32))).decode()


def test_production_rejects_unapproved_public_llm_host() -> None:
    with pytest.raises(ValidationError):
        Settings(
            environment="production",
            encryption_key_base64=production_key(),
            llm_base_url="https://public-llm.example.com",
            llm_model="clinical-model",
        )


def test_production_accepts_private_llm_host() -> None:
    settings = Settings(
        environment="production",
        encryption_key_base64=production_key(),
        llm_base_url="http://vllm:8000",
        llm_model="clinical-model",
    )

    assert settings.llm_model == "clinical-model"
