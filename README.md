# AI Order & Delivery Agent

Secure, multi-tenant AI assistance for order and delivery information.

This repository is being built incrementally, with customer API credentials kept out of source control and AI model access.

## Status

The local backend currently implements user registration plus Spring Security credential authentication, short-lived access-token issuance, rotating hashed refresh tokens, refresh-token reuse detection, and logout/session revocation. Authorization, external integrations, AI functionality, and cloud deployment are not implemented.

See `docs/PROJECT_HANDOFF.md` for the complete current state and `docs/AUTHENTICATION.md` for the Step 2 token/session design.
