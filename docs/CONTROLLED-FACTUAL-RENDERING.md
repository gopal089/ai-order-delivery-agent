# Controlled factual rendering and persistence

Processes 31–35, 2026-10-04. Supersedes the earlier withhold-all presentation, not the model trust rule.

## Authority and rendering

ControlledFact projects only existing typed tool returns, at the actual authorized backend execution
point. It does not parse model prose, model metadata or serialized ToolResult JSON. Allowed fields are
order status, shipment status, tracking status/location and package location, with the actual optional
source timestamp. No ETA exists in the inspected normalized provider contract; none is introduced.
Tracking descriptions, credentials, opaque credential references, IDs and arbitrary payloads are not
part of the projection. Collection entries remain in provider order; no sorting/freshness inference.

ResponseGroundingBoundary requires each projected field's tool sequence/name to match a successful
backend RetrievalEvidence. It renders fixed labels and JSON-quoted provider values plus source times.
Public facts explicitly carry EXTERNAL_UNTRUSTED valueTrust: field origin is supported, but provider
text is not instructions, independently verified truth, HTML or Markdown. Consumers must display it
as plain text, not execute/render it as markup. No keyword blacklist is used. Backend orchestration
never interprets the rendered strings as tool calls/URLs/credentials/security decisions.

EXTERNALLY_SUPPORTED now means the public **controlled factual portion** contains provider fields
whose origin is evidenced by successful current execution. It does not endorse the model's answer:
generatedText remains internal and is withheld in every case, including correct model prose. Tool
success without projected present fields cannot earn this classification. Missing fields are explicitly
unavailable; failed location retrieval yields an unavailable notice, not an inferred location. All-
unavailable/empty/no-retrieval replies remain MODEL_GENERATED_UNVERIFIED. PARTIALLY_SUPPORTED remains
reserved; no mixed model prose is published. The existing trust field describes withheld model text;
supportStatus describes controlled presentation. No confidence/accuracy score or freshness guarantee.

Bounds: at most 100 order summaries or 50 tracking events per projection; each value at most 1,000
UTF-16 units; final serialized assistant envelope at most 8,192. Oversize data fails closed, not silently
truncated into an altered fact. Existing model/tool/loop/call budgets still apply. This is a minimal
status/location renderer, not a full order UI, independent fact checker or domain vocabulary policy.

## Persistence and history

Reuse messages.content text, no Flyway migration. New assistant content is a versioned JSON envelope:
schemaVersion=1, renderedText, supportStatus, modelContentWithheld=true, projected facts, and safe
retrievalEvidence references (toolSequence/name/outcome/completion time/error). Source timestamps are
on facts; internal IDs remain in the existing audit, not public facts. Tool audit rows now include the
sequence linked to assistantMessageId; response-grounding audit also records fact count and withholding.
User/assistant/envelope/audit writes remain atomic and mandatory audit failure rolls them back.

Generated conversational/prose content is represented as withheld, never persisted as raw model text.
There are no credentials, headers, full external payloads or model metadata/usage in the envelope.
The typed schema excludes credential-bearing objects; it cannot universally recognize secrets that
an undocumented future provider might place inside an allowed text field. Real provider field validation/
privacy policy remains a prerequisite before a production adapter is approved.

Stored content is contextual UNTRUSTED_HISTORY on later model requests, even if an old envelope says
EXTERNALLY_SUPPORTED. It is never decoded into current RetrievalEvidence/facts. Public history retains
the earlier conservative assistant/OTHER masking, including supported snapshots; historical factual
display is not implemented. User text remains contextual. Legacy unsupported rows are not rewritten.

## Bedrock discovery (not assumptions)

Actual safe checks: process environment and ignored .env inspected only for presence of known AWS/
Bedrock configuration keys (no values printed); standard /Users/gopalsarma/.aws/config and credentials
files are absent. Alternate config/credential paths, profile, region, web-identity/container credential
sources and Bedrock bearer-token variables are absent. AWS CLI configure list was captured internally,
with metadata discovery disabled; it indicates no region or access-key credential source. The first
presence-only parser's whitespace matching was corrected and region absence confirmed. No STS/Bedrock
network request was made. This is absence of discovered local configuration, not proof of account state.

Java toolchain 21, host 25.0.1, Gradle 9.7.1, Spring Boot 4.1.1 and CLI 2.37.4. Build/settings/application/
test configuration, environment example and deployment skeleton contain no adapter, AWS dependency,
approved region/model/profile or IAM reference. Existing readiness research remains in
RESPONSE-GROUNDING-AND-BEDROCK-READINESS.md.

Official [credential chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html)
and [Converse API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html)
were checked again. Converse remains a candidate behind AiModelProvider, not a selected/verified API
for a nonexistent approved model. Region/model availability, tool support, API shape, model access,
account authentication and exact IAM scope cannot be verified without that selection/configuration.
Existing 5s model/20s tool/30s loop budgets require bounded SDK timeouts/retries and safe error mapping;
none is implemented in an absent adapter. No IAM policy, model ID, region or credentials were invented.

BEDROCK CONFIGURATION BLOCKED — region, standard authenticated session/role, approved model/profile,
IAM/model access and tool-transcript mapping approval missing. The Amazon Bedrock skill required this
prerequisite pause; Process 34 and opt-in Bedrock smoke test NOT IMPLEMENTED. BEDROCK RUNTIME NOT
VERIFIED — no adapter/configuration or real invocation. No cloud setup or AWS credits used.

## Evaluation and verification

evaluation/README.md documents the new minimal deterministic JUnit runner and 12 explicit cases,
assertion-based pass/fail reasons and measured/not-available metadata. It supplements existing real-
database authorization tests; mocks alone do not prove resource ownership. Real model quality,
provider truth, freshness and production guarantees remain MANUAL REVIEW REQUIRED.
