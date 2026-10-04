# Provider execution and HTTP transport verification

## Repository evidence and scope

The repository contains the five-operation ExternalOrderProvider contract, typed domain requests/results,
tenant/user ownership guards, integration metadata CRUD, a scoped CredentialStore interface, closable
CredentialMaterial, JWT/session validation, Redis rate limits, centralized audits, request IDs and JSON logs.
V1–V7 are the existing schema history. No schema change is needed for this task.

There is no documented real provider, production adapter, credential configuration API, or CredentialStore
implementation. Documentation referring to the frontend as a landing page only is stale: login/register/dashboard
routes and a frontend authentication client exist in the working tree, but frontend/browser behavior was not
reverified during this backend task.

## HTTP boundary

SecureHttpTransport is for trusted backend adapters only. No HTTP endpoint or AI tool exposes it.
It constructs no provider endpoints and implements GET/HEAD only. Mutating requests are deferred until
documented provider semantics exist. Headers may contain credentials supplied by trusted adapter code;
they are never returned or logged. URL user-info, query strings and fragments are rejected entirely.
This conservative query policy requires explicit review if a documented provider needs non-secret filters.

OkHttp 4.12.0 is explicitly pinned. Its route selector converts validated DNS answers into resolved
InetSocketAddress instances, and its socket connection uses those instances. TLS retains the original
hostname. Each call uses a fresh connection pool, direct connections, HTTP/1.1, no cookies/cache,
no authentication follow-ups, no redirects, and no retry. DNS answers are checked at connection resolution,
all answers must be permitted public unicast, and a mixed public/private answer is rejected wholesale.
IP literals are checked independently because OkHttp bypasses custom DNS for numeric hosts.

Loopback, private, link-local, unspecified, multicast, CGNAT, metadata, documentation/benchmark ranges
and conservative IPv6 transition/special-use ranges are denied. IPv6 is restricted to permitted addresses
within 2000::/3. This is application policy; production network egress controls have not been configured.

Connect timeout: 3 seconds. Read/write timeouts: 5 seconds. Total HTTP-call timeout: 15 seconds.
DNS waits are bounded to 2 seconds using at most four daemon workers and no waiting queue. OS DNS calls
may ignore cancellation, so saturation fails closed rather than allocating unbounded threads.
Retries: zero, including a 503 with Retry-After: 0 (that response header is removed before OkHttp's
follow-up interceptor can act). Statuses such as 401/429/503 are returned to trusted adapter code without
body/header logging or invented provider error mapping.

Response content is capped at 1 MiB, checked against Content-Length and while streaming. Accept-Encoding
is fixed to identity; any compressed Content-Encoding is rejected before decompression. Therefore no
decompression path can exceed the cap. Response bytes remain untrusted and backend-only; their string
representation is redacted. TLS trust and hostname checks use client defaults in production.

Official client implementation inspected:
- https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/internal/connection/RouteSelector.kt
- https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/internal/connection/RealConnection.kt
- https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt

## Execution boundary

ExternalOrderExecutionService exposes the existing five domain operations as backend service methods.
Callers supply integration selection and domain identifiers, never tenant/user/session IDs, URLs or
credentials. It derives context from AuthenticatedUserContextProvider, reuses
TenantDataAuthorizationService, scopes integration lookup by tenant/user, rejects disabled integrations,
requires credential ownership, selects exactly one registered BackendOrderProviderAdapter, and retrieves
scoped credentials only after these checks. Missing and unauthorized integrations use the same denial.

Credentials are closed after either success or failure. Provider exceptions are mapped to fixed categories
without retaining potentially sensitive messages/causes. Results must be non-null. Future real adapters
must validate response schemas and map to the existing domain types; no undocumented mapping is provided.
Provider text is data; no prompt, AI framework registration or model path exists here. A later backend-only
tool interface binds integration/identity and explicitly labels output as untrusted; see the batch below.

No adapter or store bean is registered by this task. Synthetic store/provider implementations exist only
in tests. The production boundary fails closed for missing configuration/adapter/store. There is no
order/tracking API endpoint or real external provider call.

