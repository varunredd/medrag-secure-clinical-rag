import uuid

import pytest

from app.llm import validate_model_output
from app.schemas import Citation


def citation(index: int) -> Citation:
    return Citation(
        documentId=uuid.uuid4(),
        page=index,
        chunkOrdinal=index,
        excerpt=f"Evidence {index}",
        score=0.9,
    )


def test_accepts_exact_grounded_json() -> None:
    result = validate_model_output(
        '{"answer":"Medication was changed [E1].","used_evidence":["E1"]}',
        [citation(1)],
    )
    assert result.answer == "Medication was changed [E1]."


def test_rejects_uncited_model_statement() -> None:
    with pytest.raises(ValueError):
        validate_model_output(
            '{"answer":"Medication was changed.","used_evidence":["E1"]}',
            [citation(1)],
        )

@pytest.mark.asyncio
async def test_explicit_extractive_policy_never_calls_model() -> None:
    from app.llm import GenerationPolicy, summarize

    result = await summarize("What changed?", [citation(1)], GenerationPolicy("EXTRACTIVE"))
    assert result.generation_model == "extractive-tenant-policy"


@pytest.mark.asyncio
async def test_tenant_private_policy_degrades_when_vault_unavailable() -> None:
    from app.llm import GenerationPolicy, summarize

    result = await summarize(
        "What changed?",
        [citation(1)],
        GenerationPolicy(
            "PRIVATE_OPENAI_COMPATIBLE",
            "vault://medrag/clinic-a/llm#endpoint",
            "vault://medrag/clinic-a/llm#api-key",
            "private-clinical-model",
        ),
    )
    assert result.generation_model == "extractive-vault-resolution-unavailable"
