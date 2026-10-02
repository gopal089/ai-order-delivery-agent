# Tenant and user data isolation

This boundary protects the ownership metadata already present in the database without inventing domain APIs or business behavior.

## Existing schema

The `integrations`, `orders`, `conversations`, and `messages` tables already contain non-null `tenant_id` and `user_id` columns. Composite foreign keys also prevent children from referring to parents owned by another tenant/user. No schema change or migration was required.

The `integrations.settings` column is explicitly non-secret. There is no credential table, credential entity, credential metadata model, or secret-store integration. Credential access therefore remains unavailable; any future credential or credential-metadata service must first authorize access to its parent integration.

## Authorization boundary

`TenantOwnedResourceRepository` reads only `tenant_id` and `user_id` ownership metadata for a resource ID. It does not load domain payloads, message content, integration settings, order payloads, or credentials.

`TenantDataAuthorizationService` exposes one authorization operation for each currently represented resource boundary:

- `requireIntegrationAccess(integrationId)`;
- `requireOrderAccess(orderId)`;
- `requireConversationAccess(conversationId)`;
- `requireMessageAccess(messageId)`;
- `requireCredentialAccess(integrationId)`.

Each method passes the authoritative database ownership tuple to the existing `TenantAuthorization.requireOwner` guard. The guard compares both tenant and user against `AuthenticatedUserContextProvider`; it never accepts client-supplied identity as authority. Missing resources and resources belonging to another user both produce the same access-denied result, avoiding resource-existence disclosure.

The same guard must run before every future read, update, or delete. Domain repositories must not expose unscoped resource data before this ownership check.

## Verified isolation

Integration tests use test-only HTTP adapters and PostgreSQL fixtures; no production domain endpoint was added. The tests prove:

- owners can pass the boundary for their integrations, orders, conversations, messages, and integration-anchored credential access;
- users from another tenant cannot pass any resource boundary;
- a second user in the same tenant cannot pass another user's resource boundary;
- modification and delete authorization are rejected before a cross-user operation can execute;
- client-supplied `tenant_id` and `user_id` values cannot override the authenticated context.

## Deliberately deferred

- Domain entities, repositories, services, DTOs, controllers, and CRUD behavior.
- Actual credential or credential-metadata storage and retrieval.
- External provider calls and order/tracking behavior.
- PostgreSQL RLS and administrative/RBAC models.

When a domain service is implemented, it must reuse this boundary rather than creating a parallel ownership system.