## Credential persistence blocker

CREDENTIAL STORE IMPLEMENTATION BLOCKED — the repository defines no approved encrypted persistence
format, storage location, key provisioning/rotation/recovery policy, or reference lifecycle. Reusing the JWT
signing key would conflict with its purpose. PostgreSQL is documented to hold references only.
No credential API or persistence implementation has been invented. A domain lifecycle contract and future
orchestration design are documented separately; AWS Secrets Manager remains behind CredentialStore.

CredentialMaterial closes and zeroizes its owned char array, but copies are the trusted adapter's
responsibility. HTTP header strings and JVM/client internal buffers cannot be reliably zeroized; avoid
retaining them beyond a call. This is not a claim that all heap copies can be erased.

## Verification

Run with the existing local database environment from backend:

```bash
set -a
source ../.env
set +a
export SPRING_PROFILES_ACTIVE=local
export DATABASE_URL="jdbc:postgresql://127.0.0.1:5432/$POSTGRES_DB"
export DATABASE_USERNAME="$POSTGRES_USER"
export DATABASE_PASSWORD="$POSTGRES_PASSWORD"
./gradlew test --console=plain
./gradlew bootJar --console=plain
```

Transport tests use local MockWebServer fixtures and a package-private test socket factory that first
asserts the selected public IP, then routes to the fixture. Production uses the default socket factory.
The TLS fixture retains hostname verification and proves correct-host acceptance/wrong-host rejection.
Execution tests authenticate through registration/login and the real JWT decoder/session converter,
then use synthetic test-only adapters/stores. They verify tenant/user isolation, disabled/unconfigured
integrations, credential cleanup, safe provider errors, and missing backend components.

Executed on 2026-10-03: the complete suite passed 151 tests, zero failures/errors/skips. The original
119 tests remain passing; 32 security tests were added. The application packaged successfully.
A disposable JAR runtime on port 18085 returned liveness/readiness 200 UP, registration 201,
login 200, integration creation 201, own integration read 200, cross-tenant read 403,
unknown-login statuses 401/401/429, refresh 200, logout 204, and revoked-session access 401.
Startup validated seven Flyway migrations; PostgreSQL returned SELECT 1 and Redis PONG.
The generated password, tokens and signing key had zero runtime-log matches. Expected login,
refresh, logout, integration and rate-limit audit rows were queried directly. Temporary users,
integrations, sessions, audit records and rate-limit keys were removed; port 18085 was closed.
Production DNS/network/provider interoperability, deployment egress, and encrypted credential persistence
are not verified. No existing secret-scanning pipeline is configured; a focused source scan is supplemental.

## Processes 6–10 verification — 2026-10-03

See CREDENTIAL-LIFECYCLE-AND-TOOL-BOUNDARY.md for the actual credential trace, rotation contract,
ownership/lifetime guarantees within this boundary, unimplemented production lifecycle/audits, tool
argument/identity boundary, and important Java cancellation/heap-copy/future-AI limitations.

Exact backend commands (with the local environment loaded as above):

```sh
./gradlew test --rerun-tasks --console=plain
./gradlew test --tests '*CredentialLifecycleContractTests' --tests '*ProviderExecutionBudgetTests' --tests '*ExternalOrderExecutionIntegrationTests' --tests '*SecureHttpTransportTests' --console=plain
./gradlew bootJar --console=plain
```

Baseline full rerun: 151 passed, 0 failed/errors/skipped. Focused suite: 61 passed, 0 failed/errors/skipped
before final deadline/log/serialization assertions. Final full rerun: 185 passed, 0 failed/errors/skipped;
packaging passed. New tests: 34 (20 execution/tool, 6 lifecycle, 7 budget, 1 active HTTP cancellation).
All 7 registration, 12 authentication, 16 authorization, 12 isolation, 13 URL-policy, 18 transport,
13 rate-limit, and existing audit/observability tests passed. Deterministic provider coverage includes all
five operations, safe provider failures/null/mismatched order/shipment responses, untrusted text, missing
components, disabled/foreign integration, retrieval failure and transport rejection.

