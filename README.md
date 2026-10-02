# AI Order & Delivery Agent

Secure, multi-tenant AI assistance for order and delivery information.

This repository is being built incrementally, with customer API credentials kept out of source control and AI model access.

## Status

The local backend currently implements user registration, Spring Security credential authentication, short-lived access tokens, rotating hashed refresh tokens, session revocation, bearer-token request authentication, a reusable tenant-aware ownership boundary, and a provider-neutral external order/tracking contract. Real provider adapters and calls, business-domain services, AI functionality, and cloud deployment are not implemented.

See `docs/PROJECT_HANDOFF.md` for the complete current state, `docs/AUTHENTICATION.md` for token/session design, `docs/AUTHORIZATION.md` for the authenticated principal, `docs/TENANT-ISOLATION.md` for resource ownership enforcement, and `docs/EXTERNAL-ORDER-PROVIDER.md` for the provider contract.
