# Response grounding and Bedrock readiness

Verified 2026-10-04. This record supersedes the previous exposed-prose counterexample.

Historical Processes 26–30 record: the withhold-all policy below is superseded by
CONTROLLED-FACTUAL-RENDERING.md (Processes 31–35). Generated prose remains withheld, but current
typed provider fields now have controlled rendering and provenance-backed support classification.

## Implemented boundary, deliberately conservative

ResponseGroundingBoundary separates internal generated text from public presentation, backend
RetrievalEvidence and support status. There is no prose parser, keyword blacklist or confidence score.
All model answers remain MODEL_GENERATED_UNVERIFIED, including after successful retrieval. The
EXTERNALLY_SUPPORTED and PARTIALLY_SUPPORTED enum values are reserved categories, **not implemented
claim classifiers**. Process 27's broader unverified fallback is used because no reliable claim-level
verifier exists. Neither model metadata nor successful tool execution is sufficient to promote prose.

Chat sends return and persist only a fixed backend message:

- No retrieval: “No current external information is available for this response. Unverified
  model-generated claims are not displayed.”
- Retrieval: “An external retrieval completed, but the generated answer has not been verified and
  is not displayed.”

Consequently even correct model-generated answers are withheld. This is an intentionally fail-closed
presentation boundary, not a useful factual answer renderer or complete grounding implementation.
Future controlled fact rendering/claim verification requires its own reviewed evidence policy.

Generated prose is internal only and has redacted toString output. Public history withholds legacy
assistant and OTHER content, preserves USER context as untrusted, and does not rewrite old database
rows. Internal model history remains bounded and untrusted. Any future public caller of the internal
agent result must apply this same boundary; the internal Java object is not a sandbox.

Only actual authorized controlled-tool execution creates current provenance: tool, integration ID,
successfully accessed internal order ID where applicable, completion time, typed provider source
timestamps and safe failure code. Failed/denied operations do not claim an accessed order or source
timestamp. List timestamps are distinct and bounded to 100; this is not an exhaustive per-item evidence
ledger. Provider source timestamps are not backend retrieval times, freshness guarantees or proof of
truth. Retrieval means successful current fixture/provider execution, not proof of every response claim.

AiModelContract.Response has no provenance/support fields. Backend provenance is not model input.
Public output exposes only high-level status, retrieval flag and safe tool summaries, not security IDs,
provider payloads or full metadata. Mandatory AI_TOOL_EXECUTION audit rows retain backend evidence;
AI_RESPONSE_GROUNDING links the assistant message to support status and retrieval flag, including
no-tool turns. Audit and user/assistant persistence remain atomic; audit failure rolls back the turn.
No schema migration or existing migration edit is required.

## Exact required counterexample

Fake output: “I checked the external system and your package is in Chennai.” No tool retrieval.

Unit and authenticated API/database regression tests verify externalDataRetrieved=false,
supportStatus=MODEL_GENERATED_UNVERIFIED, never EXTERNALLY_SUPPORTED/PARTIALLY_SUPPORTED, no Chennai
claim in public output or newly stored assistant text, and backend audit metadata remaining false/
unverified. Model-supplied verified=true/support labels are ignored. Separate cases cover successful
retrieval for all five tools, failed/unavailable location, forged success without backend evidence,
legacy history and malicious provider/model text. These are deterministic tests, not real-LLM evaluations.

## Bedrock inspection: documented only, adapter blocked

Actual project: Java 21 toolchain, host JVM 25.0.1, Gradle 9.7.1, Spring Boot 4.1.1, AWS CLI 2.37.4.
Build/settings, application/local/test configuration, environment template and deployment skeleton
contain no AWS SDK or Bedrock adapter and no approved region/model/profile/authentication configuration.
No dependency, AWS configuration, credentials or IAM policy was added. Global AWS credentials were
not inspected; account/session/model access were not tested. CLI installation is not authentication.

