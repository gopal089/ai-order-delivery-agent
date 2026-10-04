# AI Order & Delivery Agent

Secure, multi-tenant AI assistance for order and delivery information.

This repository is being built incrementally, with customer API credentials kept out of source control and AI model access.

## Status

The local backend implements registration/authentication, Redis abuse protection, token/session security, tenant/user ownership, request/audit observability, health probes, integration metadata CRUD, the external order contract, a guarded provider execution boundary, and a secure GET/HEAD HTTP transport. Real credential storage, provider adapters/calls, AI functionality, and cloud deployment are not implemented.

See `docs/PROJECT_HANDOFF.md` for the current state and the authentication/security documents under `docs/`. `docs/PROVIDER-EXECUTION-VERIFICATION.md` records the tested backend execution boundary, secure HTTP reader, and the explicit blockers for credential persistence and real provider adapters.
