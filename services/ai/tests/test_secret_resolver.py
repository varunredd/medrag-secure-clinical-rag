import pytest

from app.secret_resolver import SecretResolutionError, parse_vault_reference


def test_parses_vault_kv_reference() -> None:
    assert parse_vault_reference("vault://medrag/clinic-a/llm#api-key") == (
        "medrag/clinic-a/llm",
        "api-key",
    )


def test_rejects_path_traversal_reference() -> None:
    with pytest.raises(SecretResolutionError):
        parse_vault_reference("vault://medrag/../secret#value")
