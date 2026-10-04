# Conversation send and AI security boundary

Implemented/verified 2026-10-04. This extends existing AI contracts and ownership guards, not a
production model/provider, AWS deployment or factual-verification system.

## Authenticated API

| Operation | Contract |
| --- | --- |
| `POST /api/v1/conversations` | Required JSON `{}`; 201 with internal ID and creation time |
| `GET /api/v1/conversations/{conversationId}/messages` | Authorized bounded recent contextual history with truncation flag |
| `POST /api/v1/conversations/{conversationId}/messages` | JSON containing exactly positive integer `integrationId` and nonblank `message` of at most 4,000 characters |

Existing bearer authentication establishes tenant/user/session. Existing guards enforce conversation
and integration ownership. Integration selection is per-turn and limited to the caller's owned records;
no conversation/integration binding column or inferred status/title lifecycle was added. Create uses
schema defaults. Unknown JSON/query fields are rejected, including identities, credentials, URL,
provider and tool fields. Identity headers cannot override the principal. URLs inside user text are
context, not network permissions. No universal secret classifier is claimed: never submit credentials
or AWS secrets as conversation text.

Successful sends return message IDs, text, MODEL_GENERATED_UNVERIFIED, server-derived
externalDataRetrieved, supportStatus, controlled facts and tool summaries (name, retrieval success, fixed error code). No raw tool
data, credential references, model/provider metadata or token usage is exposed by chat. A successful
tool retrieval (even an empty provider result) sets the flag; failed/unauthorized/unavailable tools do
not. Model metadata cannot set it. Retrieval does not verify the model answer's factual correctness.
As of Processes 31–35, whitelisted provider fields are rendered by backend templates, with explicit
EXTERNAL_UNTRUSTED text provenance and source timestamps. EXTERNALLY_SUPPORTED describes only those
controlled fields, not withheld model prose or independent provider truth. See CONTROLLED-FACTUAL-RENDERING.md.

Missing/foreign conversation or foreign integration: established 403. No token: 401. Invalid request/
oversized context: 400. Missing model/saturation: 503. Model/loop deadline: 504. Unknown/malformed/
repeated model calls or model failure: safe 502. Persistence failure: safe 503. Existing request IDs
are preserved; custom errors do not contain raw causes, submitted values or database diagnostics.

## Persistence

Successful sends read owned prior history, pass the current message separately, then persist exactly
one user/assistant pair and tool-summary audit rows atomically. Failed model/limit/database/mandatory
audit execution rolls back the turn; no orphan user message. Model answers subsequently become
untrusted assistant history, not current provider facts or authorization. No model-reported model name
or provenance is promoted to persisted trusted metadata.

A scoped PostgreSQL row lock serializes successful concurrent turns. Lock wait is bounded at 2s;
chat SQL statements at 3s. The transaction remains open across bounded AI execution. Connection-pool
load/sizing is not production verified. No global lock, migration or conversation lifecycle is invented.
Tool activity uses existing transactional AuditEventService with AI_TOOL_EXECUTION and fixed summaries,
not prompts/arguments/provider payloads/secrets. The tool_executions table is not populated through a
competing logger. Failed overall turns roll back completion audit too; a separately durable attempt
ledger would require an approved lifecycle/transaction decision. Audit failure is fail-closed.

## Trust and budgets

SystemInstructions is centralized backend code. It keeps user/history/provider/tool content separate
from system instructions, prohibits secret disclosure/credential requests/fabrication, requires tools
for current state, distinguishes unavailable information and forbids unsupported verification claims.
No keyword blacklist or complete prompt-injection defense was added.

Existing enum/argument allowlists and server authorization remain. Tests simulate adversarial model
requests from hostile text and verify that arbitrary tools/classes/provider/URL/identity/credential
requests cannot bypass backend validation. Model workers have no authenticated context; only trusted
tool workers receive caller context, then clear it. These are trusted adapters, not hostile Java sandboxes.

Limits remain: model 5s, tool 20s, loop 30s after synchronous preflight; three total tools, at most four
model rounds; separate four-worker/no-queue pools. Cancellation is cooperative Java interruption.
Current/system/user/other-history text: 4,000 UTF-16 units; assistant history/response: 8,192; recent
history: 20 messages with truncation flag; combined request: 65,536; tool result: 32,768. Blank/oversize
answers/context are rejected; oversize tool results become safe failures. No invented token limits;
optional final-call usage stays internal. No summaries, semantic memory, RAG, embeddings or cache.

## Grounding counterexample

A malicious fake model emits “I checked the external system and your package is in Chennai.” without
a tool. The API returns MODEL_GENERATED_UNVERIFIED and externalDataRetrieved=false, and now withholds
the generated sentence. Only backend presentation is returned and persisted; public legacy
assistant/OTHER history is also withheld without rewriting old rows. Successful retrieval still does
not promote model prose. Controlled typed facts can now earn EXTERNALLY_SUPPORTED from their backend
execution evidence; no arbitrary prose claim classifier or PARTIALLY_SUPPORTED output is implemented.
Backend audit retains typed execution provenance separately from generated text. No confidence/accuracy
score or complete prompt-injection/semantic-verification guarantee exists. See
RESPONSE-GROUNDING-AND-BEDROCK-READINESS.md for the conservative fallback and current AWS inspection.

Tests cover hostile user/history/typed external responses, order status, tracking descriptions and tool
results. Order names/descriptions do not exist in the normalized contract; no imaginary fields/tests
were added. Current provider retrieval still does not fall back to persisted/cache/conversation state.

## Bedrock: blocked, not implemented

Build/settings/properties/environment template contain no AWS SDK/Bedrock dependency, adapter or
approved model/inference profile, region, authentication/IAM/model-access or inference API configuration.
Java toolchain is 21; AWS CLI reports 2.37.4. No SDK version was selected/installed; SDK compatibility,
timeout/error implementation and account/session/model access were not verified. No AWS calls ran.

BEDROCK IMPLEMENTATION BLOCKED — approve model/inference profile, region/data residency,
authentication/IAM/model access and API/configuration before SDK/version/timeout/tool-schema mapping.
BEDROCK RUNTIME NOT VERIFIED — no adapter/approved configuration and no real model invocation.

The Amazon Bedrock skill influenced the prerequisite check and pause; existing AiModelProvider remains
replaceable. Official [Converse documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html)
describes system/messages/tools, model ID and invocation permissions;
[Java credential chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html)
describes credential resolution. Neither establishes this project's AWS configuration or authentication.
No LangChain4j, AWS dependency, credentials or duplicate model abstraction was introduced.

## Runtime isolation

Normal bootJar contains no fake model/provider/store/test launcher. Without a real model, creation
works and send returns safe 503. Deterministic runtime verification uses a temporary separate test
fixture JAR, explicit test launcher/profile and the packaged application's classes/dependencies, not a
production fallback. Fixtures use synthetic material and no HTTP/AWS call; JAR/logs are removed.

See PROVIDER-EXECUTION-VERIFICATION.md for executed commands/counts/runtime/security evidence.
