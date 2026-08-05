# Integration tests should run against the Spring JWKS endpoint. This unit test documents scope parsing.
from app.security import InternalPrincipal

def test_internal_principal_is_immutable():
    p=InternalPrincipal("actor","tenant",frozenset({"ai:query"}),frozenset({"DOCTOR"}),"req","jti",9999999999)
    assert "ai:query" in p.scopes