The real service deadline test waited approximately 20 seconds and verified credential disposal. Unit
budget tests exercised interruption-ignoring work, late retrieval cleanup, five-attempt enforcement,
remaining HTTP timeout, trusted worker context cleanup and four-worker saturation. These tests release
all blocking fixtures; Java force-stop/process isolation is not implemented.

Packaged runtime: java -jar backend/build/libs/backend-0.0.1-SNAPSHOT.jar with local profile and port
18086 returned GET /actuator/health/liveness and /actuator/health/readiness: HTTP 200, {"status":"UP"}.
Startup validated 7 Flyway migrations. Docker Compose PostgreSQL/Redis were healthy; database history
listed versions 1–7 success=true, Redis returned PONG. Configured database-password/signing-key markers
had zero runtime-log matches. Health-only runtime created no application data, then was stopped; its
temporary log was removed and port 18086 verified closed. New database fixtures roll back via existing
transactional tests, and existing rate-limit tests delete their namespaced Redis keys.

Gitleaks 8.30.1: gitleaks dir <temporary source-only snapshot> --redact --no-banner --no-color
reported no leaks. Snapshot included all Git-tracked and non-ignored untracked source files, excluding
.env, caches and generated output, and was removed after scanning. This is an executed local scan, not
a CI/deployment guarantee. git diff --check passed; .env/build/cache ignore rules were checked. No new
dependency, configuration or migration changes were made in this batch. Prior uncommitted dependency,
configuration, frontend and V6/V7 files were preserved; no commit/push was performed.

Warnings: non-fatal Gradle/macOS FSEvents and JVM sharing warnings persisted. An initial new-test
compilation failed on an ambiguous AssertJ overload and was corrected before successful execution.

## Processes 11–15 verification — 2026-10-03

ORDER-TRACKING-DOMAIN.md records the actual schema/field inventory, tenant grouping rather than a
tenants table, composite relationships, read-service boundaries and explicit data-authority decisions.
No new migration, dependency, runtime configuration, domain HTTP controller, real adapter or AWS store.

Executed from backend with the existing local environment loaded:

```sh
./gradlew test --rerun-tasks --console=plain
./gradlew test --tests '*ExternalOrderExecutionIntegrationTests' --console=plain
./gradlew bootJar --console=plain
```

Baseline full rerun: 185 passed, 0 failed/errors/skipped. Focused suite before the last pagination/projection
test: 48 passed, 0 failed/errors/skipped. Final full suite: 202 passed, 0 failed/errors/skipped; bootJar
passed. The final execution/domain suite contains 49 tests; 17 new domain tests were added. Existing
registration (7), authentication (12), authorization (16), tenant isolation (12), provider contract (7),
HTTP transport (18), URL policy (13), rate limits (13), lifecycle/budget and audit/observability tests pass.

Tests exercised real Spring registration/login/JWT/session resolution, scoped domain reads/resolution,
deterministic test provider calls, shipment/event ownership and actual foreign-key rejection. Poisoned
persisted status, event/location, synthetic unexpired cache and conversation text never became current
provider data. Successful provider data wins; unavailable data stays unavailable; provider failure stays
an error. Repeated calls retrieve again rather than read a cache. Tests run in transactions/savepoints
and roll back all domain fixtures.

Packaged JAR on temporary port 18086: liveness/readiness HTTP 200 with {"status":"UP"}; startup validated
7 Flyway migrations. PostgreSQL/Redis containers were healthy, migration history versions 1–7 all
success=true, Redis PONG. Runtime configured-secret marker matches: 0. The health-only process was
stopped, temporary log removed and port verified closed; it created no application rows. SQL counts
for fixture orders/shipments/events were all zero; remaining default test-rate-limit keys were zero.
No packaged authenticated order/tracking HTTP request is claimed: no production domain controller exists.
Authenticated domain execution was exercised in the Spring integration suite, not via an invented API.

