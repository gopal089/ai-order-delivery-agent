# AI boundary and supported domain APIs

Verified 2026-10-03. This is a backend boundary, not a production AI deployment.
Subsequent 2026-10-04 changes add conversation create/send/history APIs, centralized instructions
and transactional message/audit persistence; see CHAT-AND-AI-SECURITY.md for the superseding details.
No real model/provider was added. Older no-chat/no-history-write statements below describe the
processes 16–20 batch only, not the current state.
Existing authentication, ownership guards, provider contracts, transport and V1–V7 are reused.
No AI SDK, dependency, schema, configuration, frontend or extension change was required.

## Supported authenticated HTTP surface

| GET route | Supported service read |
| --- | --- |
| `/api/v1/orders?afterId=0&limit=100` | Owned persisted order projections, ascending internal-ID keyset pagination; limit 1–100 |
| `/api/v1/orders/{orderId}` | Owned persisted order projection |
| `/api/v1/shipments/{shipmentId}` | Owned persisted shipment projection |
| `/api/v1/shipments/{shipmentId}/tracking-history` | Shipment-specific provider tracking history |
| `/api/v1/shipments/{shipmentId}/current-status` | Shipment-specific provider current status |
| `/api/v1/shipments/{shipmentId}/current-location` | Shipment-specific provider current location |

Order/shipment path IDs are internal database IDs. Tenant/user/session come only from the existing
authenticated context. Services reuse existing tenant/user ownership checks and scoped SQL.
Missing and foreign resources both retain the established 403 response; no bearer token gives 401.
Unknown query fields, duplicate query fields, nonpositive resource IDs, invalid pagination and GET
bodies are rejected with 400. Identity/credential-reference/provider-URL query fields are not accepted;
identity headers do not override authenticated context. Error responses preserve request-ID behavior.
Provider failures use a fixed safe message: deadline 504; missing/disabled/unconfigured/busy components
503; other provider failures 502. No original exception, credentials or raw provider payload is returned.

Persisted order/shipment projections are explicitly persisted application data, not fresh external facts.
Tracking/status/location retain existing provider provenance/source-observed/retrieved timestamps.
A shipment without an external shipment ID stays explicitly UNAVAILABLE; no cache/history fallback.
There is no new order ingestion, mutation, external order-list HTTP route or aggregate shipment selection.

## Model contract

`AiModelProvider.generate(AiModelContract.Request)` returns a typed response with either answer text
or controlled tool calls. Requests structurally separate fixed backend system instructions, current
user text, untrusted prior history and typed tool results. Optional provider/model metadata and optional
nonnegative token usage are supported. Result usage is explicitly final-call usage, not a fabricated sum.
The final answer is labeled MODEL_GENERATED_UNVERIFIED; this implementation does not certify grounding
or detect prompt injection. Trust labels are data boundaries, not a model-enforced security guarantee.

No production model bean or agent HTTP endpoint exists. Missing/ambiguous model configuration fails
closed with MODEL_UNAVAILABLE. The scripted provider lives exclusively under test sources and covers
plain answers, all five tools, repeated/malformed requests, failure and timeout. No real model is called.
LangChain4j/Bedrock were absent and are unnecessary for this provider-neutral boundary.

## Orchestration and authorization

`AgentOrchestrationService.execute(conversationId, integrationId, currentMessage)` authenticates,
reads owned conversation history and binds an owned integration before calling the model. Model inputs
contain no authenticated tenant/user/session IDs, credential references/material, provider selection or
transport capability. Model workers explicitly clear Spring security context and MDC. Only trusted
tool workers receive caller authentication; worker state is cleared afterward.

The enum allowlist has exactly `getCustomerOrders`, `getOrderDetails`, `getTrackingHistory`,
`getCurrentShipmentStatus`, `getCurrentPackageLocation`. Customer orders accepts no arguments; others
accept exactly one positive Integer/Long `orderId`. Strings/fractions, identity fields, URLs, credentials,
provider choices and arbitrary class names are rejected before invocation. Calls use the existing
`ControlledOrderToolFactory` and owned internal-order resolution, never reflection or arbitrary HTTP.
Tool ownership failures become ACCESS_DENIED; provider failure becomes PROVIDER_UNAVAILABLE;
timeout/failure/oversize result use fixed structured codes without original causes or messages.

Limits: three total tool calls (including failed calls), at most four model rounds, five seconds per
model call, twenty seconds per tool call, thirty seconds for the model/tool loop. Separate model/tool
pools each allow four workers and no queue; saturation fails closed. The thirty-second loop deadline
begins after synchronous authentication/history/integration preflight, not before database reads.
Java cancellation interrupts work but cannot forcibly stop interruption-ignoring code; such work remains
bounded by pool capacity. These are trusted backend adapters, not a sandbox for hostile Java plugins.

Character limits (Java UTF-16 units, not tokens): system/current/history message 4,000 each; recent
history 20 messages; tool result 32,768; combined model request 65,536; answer 8,192. Oversized context
fails closed rather than inventing summarization. Model outputs/requests/results omit content from
their diagnostic `toString`; this is not a universal filter for secrets users voluntarily paste into text
or a guarantee that future adapters cannot echo secrets. Adapters must uphold the credential-free contract.

## Conversation and data authority

`ConversationContextService` checks conversation tenant/user ownership then reads recent messages
with scoped SQL (21-row lookahead, return newest 20 in chronological order, explicit truncated flag).
Stored user/assistant roles become contextual roles; any other role, including stored `system`, becomes
OTHER. All stored text is UNTRUSTED_HISTORY. History does not authorize calls or execute tools.
There are no history writes, message/conversation APIs, embeddings, RAG, persistent semantic memory,
or inferred conversation lifecycle semantics in this batch.

Provider/tool data is EXTERNAL_UNTRUSTED as instruction-bearing text but remains the source for
current shipment facts. Provider retrieval remains fresh-per-call with no conversation/cache fallback.
Model-generated prose is not itself authoritative. A real model may still ignore instructions or invent
an answer: output grounding/evaluation and prompt-injection defenses are not claimed here.

## Remaining decisions and blockers

- Real provider endpoints/authentication/request/response documentation is missing.
- Production credential persistence remains blocked on deployment/ownership/key/recovery/retention policies.
- No real model adapter is configured; later SDK/serialization/tool-schema mapping needs separate approval.
- Source-age/clock-skew/freshness policy and caching remain undecided, not inferred.
- Provider order-list results contain external IDs, while tool arguments use internal IDs. Ingestion/mapping
  is not implemented: the agent cannot discover internal IDs from a provider list without later wiring.
- Existing order-level tools retain their contract; shipment HTTP routes use explicit internal shipment
  IDs. No arbitrary selection among multiple shipments or invented provider shipment semantics was added.
- No frontend agent flow, production AI endpoint, memory persistence, AWS, CI scanner or real-provider
  interoperability was implemented or verified.

See `PROVIDER-EXECUTION-VERIFICATION.md` for exact test/runtime evidence and limitations.
