# Credential lifecycle, execution budget and future tool boundary

## Verified scope — 2026-10-03

This batch builds on the existing authentication, ownership guards, provider contract and secure
HTTP reader. It introduces no real provider, credential persistence, credential API, AWS dependency,
AI framework, business-domain endpoint, migration, frontend change or extension change.
Only deterministic test infrastructure supplies credentials and providers.

## Actual credential flow

1. ExternalOrderExecutionService obtains user/tenant/session from AuthenticatedUserContextProvider.
2. TenantDataAuthorizationService checks integration ownership through existing TenantAuthorization.
3. The integration is looked up by ID **and** authenticated tenant/user; missing and foreign IDs receive
   the same denial. Disabled/unconfigured integrations stop before credential access.
4. Credential ownership is checked against the integration; exactly one trusted backend adapter is
   resolved from the integration's persisted provider key. No store/adapter means fail closed.
5. A bounded backend worker retrieves CredentialMaterial using the complete CredentialScope and
   opaque CredentialReference. No client/tool can supply these values.
6. Only the trusted adapter receives validated base URI, credential material and SecureHttpTransport.
   Provider operation request/result models contain neither material nor credential references.
7. Material is closed and its owned char array zeroized on success, failure or execution deadline.
   Material returned after cancellation is closed before adapter use. Every HTTP call participates
   in the execution deadline/call count and registers active-call cancellation.

IntegrationResponse omits both material and reference. It returns only a configured boolean and
non-secret credential type alongside normal metadata. No AI implementation or model invocation exists.
The new tool interface has no credentials, headers, URLs or identity arguments. Backend exceptions
crossing execution use fixed categories with no provider/store message or cause; execution and transport
do not log request/response bodies, headers, materials or references. Tests exercise sensitive-marker
failure paths and existing integration-response/audit redaction.

Limits: zeroization covers CredentialMaterial's own char array, not all heap/client copies. Trusted
adapters must wipe char-array copies and avoid retention; HTTP header Strings cannot be reliably erased.
No real adapter exists, so conversion to actual provider authentication headers is NOT VERIFIED.
Key-based log/audit redaction is not a universal filter for arbitrary free text or third-party loggers.
Unknown provider schemas could echo secrets in ordinary text; real-adapter schema/field selection and
secret-safe mapping are BLOCKED on documentation. No blanket claim of future credential non-disclosure
or model prompt-injection resistance is made.

## Lifecycle contract

CredentialStore remains provider/cloud-neutral. `store`, `retrieve`, `rotate`, `delete` accept the complete
CredentialScope (tenant, user, integration). A store implementation must independently verify persisted
ownership for every reference operation. Supplied scope alone is not proof of ownership.

- Store: accept caller-owned closable material, persist in an approved secret backend, return only an
  opaque reference. Caller owns/cleans input and any copied arrays.
- Retrieve: match reference to complete persisted ownership; return a new caller-owned material lease.
- Rotate: same-scope replacement; success invalidates old reference, failure preserves old credential.
  The default implementation fails closed, preserving existing implementations without inventing storage.
- Delete: authorize parent integration and stored scope before invalidating the reference and applying
  the approved backend deletion policy. Recovery/retention/idempotency need an approved production policy.
- Reference: remain internal to backend persistence/execution, excluded from client/model/log/audit output.
- Ownership: future lifecycle orchestrator must call existing integration/credential guards before any
  store operation. The storage adapter must recheck stored scope. No competing authorization system.

ScopedTestCredentialStore is an in-memory **test contract fixture only**, never a Spring bean. Tests prove
scope denial (different tenant, same-tenant different user, different integration), successful/failed
rotation, old-reference invalidation, deletion and independent closable retrieval. These are not evidence
that production persistence or lifecycle audit orchestration is implemented.

### Production target — DESIGNED — NOT IMPLEMENTED

AWS Secrets Manager is the target behind CredentialStore, not exposed to domain/providers/tools. There
is no AWS SDK, infrastructure, approved account/region/roles, secret resource/reference ownership mapping,
key policy, retention/recovery policy, or deployment configuration in the repository.

Future orchestration must coordinate approved secret-backend operations with the existing integration
reference and transactional audit system. Define crash recovery/compensation for secret-store success
followed by database/audit failure, rotation concurrent with in-flight retrieval, reference publication,
old-version revocation, orphan cleanup and integration deletion. Do not assume cross-system atomicity.
The rotate contract is an application requirement, not a claim about an AWS primitive.

Audit design: allowlisted operation (store/rotate/delete, retrieval if required by approved policy),
authenticated tenant/actor, integration ID, outcome, fixed safe reason and request ID only. Never material,
opaque reference, backend locator, headers or arbitrary exception text. Future events must use the central
audit mechanism and approved failure/recovery semantics; no fake lifecycle events are emitted now.