Final source-only Gitleaks 8.30.1 scan command: gitleaks dir <temporary source-only snapshot> --redact
--no-banner --no-color. Snapshot uses git ls-files -co --exclude-standard, excluding ignored .env/caches/
build artifacts while including untracked source. No leaks found; snapshot removed. git diff --check
passed; no existing migration was edited. Existing unrelated dirty files were preserved. Branch main;
HEAD remains 388088b5873df5874e31bc925a5ab62ffd32c230; no commit/push.

CACHING DEFERRED — freshness policy requires explicit product decision. Source-age/clock-skew policy,
ingestion/multi-shipment/API/AI wiring and provider contracts remain unapproved or absent. Real providers,
production credential persistence and deployment interoperability remain unverified/blocked.

## Processes 16–20 verification — 2026-10-03

See AI-BOUNDARY-AND-DOMAIN-API.md for contracts, supported routes, authority boundaries and limitations.
The 202-test baseline was rerun successfully before implementation. New work adds 34 tests: 11 actual
Spring integration/API/conversation/agent cases, 21 scripted orchestration cases and 2 worker guard cases.

Executed in backend with the existing local environment loaded without printing secrets:

```sh
./gradlew test --rerun-tasks --console=plain
./gradlew test --tests '*AgentOrchestrationTests' --tests '*ExternalOrderExecutionIntegrationTests' --console=plain
./gradlew test --rerun-tasks --console=plain
./gradlew bootJar --console=plain
```

Final full suite: 236 passed, 0 failed, 0 errors, 0 skipped. Packaging passed. Earlier focused run passed
79 tests before the final four guard/deadline cases were added. An initial Mockito fixture failure during
re-stubbing was fixed using doThrow; final focused/full results are successful. Registration (7),
authentication (12), authorization (16), isolation (12), transport (18), URL policy (13), Redis rate-limit,
audit/redaction, credential-contract and provider-budget regressions passed. Tests validated actual
five-second default model timeout, shortened overall/tool deadlines, repeated-call limit, saturation,
worker-context cleanup, all five controlled tools, safe tool errors and malicious arguments. Real
registration/login/security context plus real owned-domain/provider-tool execution were used in Spring
agent integration cases; fake model/provider/store remain test-only. Committed asynchronous test
fixtures are scoped to unique generated users and explicitly cleaned in finally blocks.

Packaged JAR verification (java -jar backend/build/libs/backend-0.0.1-SNAPSHOT.jar, local profile,
temporary port 18086) actually exercised:

- GET liveness/readiness: 200 UP.
- POST registration/login for two generated disposable users: 201/200.
- POST integration configuration: 201; neutral fixture, no credentials or external API call.
- GET /api/v1/orders and /api/v1/orders/{id}: 200 for owner.
- GET /api/v1/shipments/{id}: 200 for owner.
- GET shipment tracking-history/current-status/current-location: 200 explicitly UNAVAILABLE because
  the fixture has no external shipment ID; no invented real-provider success.
- Missing authentication: 401; other-user order/shipment: 403; missing order: 403; tenant override
  query: 400. API responses carried X-Request-ID.
- Startup validated 7 Flyway migrations. Generated passwords/access/refresh tokens and configured
  database password/signing-key had zero marker matches in runtime log.
- Both generated users and dependent rows were deleted with unique-user scope; only the temporary
  rate-limit namespace was cleared. Process stopped, log removed, port 18086 confirmed closed.

No packaged AI HTTP invocation is claimed: no AI endpoint or production model exists. Deterministic
AI/tool calls were exercised in unit and authenticated Spring integration tests, not through a fake
production API. No real-provider interoperability, production readiness, Bedrock, prompt-injection
protection, credential persistence or final-answer grounding guarantee is claimed.

Local source-only Gitleaks 8.30.1 scan uses the existing temporary snapshot script, including tracked
and non-ignored untracked sources while excluding .env/build/cache output. No leaks found; this is
not CI scanning. git diff --check passed. No dependency/configuration/migration/frontend/extension
files were changed in this batch; earlier dirty changes remain. Main HEAD remains
388088b5873df5874e31bc925a5ab62ffd32c230. No commit/push. Non-fatal JVM sharing/macOS FSEvents
warnings persist. Approved credential policies, real provider/model contracts, ID ingestion mapping,
source-age/multi-shipment/future grounding policies remain decisions or blockers, not implemented guesses.

