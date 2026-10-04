# Secure External Integration Configuration

## Implemented scope

Authenticated users can manage safe metadata for their own external order/tracking integrations:

- `POST /api/v1/integrations`;
- `GET /api/v1/integrations`;
- `GET /api/v1/integrations/{id}`;
- `PATCH /api/v1/integrations/{id}`;
- `DELETE /api/v1/integrations/{id}`.

Responses contain the integration ID, provider key, display name, normalized base URL, enabled state, a credential-configured boolean, credential type metadata, and timestamps. They never contain a credential-store reference or credential material. The API does not currently accept credential material because no approved secret-store implementation exists.

## Ownership boundary

Create and list operations derive tenant/user identity exclusively from `AuthenticatedUserContextProvider`. Read, update, and delete first call `TenantDataAuthorizationService.requireIntegrationAccess`, then use a repository query scoped by the same authenticated tenant/user tuple. Request DTOs have no tenant or user identity fields; client-supplied identity properties cannot override the security context.

## Credential boundary

`CredentialStore` defines backend-only store, retrieve, and delete operations scoped by tenant, user, and integration. `CredentialMaterial` copies mutable character arrays, redacts `toString()`, and supports explicit zeroization through `close()`. `CredentialReference` also redacts its string representation.

No `CredentialStore` implementation is registered yet. PostgreSQL stores only the nullable opaque `credential_reference` and non-secret `credential_type`; the database comments and constraints prohibit treating those columns as raw secret storage. Future credential configuration requires a separately approved secret-store implementation and service/API workflow.

The `ExternalOrderProvider` contract remains credential-free. Future provider resolution must happen in trusted backend code after authorization. AI tools, prompts, browser responses, and logs must never receive credential material or credential-store references.

## Base URL validation

Configuration-time validation:

- accepts only absolute hierarchical HTTP(S) URLs;
- requires HTTPS outside the `local` Spring profile;
- rejects URL user-info, query strings, and fragments;
- requires a valid normalized hostname and rejects single-label/local/internal names;
- rejects loopback, unspecified, private, carrier-grade NAT, link-local, multicast, and reserved literal ranges covered by the validator;
- rejects IPv6 unique-local, site-local, link-local, loopback, unspecified, and multicast literals;
- rejects ambiguous integer, hexadecimal, octal-like, and shortened numeric host forms;
- rejects encoded dot/backslash path forms and normalizes safe paths;
- performs no DNS lookup and makes no network connection.

### Important SSRF limitations

Configuration validation is not complete SSRF protection. A normal-looking public hostname can resolve to a private address, change answers between validation and connection, or redirect to a forbidden destination. When outbound HTTP transport is implemented, it must independently:

- resolve every A/AAAA result immediately before each connection and reject every forbidden address;
- pin the validated address for the connection while retaining correct TLS hostname verification;
- revalidate every redirect target or disable redirects;
- apply connection, response-size, and timeout limits;
- use network-level egress controls that block metadata, loopback, private, link-local, and other disallowed destinations;
- avoid untrusted proxy configuration and revalidate on every retry/re-resolution.

The later transport task added a backend-only OkHttp GET/HEAD boundary with connection-time DNS/IP validation, address pinning, disabled redirects/retries/proxies, TLS hostname checks, fixed timeouts, and bounded identity-encoded responses. See `PROVIDER-EXECUTION-VERIFICATION.md`. No real provider endpoint, authentication scheme, response mapper, or production credential store exists.

## Database and audit

`V6__secure_external_integration_configuration.sql` adds nullable `base_url`, `credential_reference`, and `credential_type` columns plus integrity constraints and security comments. The columns are nullable so the migration does not fabricate values for pre-existing rows. Application-created integrations require a base URL.

Create, update, and delete operations write fixed `INTEGRATION_CREATED`, `INTEGRATION_UPDATED`, and `INTEGRATION_DELETED` events through the centralized `AuditEventService`. Records use the authenticated tenant/user, integration ID, success outcome, and request ID. URLs, request bodies, credentials, credential references, keys, headers, and tokens are never written. Credential-reference lifecycle events remain deferred because credential operations do not yet exist. Audit writes share the integration transaction, so a required audit failure rolls back the integration mutation.
