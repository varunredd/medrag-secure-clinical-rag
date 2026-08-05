# Identity Federation, Invitations, and Break-Glass Access

MedRAG deliberately does not create a second user directory in the clinical database. Users, invitations, MFA, account recovery, federation, and session revocation belong in the OIDC identity plane. The local Keycloak realm is a reference implementation.

## Production federation

- Connect Keycloak to the clinic's approved OIDC/SAML directory or replace it with an equivalent provider.
- Map immutable external organization membership to the `tenant_id` claim.
- Map only approved MedRAG roles into `realm_access.roles`: `DOCTOR`, `NURSE`, `CLINIC_ADMIN`, and `AUDITOR`.
- Emit audience `medrag-api` in access tokens. Do not rely on ID-token roles for API authorization.
- Require MFA and conditional access for Clinic Admin and Auditor roles.
- Configure exact web redirect and post-logout redirect URIs from `APP_BASE_URL`; do not use broad production wildcards.
- Test refresh-token claim mapping so tenant and role claims remain present after refresh.

## Invitation flow

Use the identity provider's invitation or lifecycle workflow. An invitation is not active until the identity system has verified the address, assigned the clinic tenant, and approved the role. MedRAG reads the resulting verified token claims; it does not accept a browser-supplied tenant or role.

## Break-glass

Create a separate, phishing-resistant emergency account with no routine use. Store credentials in an audited privileged-access-management system, require incident/change approval, issue time-bounded access, and alert on every login. Prefer an `AUDITOR` role for investigation; grant `CLINIC_ADMIN` only when an emergency change is required. MedRAG records login/logout and every sensitive action, but production must export those events to immutable storage/SIEM.

The included Auditor UX is read-only for clinical records and exposes audit and readiness views; it cannot upload, query, delete, redrive, or change tenant settings.