## Processes 21–25 verification — 2026-10-04

CHAT-AND-AI-SECURITY.md records API, transaction/audit semantics, trust boundaries, bounds and the
Bedrock blocker. Existing schema, authentication/ownership guards and AiModelProvider are reused.
No AWS SDK/dependency/configuration/migration/frontend/extension change was made.

Executed from backend with ignored local environment loaded without printing secrets:

```sh
./gradlew test --rerun-tasks --console=plain
./gradlew test --tests '*ConversationApiIntegrationTests' --tests '*AgentOrchestrationTests' --console=plain
./gradlew test --tests '*ConversationApiIntegrationTests' --console=plain
./gradlew test --rerun-tasks --console=plain
./gradlew bootJar --console=plain
```

Baseline full rerun: 236 passed, zero failures/errors/skips. First focused implementation run: 33
passed. Expanded focused run had 47 cases, one fixture-setup failure: mocking the transactional
AuditEventService proxy triggered its mandatory-transaction interceptor during stubbing. An already
running full suite likewise reported that failure (262 cases). Stubbing the underlying AOP target
fixed it; the focused chat suite passed 17 cases. After the additional forged-header case, final full
rerun passed 263 cases, zero failures/errors/skips. bootJar passed. JVM sharing/macOS FSEvents
warnings are non-fatal and unchanged. New tests: 27 (18 chat integration and 9 additional orchestration).

Tests cover actual JWT-authenticated chat create/send/history and persistence; owner versus foreign/
missing/same-tenant other-user conversation; foreign integration and model-selected order; denied
identity/URL/credential/tool fields and forged identity headers; atomic message/audit rollback; concurrent
serialized history; long assistant-history compatibility; timeout/loop limits/provider failure/unavailable
tracking; hostile current/history/provider/order-status/tracking-description/tool text; unchanged system
instructions; unknown tools/URL/provider/identity overrides; response/tool-result bounds and blank answers.
Existing registration 7, authentication 12, authorization 16, isolation 12, Redis rate-limit 13, transport
18, URL-policy 13, audit/redaction/provider/lifecycle/budget tests pass. No real LLM was invoked.

One deliberate fake outputs an unsupported external-lookup claim without a tool. Backend metadata
correctly stays externalDataRetrieved=false, MODEL_GENERATED_UNVERIFIED, with model/usage metadata
omitted. Its prose still contains the claim: semantic grounding and complete prompt-injection protection
are NOT verified. No accuracy/confidence score or invented external-verification label was added.

Runtime was actually executed twice on temporary port 18087 using the packaged application:

1. Normal java -jar startup: liveness/readiness 200 UP; two generated registrations/logins 201/200;
   integration fixture 201; POST /api/v1/conversations 201. POST /{id}/messages returned safe 503 with
   no model configured; database message count remained zero. Production bootJar contains no fake.
2. Explicit PropertiesLauncher with temporary separate test-classpath fixture JAR/profile: liveness/
   readiness 200; POST /{id}/messages 200 exercised deterministic getCustomerOrders through actual
   backend authorization/provider-tool boundary. GET /{id}/messages 200 returned two untrusted history
   messages. Database verified two message rows and one safe AI_TOOL_EXECUTION audit row. Backend
   retrieval flag true while output trust remains MODEL_GENERATED_UNVERIFIED; no raw tool payload.
   Anonymous history 401; foreign-user history/send 403; tenant body override 400; request ID verified.

Both startup logs validated seven Flyway migrations and contained zero matches for generated passwords,
access/refresh tokens and configured database-password/signing-key markers. Both processes stopped;
port 18087 closed; unique runtime users/dependent data and only temporary Redis namespace deleted;
temporary fixture JAR/logs removed. No real HTTP provider or AWS/Bedrock call occurred. Final SQL count
for generated chat-test/runtime users was zero. Redis PONG; migration history V1–V7 success=true.

