# External Order Provider Abstraction

## Scope

`ExternalOrderProvider` is the provider-neutral backend boundary for reading authoritative order and shipment information. This task defines only the contract. It does not register a Spring bean, expose an endpoint, resolve credentials, perform an HTTP request, or model any real provider.

The five operations are:

- `getOrders(GetOrdersRequest)`;
- `getOrderDetails(GetOrderDetailsRequest)`;
- `getTrackingHistory(GetTrackingRequest)`;
- `getCurrentShipmentStatus(GetTrackingRequest)`;
- `getCurrentPackageLocation(GetTrackingRequest)`.

## Identity and authorization boundary

Every request carries an `ExternalOrderProviderContext` containing the authenticated tenant ID, user ID, session ID, and already-authorized integration ID. A future application service must create this context from `AuthenticatedUserContextProvider` and must call `TenantDataAuthorizationService.requireIntegrationAccess` before selecting or invoking a provider.

Client-supplied tenant, user, session, or integration ownership data is never authoritative. The provider abstraction does not itself replace the existing authentication and authorization boundary.

## Credential boundary

No request, response, context, exception, or interface method contains provider credentials. A future backend-owned provider resolver must retrieve credentials after authorization and bind them internally to a provider implementation. Credentials must not cross into an AI tool request or response, model context, browser response, log message, or exception message.

The future AI layer may invoke only controlled backend tools. It will not receive an `ExternalOrderProvider` instance, provider credentials, URLs, or arbitrary network access.

## Provider-neutral data

External order and shipment IDs are opaque typed values. Status, location, and timestamps are provider-reported values; the abstraction does not infer or manufacture them. Optional shipment IDs allow a documented provider to supply tracking at either order or shipment granularity. Result collections are immutable snapshots.

The contract intentionally contains no provider payload map or raw response body. The normalized detail shape is minimal until real provider documentation and approved business requirements exist.

## Errors

All provider failures derive from `ExternalOrderProviderException`:

- `ExternalProviderAuthenticationException`;
- `ExternalApiException`, with a backend-only retryable flag;
- `ExternalOrderNotFoundException`;
- `TrackingUnavailableException`;
- `UnsupportedProviderOperationException`.

Messages are deliberately generic and contain no identifiers, response bodies, or credentials. Future adapters may retain a cause for internal handling, but API/log redaction remains the responsibility of the application boundary.

## Deferred decisions

The backend execution boundary and secure GET/HEAD transport now exist; see `PROVIDER-EXECUTION-VERIFICATION.md`. No real adapter or credential store is registered. Real endpoint/authentication/response mapping, pagination/filtering, normalized detail expansion, caching, and credential persistence remain blocked on documented provider information and an approved secret-store design.