Production persistence/lifecycle remains BLOCKED on those decisions. No plaintext database store,
encrypted credential table, fake production store, AWS integration or new migration was added.

## Execution limits

After synchronous authenticated ownership/configuration checks, the entire credential retrieval → adapter
creation → provider operation → response check has a 20-second monotonic deadline and at most five HTTP
attempts. Failed policy attempts also consume the budget. HTTP call timeout is min(15 seconds, remaining
execution time); existing 3-second connect, 5-second read/write, 2-second DNS and 1 MiB response controls
remain. No retry was added. Order-specific responses must match the requested order and, when supplied,
shipment ID. Null/wrong-resource responses are rejected safely.

At most four provider daemon workers, no waiting queue; saturation returns EXECUTION_BUSY. Cancellation
closes material and cancels active transport calls. Trusted Spring authentication is explicitly copied
to a worker and cleared afterwards; caller database transactions and arbitrary MDC are not propagated.
Future secret adapters must own their transaction boundaries and not depend on a caller-thread transaction.

Java cannot safely force-stop arbitrary adapter code that ignores interruption. The caller deadline and
HTTP cap are enforced, but such code can occupy a bounded worker until it exits. Four stuck workers cause
fail-closed saturation; process isolation/production operational recovery is NOT IMPLEMENTED. Adapters
are trusted code and must use the supplied transport synchronously, without unmanaged threads, alternate
HTTP clients, indefinite computation or retained credentials. The boundary is not a hostile Java sandbox.
Database preflight timing remains subject to existing DataSource behavior, outside the provider budget.

## Deterministic provider fixture

DeterministicTestOrderProvider lives exclusively under src/test. All five operations use fixed fixture
IDs, status/location labels and timestamp; no commercial provider, endpoint or auth scheme is represented.
Explicit scenarios cover not-found, tracking unavailable, authentication/API failure, null/wrong-resource
response and malicious instruction-like text. Existing/new execution tests cover disabled/unauthorized
integrations, tenant/user isolation, retrieval failure and secure-transport failure. No mock bean or mock
credential enters production configuration.

## Future tool boundary

ControlledOrderToolFactory is backend-only and binds an owned integration to the current authenticated
user/tenant/session. Expose **only** ControlledOrderTools to a future model, never the factory, execution
service, adapter registry, CredentialStore or transport. There are exactly five read operations:
getCustomerOrders(), getOrderDetails(long orderId), getTrackingHistory(long orderId),
getCurrentShipmentStatus(long orderId), getCurrentPackageLocation(long orderId).

Each call rechecks current binding and ownership. Order-specific calls take the existing internal numeric
orders.id; existing TenantDataAuthorizationService checks ownership and a scoped SQL lookup resolves
external_order_id only within the bound integration/tenant/user. Foreign, missing and another-integration
orders cannot reach credential retrieval. No string URL or identity/credential argument is accepted.
No order ingestion/cache management or order service/API was built. Therefore these methods require an
already-persisted owned order mapping. getCustomerOrders returns the existing provider result, whose
external IDs are **not** tool internal IDs; future order ingestion/selection must establish mappings before
order-specific tool use. A real-provider account's remote ownership semantics are NOT VERIFIED.

Results serialize inside UntrustedProviderData with trust=EXTERNAL_UNTRUSTED and a data field. Malicious
provider text remains a data string, never executed or assigned an instruction role; toString omits it.
There is no prompt builder/interpreter or AI invocation. Tests prove serialized trust labeling, absent
credential/context fields, rejected URL invocation, authenticated binding and cross-owner denial.
Future LangChain4j/Bedrock registration, strict JSON argument validation, instruction-role handling,
session validation, multi-tool agent-wide budget and prompt-injection evaluation are NOT IMPLEMENTED.
The current budget is per backend operation, not per future agent conversation. Do not cache tool objects
across authenticated requests or expose provider-selected URLs/implementations through AI arguments.

## Evidence

Baseline: ./gradlew test --rerun-tasks --console=plain, 151 passed, zero failed/errors/skipped.
Focused lifecycle/budget/execution/HTTP suite: 61 passed, zero failed/errors/skipped before the final
deadline/log/serialization assertions. One initial test compilation failed on an ambiguous AssertJ generic
overload; typed locals resolved it. Final evidence is recorded in PROVIDER-EXECUTION-VERIFICATION.md.
No schema, build dependency or runtime configuration was changed by this batch. V1–V7 remain untouched.
No commit or push is authorized or performed.

Subsequent processes 11–15 add read-only OrderDomainService/ShipmentTrackingService and shipment/event
ownership guards. The tool factory now reuses OrderDomainService for owned provider-reference resolution.
See ORDER-TRACKING-DOMAIN.md; no domain HTTP endpoint, ingestion, cache or real provider/store was added.
