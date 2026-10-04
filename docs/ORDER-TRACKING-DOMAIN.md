# Order / shipment / tracking domain verification

## Actual schema and relationships

V2 already contains all required ownership fields. V4 adds users.password_hash; V5 adds refresh session
IDs; V6 adds integration base URL/credential reference/type; V7 changes anonymous audit ownership.
No migration is required for this batch and none was added or edited.

There is **no tenants table**, tenant entity or membership service. users.tenant_id is a UUID grouping
attribute, currently assigned at self-registration. The database can hold multiple users per tenant.
The actual ownership relationships are:

```
users(tenant_id, id)
  -> integrations(tenant_id, user_id, id)
     -> orders(tenant_id, user_id, id)
        -> shipments(tenant_id, user_id, id)
           -> tracking_events(tenant_id, user_id, shipment_id)
```

All arrows are composite foreign keys including tenant and user. Integrations reference users;
orders reference their integration; shipments reference their order; events reference their shipment.
Conversations separately reference users; messages reference an owned conversation. They are not an
authoritative shipment source and have no order/shipment foreign-key relationship.

Existing field inventory:

- users: id, tenant_id, email, display_name, is_active, password_hash, created_at, updated_at.
- integrations: id, tenant_id, user_id, provider_key, display_name, settings, is_enabled,
  base_url, credential_reference, credential_type, created_at, updated_at. settings is non-secret only.
- orders: id, tenant_id, user_id, integration_id, external_order_id, order_status, cached_payload,
  source_updated_at, fetched_at, cache_expires_at, created_at, updated_at.
- shipments: id, tenant_id, user_id, order_id, external_shipment_id, carrier, tracking_number,
  shipment_status, cached_payload, source_updated_at, fetched_at, cache_expires_at, created_at, updated_at.
- tracking_events: id, tenant_id, user_id, shipment_id, external_event_id, event_status,
  event_description, event_location, occurred_at, source_payload, created_at.
- conversations: id, tenant_id, user_id, title, status, created_at, updated_at.
- messages: id, tenant_id, user_id, conversation_id, message_role, content, model_name, created_at.

Internal keys are bigint identities. External order/shipment/event identifiers are separate text fields.
external_shipment_id and external_event_id are nullable. Domain methods do not accept external identifiers
as ownership authority. Existing users/integration JPA entities remain unchanged; no new JPA entity or
competing table was created. Read projections use scoped JdbcTemplate queries, consistent with existing
ownership metadata queries.

## Service boundaries

OrderDomainService lists **persisted application orders**, retrieves one owned order, and resolves the
owned internal ID to a backend-only integration/external-order reference. Listing is tenant/user scoped
and keyset-paginated (default/max page 100, explicit next cursor). This is a read-size safety limit, not
a cache TTL. Listing does not fetch or ingest external orders or silently create mappings.

Resolution uses TenantDataAuthorizationService.requireOrderAccess, scoped lookup and existing integration
ownership guard before execution can retrieve any credential. ControlledOrderToolFactory now reuses this
resolution rather than maintaining its own SQL lookup; it still rejects another bound integration.
The legacy typed tool contracts and provider operations were preserved.

ShipmentTrackingService reads persisted shipment details and one persisted tracking event, and requests
provider tracking history/current status/current location for an **internal shipment ID**. Existing
TenantAuthorization is reused through added shipment/event methods in TenantDataAuthorizationService.
Reads recheck parent order ownership and use tenant/user-scoped SQL. Foreign/missing resources produce
the same fixed AccessDeniedException, never an unavailable result that discloses existence.

For provider calls the service resolves owned shipment -> owned order -> owned integration, reads the
server-side external shipment identifier, and delegates to the existing bounded ExternalOrderExecutionService.
The existing service checks integration enablement/configuration/ownership before credential retrieval.
No method accepts tenant/user/session override, URL, credential material or reference. Domain results use
internal IDs and selected typed fields, not raw HTTP responses, cached_payload or source_payload.
No credential field enters the projections. Fixed ProviderExecutionException categories remain safe
errors with no original sensitive message/cause.

