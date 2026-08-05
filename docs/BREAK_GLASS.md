# Emergency break-glass access

MedRAG does not implement a permanent `BREAK_GLASS` role or an application-side privilege bypass. Emergency access must remain an identity-provider workflow that issues a short-lived token carrying the same clinical roles the responder is authorized to assume.

## Required claims

An emergency access token must contain:

```json
{
  "break_glass": true,
  "break_glass_reason_id": "INC-2048",
  "iat": 1720000000,
  "exp": 1720000300
}
```

The reason identifier is an incident/workflow reference, not free-text clinical data. Both the Next.js BFF and Spring API reject active break-glass tokens when the identifier is missing/unsafe or the token lifetime exceeds 15 minutes.

## Behavior

- Break-glass claims do **not** add roles or bypass route authorization.
- The dashboard displays a persistent emergency-access banner and expiry.
- Every Spring audit event during the emergency session is tagged with `breakGlass=true` and a SHA-256 of the reason identifier.
- Raw reason text is not copied to application logs or audit metadata.
- Refresh must return a newly valid break-glass token; old claims are never retained from a prior token.

## Identity-provider workflow

A real deployment should require strong re-authentication, reason selection, approval or dual control where feasible, a maximum 15-minute session, immediate revocation, and post-event review. Configure the IdP to assign only the minimum required existing role for the approved tenant and to emit the two claims above.

The local realm intentionally does not provide a break-glass user or self-service activation path.