Repository-source fingerprints before/after verified that build/settings/configuration, V1–V7,
frontend/extension and unrelated dirty files were unchanged. git diff --check passed. Final source-only
Gitleaks scan includes tracked/non-ignored untracked sources, excludes .env/build/cache, and found no
leaks; snapshot removed. This is a local scan, not a CI guarantee. Branch main, HEAD
388088b5873df5874e31bc925a5ab62ffd32c230 unchanged; no commit/push.

AWS CLI 2.37.4 exists, but approved model/inference profile, region/data residency, authentication/IAM/
model access and inference API/configuration are absent from the project. No SDK version/compatibility,
SDK timeout/error implementation or AWS session/model access was verified. BEDROCK IMPLEMENTATION
BLOCKED; BEDROCK RUNTIME NOT VERIFIED. Missing production credential policies/provider contracts and
ID ingestion/multi-shipment/freshness/grounding policies remain decisions, not guessed implementations.

## Processes 26–30 verification — 2026-10-04 (supersedes prior prose-exposure result)

Independently reran the 263-test baseline. Focused command:
`./gradlew test --tests '*ResponseGroundingBoundaryTests' --tests '*ConversationApiIntegrationTests' --tests '*AgentOrchestrationTests' --console=plain`
passed 66 cases. Final `./gradlew test --rerun-tasks --console=plain` passed 281 tests, zero failures,
errors or skips. `./gradlew bootJar --console=plain` passed. Non-fatal JVM sharing/FSEvents warnings remain.
Final suites include 30 orchestration, 26 chat API and 10 grounding cases. Registration 7, authentication
12, authorization 16, isolation 12, rate-limit 13 and secure-transport 18 regressions all passed.

The exact fake “I checked the external system and your package is in Chennai.” with no retrieval
remains externalDataRetrieved=false and MODEL_GENERATED_UNVERIFIED at unit and authenticated API/SQL
layers. It is never EXTERNALLY_SUPPORTED/PARTIALLY_SUPPORTED and no longer reaches the response or
new assistant row. Model verification metadata cannot promote it. All five successful typed tool paths
retain backend source timestamp/resource provenance without promoting answer support. Failed/unavailable
location and malicious provider/model content remain unverified and withheld. Legacy assistant/OTHER
public history is masked without modifying old rows. There is no implemented claim-level verifier.

Packaged runtime was executed again on port 18087: normal liveness/readiness 200 UP, generated
register/login 201/200, integration/conversation creation 201, send 503 without production model,
zero partial message rows. A temporary explicit test-classpath fixture then exercised send 200 through
authorization/domain/provider/tool/model/provenance. Retrieval=true, supportStatus=MODEL_GENERATED_UNVERIFIED,
fixed backend presentation in response and assistant SQL row, two message rows, one AI_TOOL_EXECUTION
and one AI_RESPONSE_GROUNDING audit row. History 200, anonymous 401, foreign-user history/send 403,
tenant body override 400 and request IDs were checked. No arbitrary model prose/provider payload exposed.

Both runtime logs validated seven migrations and matched zero generated password/token/configured-secret
markers. Processes were stopped, port closed, only disposable fixture users/dependent rows and temporary
Redis namespace cleaned; fixture JAR/logs removed. Final generated chat-test/runtime-user count=0;
Redis PONG; V1–V7 success=true. Normal bootJar contains no runtime test launcher or deterministic provider.
No real external HTTP provider, Bedrock model or AWS API was invoked.

Source fingerprints relative to this batch's baseline show only grounding/AI contract/orchestration/
conversation/audit-event/test/documentation changes. No build/dependency/configuration, existing V1–V7,
frontend/extension or unrelated preexisting dirty file changed. git diff --check passed. Gitleaks source-only
scan included tracked and non-ignored untracked files and found no leaks. Ignored .env/build/cache were
excluded; scanner snapshot removed. These checks do not establish production readiness.

Official AWS SDK/API readiness details and exact blockers are in
RESPONSE-GROUNDING-AND-BEDROCK-READINESS.md. Process 29 adapter is BLOCKED, NOT IMPLEMENTED; actual SDK
resolution/compatibility/authentication/model availability/runtime are NOT VERIFIED. Stronger grounding
classifiers and factual answer rendering are also not implemented. Branch main and HEAD
388088b5873df5874e31bc925a5ab62ffd32c230 unchanged. Working tree remains dirty; no commit/push.