If a persisted shipment has no external_shipment_id, shipment-specific current data is explicitly
unavailable and no provider credential is retrieved. The service does not guess an order-wide aggregate
as that shipment's state. TRACKING_UNAVAILABLE or UNSUPPORTED map to empty values/events and UNAVAILABLE
provenance; API/auth/configuration/disabled/transport failures remain explicit safe errors. An empty
successful history is distinguishable from unavailable history by provenance. Locations are never
inferred from events, carrier, tracking number, database fields or conversation text.

No domain HTTP controller, ingestion/synchronization, cache writer, real provider, AWS store or AI
registration was introduced. Existing future tools still operate on internal **order** IDs and optional
order-level provider tracking; the new shipment-specific service is a distinct domain scope, not a
silent change to those established tool contracts. Product-approved multi-shipment selection/tool and
API exposure remain future decisions.

## Data authority and freshness

All public application projections include explicit provenance:

- PERSISTED_APPLICATION: database fields only. fetchedAt/sourceUpdatedAt describe stored metadata;
  this label never claims current remote status/location. No raw cache/source payload is returned.
- EXTERNAL_PROVIDER_RESPONSE: successfully retrieved typed provider fields, with backend retrievedAt
  and provider sourceObservedAt when supplied by the existing contract. Tracking history has no single
  authoritative source timestamp in the contract; individual events retain occurredAt.
- UNAVAILABLE: no fabricated status/location/events. The retrieval/attempt timestamp is recorded,
  but sourceObservedAt is absent. Provider failure is not converted to a persisted or memory fallback.

Every current status/location/history call requests the provider anew. Source timestamps are reported,
not replaced with now. A new retrieval does not prove the remote source observation is recent; no
maximum accepted source age/clock skew has been approved. Current means the response of the current
provider retrieval, not a fabricated freshness guarantee.

Redis currently supports authentication rate limits; there is no order/tracking cache implementation.
PostgreSQL has optional cache payload/expiry columns and integrity constraints, but these services do not
read or write their payloads. Existing conversation/message tables are contextual data only and never
queried by current-data methods. No cache, TTL or memory fallback was introduced.

CACHING DEFERRED — freshness policy requires explicit product decision.

Decisions needed before adding cache/ingestion/API/AI wiring: acceptable source age, clock-skew handling,
TTL/invalidation and failure behavior, provider-account ownership/ingestion rules, internal-ID mapping,
and multi-shipment selection. Real interoperability remains BLOCKED — provider contract missing.
Production secret persistence remains blocked as documented in CREDENTIAL-LIFECYCLE-AND-TOOL-BOUNDARY.md.

## Test evidence and scope

The original 185-test suite was rerun before modifications. New database/provider-domain tests authenticate
using the real registration/login endpoints and JWT decoder/session converter, then call backend services
with the trusted Spring principal. Only test fixture stores/providers are instantiated.

Tests cover scoped listing/keyset pagination; own/foreign/missing/same-tenant different-user orders,
shipments and tracking events; actual composite FK rejection using rolled-back savepoints; absence of a
tenants table; internal/external ID separation; absence of identity/URL/credential method inputs; raw
payload exclusion; successful tracking/status/location; explicit unavailable data; missing shipment
mapping; safe provider failures; disabled integrations before retrieval; repeated provider calls; and
persisted events, unexpired synthetic cache and conversation text never substituting for provider data.
The fixture cache's one-hour expiry is test-only poison data, not a production TTL decision.

Database test fixtures are transactional and rolled back. No real provider endpoint/schema/authentication
was invented. Packaged domain HTTP requests cannot be exercised because no order/tracking controller
was requested/introduced; authenticated domain behavior is verified in the Spring integration suite,
not claimed as a packaged HTTP endpoint. Final command counts/runtime/scan evidence are recorded after
execution in PROVIDER-EXECUTION-VERIFICATION.md.
