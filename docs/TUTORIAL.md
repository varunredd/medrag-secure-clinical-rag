# Synthetic Happy-Path Tutorial

1. Run `./scripts/bootstrap-dev.sh`.
2. Start the durable development stack with `make up-dev` or the immutable production-like stack with `make up`.
3. Open `http://localhost:3000` and sign in as `doctor@demo.medrag.local` using the local-only password in the README.
4. Open **Clinical records** and upload `samples/synthetic-clinical-note.txt`.
5. Watch the state move through `QUEUED` → `PROCESSING` → `READY`. If it fails, the UI shows a failure code and support code; Doctor or Clinic Admin can retry.
6. Open **Ask MedRAG**, explicitly select the ready synthetic record, and ask: `What follow-up and urgent-care instructions are documented?`
7. Review the answer, confidence, page/chunk citations, and disclaimer. With no private LLM configured, MedRAG clearly returns the grounded extractive mode rather than silently calling a public provider.
8. Sign out. The BFF session is deleted first and the browser is sent through the Keycloak end-session endpoint, preventing an immediate silent SSO bounce.

Use synthetic data only in local environments.