## Processes 31–35 verification — controlled factual rendering

Baseline `./gradlew test --rerun-tasks --console=plain`: 281 passed. Focused rendering/grounding/chat
run: 46 passed. Separate evaluation command
`./gradlew test --tests '*AiEvaluationBaselineTests' --rerun-tasks --console=plain`: 12 passed.
Final `./gradlew test --rerun-tasks --console=plain`: 304 passed, zero failures/errors/skips;
`./gradlew bootJar --console=plain`: passed. The preceding full post-change run also passed 304.
Warnings remain non-fatal JVM sharing/FSEvents. Latest suites: chat API 28, controlled rendering 9,
evaluation 12, existing grounding 10, orchestration 30; registration 7/authentication 12/authorization
16/isolation 12 plus all SSRF/redaction/audit/request-ID/rate-limit/budget regressions passed.

Rendering now publishes only backend-projected typed fields and exact source timestamps,
with EXTERNAL_UNTRUSTED text labels. EXTERNALLY_SUPPORTED describes these evidenced fields only;
model prose stays withheld. No-tool Chennai counterexample remains false/unverified. An authenticated
fixture-provider location test returns Bangalore rather than model Chennai and never publishes
tomorrow/ETA; both response and stored envelope are checked. All five tool field projections, missing
status/time, failed/unavailable location, malicious provider/model text, forged/missing provenance,
oversize projection, stored-snapshot-to-next-turn non-authority and legacy-history regressions pass.
No prose parser, independent provider fact checker, freshness policy or PARTIALLY_SUPPORTED classifier.

Packaged runtime executed on temporary port 18087: startup/health 200 UP; generated register/login
201/200; integration/conversation creation 201; no-model send 503 with zero partial rows. Explicit
temporary test-classpath model/provider/store fixture then exercised send 200: getCustomerOrders
traversed authorization/domain/provider/orchestration; rendered fixture-status came only from typed
provider data, facts carried EXTERNAL_UNTRUSTED, supportStatus=EXTERNALLY_SUPPORTED and retrieval=true.
Fake model Chennai/tomorrow claims were absent from response and assistant content. Versioned JSON
envelope renderedText matched API text, modelContentWithheld=true, fields and successful retrieval
references present. Database: two messages, one tool audit and one response-grounding audit with correct
status. History 200; anonymous 401; foreign-user history/send 403; tenant override 400; request ID correct.

Both runtime logs validated seven migrations and matched zero generated credential/token/configured-
secret markers. Processes stopped, port 18087 closed, unique fixture data and Redis namespace cleaned,
temporary logs/JAR removed. No test launcher/fake provider/evaluation class in production bootJar.
No real external API/AWS/Bedrock calls. Versioned messages reuse existing content text; no migration.

Evaluation XML has 12 successful EVALUATION_RESULT records with exact case expectations/reasons,
positive measured case latency and usage/cost=null. No fabricated token/cost values or subjective
accuracy score. Actual security ownership is separately verified by integration tests, not just mocks.
Manual-review cases and future real-model limitations are documented in evaluation/README.md.

Actual safe AWS environment/.env/standard-file/CLI discovery found no region/authentication source/
approved model/profile. Account state, IAM/model access and model-specific tool support are NOT VERIFIED.
Process 34/opt-in real-model smoke remain blocked, not faked. See CONTROLLED-FACTUAL-RENDERING.md.

Batch-baseline source fingerprints show only AI rendering/contract/orchestration/conversation,
test-only fixtures, tests, evaluation documentation and project documentation changes. Existing migrations,
dependencies/configuration, frontend/extension and unrelated dirty files unchanged. git diff --check
passed; source Gitleaks scan found no leaks, ignored .env/build excluded and temporary snapshot removed.
Branch main/HEAD 388088b5873df5874e31bc925a5ab62ffd32c230 unchanged; no commit/push. Not production ready.
