# Authentication design

This document records the Step 2 token/session decisions. Step 3 bearer authentication and tenant-aware authorization are documented in `docs/AUTHORIZATION.md`.

## Endpoints

- `POST /api/v1/auth/register` retains its existing behavior.
- `POST /api/v1/auth/login` verifies email/password credentials and creates a session.
- `POST /api/v1/auth/refresh` rotates a refresh token and returns a new token pair.
- `POST /api/v1/auth/logout` revokes the refresh-token session and returns `204 No Content`.

Unknown email, incorrect password, inactive accounts, unknown refresh tokens, expired refresh tokens, revoked refresh tokens, and detected reuse all receive the same `401 AUTHENTICATION_FAILED` response. This prevents account and token-state disclosure.

## Access tokens

Access tokens are compact JWTs signed with HMAC-SHA-256 by Spring Security's JOSE support. They expire after 15 minutes and contain only:

- issuer;
- issued-at and expiration timestamps;
- the internal user ID as `sub`;
- the server-generated session ID as `sid`.

The signing key is read from `AUTH_ACCESS_TOKEN_SIGNING_KEY`. It must be a Base64-encoded random value that decodes to at least 32 bytes. There is no committed default. The application fails during startup when the key is absent, malformed, or too short.

Step 3 now verifies access tokens on protected requests and derives tenant identity from the server-side user record. An active refresh-token session must match the JWT's `sub` and `sid`, so logout or session-family revocation rejects the access token on subsequent requests even before JWT expiration.

## Refresh tokens and sessions

Refresh tokens are 32 cryptographically random bytes encoded using unpadded Base64 URL encoding. The raw value is returned to the caller only when a token is issued. It is never logged or persisted.

The database stores only a lowercase hexadecimal SHA-256 digest. A separate pepper is unnecessary because each refresh token has 256 bits of random entropy. The existing `refresh_tokens` table remains authoritative.

Migration V5 adds `session_id`. Every login creates a new random session ID. Every refresh-token rotation:

1. hashes the submitted token;
2. locks its database row;
3. rejects unknown, expired, or revoked tokens;
4. revokes the submitted token;
5. creates a new random token under the same session ID;
6. issues a new access token containing that session ID.

Refresh tokens expire after 30 days. Rotation does not extend a fixed original-session deadline; each successfully rotated token currently receives a new 30-day lifetime. This is a rolling session lifetime.

## Reuse detection and logout

Submitting an already-revoked refresh token is treated as reuse. The backend revokes every active refresh token with the same session ID before returning the generic authentication failure. Pessimistic row locking serializes concurrent rotation attempts.

Logout accepts the current refresh token and revokes its whole session family. It is idempotent and returns `204 No Content` even when the token is unknown or already revoked. This avoids disclosing token state.

## Spring Security boundary

Spring Security's `DaoAuthenticationProvider` performs password verification with the existing Argon2id `PasswordEncoder`. HTTP Basic, form login, server-side HTTP sessions, and Spring Security's built-in logout endpoint are disabled. The API is stateless and CSRF protection is disabled because authentication credentials are carried explicitly in JSON and no ambient cookie authentication is used.

All `/api/v1/auth/**` routes are public so clients can register and obtain/rotate/revoke tokens. Every other route requires a validated bearer token. No protected business-domain endpoint, role model, OAuth flow, or PostgreSQL RLS policy is implemented.