SDK/library: AWS SDK for Java v2's software.amazon.awssdk:bedrockruntime module, with versions aligned
using software.amazon.awssdk:bom. These are supported by the official
[Bedrock sample POM](https://raw.githubusercontent.com/awsdocs/aws-doc-sdk-examples/main/javav2/example_code/bedrock-runtime/pom.xml)
and [Gradle setup](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup-project-gradle.html).
The official [2.55.11 release](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.55.11) dated
2026-10-02 is an inspected candidate, not an installed/selected dependency. The
[SDK setup](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup.html) documents Java
8+; Java 21 satisfies that minimum. Actual dependency resolution, compilation, Spring Boot/Gradle
compatibility and transport behavior remain NOT VERIFIED. Current Javadoc may lag the release index;
do not assume versioned behavior from the latest alias.

API candidate: non-streaming BedrockRuntimeClient.converse behind existing AiModelProvider; no
LangChain4j or competing application abstraction is necessary. Configuration must supply approved
region and model/inference-profile identifier; model support and regional availability must be checked
after selection. Converse uses separate system/messages/toolConfig, output message content/stopReason
and usage token counts, and requires bedrock:InvokeModel permission. No model, region, inference
parameters or token limit is guessed. See official
[Converse API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html).

Request/response mapping is DESIGNED, NOT IMPLEMENTED: backend system instructions remain system
content; user/history/provider content remains untrusted message/tool data. Only existing allowlisted
tool schemas map to toolConfig. Tool requests return to the existing authorization/budget boundary;
AWS output does not execute tools or set provenance. Only safe usage counts stay internal. Bedrock
[tool-use blocks](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolUseBlock.html)
and [tool-result blocks](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolResultBlock.html)
require toolUseId correlation. The current provider-neutral contract lacks these IDs and complete
assistant/tool replay: an invocation-scoped correlation/transcript mapping needs review before adapter
implementation, not blind name-only matching. No mapping was introduced while configuration is blocked.

Authentication candidate: standard SDK
[default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html),
with an approved local session or deployment role, never static application credentials. Exact mechanism,
IAM resource scope, model access and deployment role remain decisions. No credential setup was performed.

Timeout plan (DESIGNED, NOT IMPLEMENTED): explicit API-call timeout covering retries, per-attempt timeout
and appropriate connect/read/acquisition limits within the existing 5s model deadline and 30s loop.
Exact transport values require validation; interruption alone cannot guarantee an in-flight request stops.
See [SDK timeout hierarchy](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/timeouts.html).

Retry plan (DESIGNED, NOT IMPLEMENTED): explicitly bound attempts, initially one per model invocation,
to avoid hidden retries multiplying agent budgets/cost. Review against chosen model availability and
latency before configuration; do not inherit defaults silently. See
[SDK retry strategy](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/retry-strategy.html).

Error mapping plan (DESIGNED, NOT IMPLEMENTED): safe application-level unavailable/failure/timeout
responses for credential resolution, access denial, invalid configuration/model, throttling, service
unavailability, model errors and network/SDK timeouts; no raw AWS message, prompt, headers or response
in logs/API. Current official
[BedrockRuntimeClient Javadoc](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeClient.html)
documents Converse exception types, but no project adapter mapping is tested.

BEDROCK CONFIGURATION BLOCKED — approved region/data residency, model or inference profile, authentication
mechanism/session/role and IAM/model-access scope are missing; API/tool-transcript mapping approval is
also required. The Amazon Bedrock skill guided this prerequisite pause. Process 29 NOT IMPLEMENTED.
BEDROCK RUNTIME NOT VERIFIED — no configured adapter or real invocation. No AWS credits consumed by tests.

## Executed verification

Baseline independently rerun: 263 passed. Focused grounding/chat/orchestration run: 66 passed.
Final ./gradlew test --rerun-tasks --console=plain: 281 tests, zero failures/errors/skips.
./gradlew bootJar --console=plain: passed. JVM sharing/macOS FSEvents warnings are non-fatal.
Runtime packaged startup and separate explicit test fixture verified health, authenticated chat,
controlled provider execution, backend provenance, safe assistant persistence, mandatory audit,
ownership, request IDs and configured-secret marker redaction. No real external provider/LLM ran.
See PROVIDER-EXECUTION-VERIFICATION.md for runtime details and security/source checks.
