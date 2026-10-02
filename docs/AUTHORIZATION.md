# Authorization boundary

Step 3 adds bearer access-token authentication and a reusable tenant/user ownership boundary. It does not add business-domain endpoints, PostgreSQL row-level security, or administrative roles.

## Request authentication

`/api/v1/auth/**` remains public for registration, login, refresh, and logout. Every other route is protected by Spring Security and requires an `Authorization: Bearer <access-token>` header. Missing or invalid authentication receives HTTP 401.

The resource server accepts only HMAC-SHA-256 JWTs verified with the existing access-token signing key. Validation requires:

- a valid signature using the configured algorithm and key;
- issuer `ai-order-delivery-agent`;
- required issued-at and expiration timestamps, with expiration after issuance and issued-at not materially in the future;
- a positive internal user ID in `sub`;
- a valid UUID session ID in `sid`;
- structurally valid JWT encoding.

## Server-resolved identity

The JWT identifies only the internal user and session. It does not carry authoritative tenant identity.

After cryptographic and claim validation, the authentication converter:

1. loads the user identified by `sub` from PostgreSQL;
2. rejects missing or inactive users;
3. obtains `tenant_id` exclusively from that server-side user row;
4. verifies that an unexpired, unrevoked refresh-token row exists for the exact tenant/user/session combination;
5. creates an authenticated `ApplicationPrincipal` containing user ID, tenant ID, and session ID.

This active-session check means logout, refresh-token reuse detection, and other session-family revocation immediately reject existing access tokens on subsequent requests, even if their JWT expiration has not yet arrived.

Request parameters, query parameters, headers, and request bodies cannot set or override the authenticated user, tenant, or session.

## Reusable application boundary

`AuthenticatedUserContextProvider` reads only Spring Security's trusted `SecurityContext` and exposes an immutable context containing:

- user ID;
- tenant ID;
- session ID;
- authenticated state.

`TenantAuthorization` provides reusable guards for future services:

- `requireTenant(resourceTenantId)` rejects resources outside the authenticated tenant;
- `requireOwner(resourceTenantId, resourceUserId)` rejects resources outside either the authenticated tenant or user.

Ownership failures throw Spring Security `AccessDeniedException` and produce HTTP 403. This mechanism must be called with ownership values loaded from authoritative server-side records, never values treated as authoritative merely because the client supplied them.

## Deliberately deferred

- Order, integration, conversation, message, shipment, and tracking authorization.
- Tenant membership and administrative/RBAC models.
- PostgreSQL RLS.
- Frontend and extension authentication integration.
- OAuth and external identity providers.

Future protected services must use the authenticated context and ownership guards without changing the `sub`/`sid` identity scheme.

The concrete ownership boundary for existing integration/order/conversation/message rows and future integration credentials is documented in `docs/TENANT-ISOLATION.md`.
