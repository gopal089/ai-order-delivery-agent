# Processes 56–60: local instrumentation, not production observability

Baseline `main` HEAD/local origin/main: `0d1e47bab38587c39b34436fd9ea2ae7862a3164`.
The initial baseline check stopped for untracked `codex.md`; the owner explicitly
authorized continuing with it preserved, untouched and outside this task. No commit/push.

## 56 — ECS production deployment: BLOCKED

Fresh read-only pre-flight on 2026-10-04: AWS CLI 2.37.4; metadata-disabled STS
get-caller-identity exit 253, credentials unavailable; configure-list exit 0,
profile/region unset. No account/principal/approved region, IAM, existing resources,
billing, credits or budget verified. No authentication, discovery without credentials,
AWS resource writes, image publishing, or DEV/QA/PROD deployment performed.

Inspected current infrastructure definitions, route inventory, Dockerfiles, CI,
health endpoints, migration/rollback and approval documentation. DEV/QA remain
review-only, deployable=false, approved values null. No deployable production IaC,
verified ECS/RDS/cache/network/Secrets Manager/KMS/IAM/TLS/ACM/WAF resources,
production approval mechanism, cloud monitoring, QA deployment acceptance,
backup/restore/rollback drill, budget or approved release digest is available.
The route inventory is not an API Gateway deployment template. Do not introduce
an IaC framework or create resources merely to fill these gaps.

Production gate remains missing: authenticated intended account, approved region,
verified IAM, approved deployable isolated infrastructure, managed secrets,
TLS/ingress, isolated DB/Redis, monitoring/security monitoring, tested rollback,
safe migration strategy, cost approval, explicit production approval, QA acceptance
and verified immutable release artifacts. No desired/running task counts, task
health, service/deployment status or cloud API results exist as evidence here.

## 57 — OpenTelemetry: locally implemented and tested; export disabled

Previously no OpenTelemetry/Datadog instrumentation or exporter existed. The existing
Spring Boot 4.1.1 dependency BOM manages OpenTelemetry 1.62.0. Add only the OTel API
and Spring's AspectJ starter in production; SDK/testing exporter is test-only.
No Java agent, auto-instrumentation starter, SDK environment autoconfiguration,
OTLP dependency, collector, endpoint, credentials or global SDK registration.

`TelemetryConfiguration` supplies OpenTelemetry.noop by default. Tests explicitly
supply a primary OpenTelemetry SDK with an in-memory exporter; they inspect real
finished spans, not fabricated traces or logged examples. A future approved SDK
bean must explicitly replace/take precedence over that no-op and be privacy-tested
before enabling export. An OTEL_* environment variable alone cannot enable export.
Provider-neutral instrumentation follows the [OTel Java API](https://opentelemetry.io/docs/languages/java/api/).

Bounded spans:

| Boundary | Coverage and limitation |
| --- | --- |
| HTTP | Synchronous servlet request, before security; fixed server span, method allowlist, exact server-route allowlist, status. Health probes omitted. Unknown/rejected route UNMATCHED; no URI fallback. Root context prevents caller parent/baggage/sampling injection. No asynchronous-dispatch tracing claim. |
| Provider | Existing ExternalOrderExecutionService entry points, not per outbound HTTP/socket/DNS spans; final SecureHttpTransport unchanged. No URL/vendor/credential/request/response attributes. |
| Database | ConversationContextService history and audit-write service boundaries, not every JDBC/JPA statement or result. Never capture SQL/parameters/history rows. |
| Redis | Existing AuthenticationRateLimiter check/reset boundaries, not every individual Redis command. No keys, Lua, email/IP/session or arguments. |
| AI | Existing orchestration span and caller-side model/tool wait spans include wait/queue duration. No prompts, results, metadata, usage, order/integration IDs or tool arguments. |
| Authorization/audit | Existing service boundaries; denied/error status. Audit span indicates attempted write, not committed transaction durability. |

Narrow Spring aspects never inspect arguments/results or exception messages, and
rethrow original failures. They do not change transaction semantics, authorize a
request, retry work, or manufacture provider retrieval. Existing model/tool pool
security context rules are unchanged. OTel context is NOT propagated into those
worker pools; provider work on a worker may be a separate trace. Caller-side wait
spans are not a claim of full distributed end-to-end correlation.

Latency comes from SDK span start/end timestamps, not a production metric/benchmark.
Failures set ERROR without description/exception event/stack/message. No metrics
exporter, duration histogram, dashboard or production trace was implemented.
Sampling, version/environment resource attributes, privacy/retention, verified
destination/TLS, queue limits and exporter failure behavior require approval/testing.
Do not blindly enable general auto-instrumentation: it can capture unsafe SQL,
URL, header, exception, prompt or provider fields outside this allowlist.

## Telemetry data classification

Exported span attributes are only `http.request.method`, `http.route`,
`http.response.status_code`; span names derive from a closed operation enum.
Unknown routes/methods collapse to UNMATCHED/OTHER. No arbitrary attribute-map API.
SDK trace/span IDs and timing/status are generated local instrumentation metadata.
Resource configuration is not taken from customer requests.

NEVER export passwords, access/refresh tokens, Authorization/cookies, API keys,
AWS/Datadog/customer credentials, secret references, URL/path/query values,
SQL statements/arguments, provider payloads, tracking/customer identifiers,
prompts/history/model responses, exception messages/stacks or arbitrary metadata.
Do not promote raw request IDs, user/tenant/session IDs to metric/span labels.
Existing X-Request-ID response/log propagation remains unchanged and tested.
Client-supplied request IDs are not trace attributes; format validation is not
proof that a client supplied no sensitive data.

Existing authenticated application logs/audit storage include server-side ownership
and request IDs. Treat those as RESTRICTED operational data, not public telemetry.
New aggregatable security signals temporarily clear/restore MDC, contain only
closed eventType/signalKind fields, and do not inherit customer identity/secrets.
Before shipping ANY existing stdout stream, approve strict readership, retention,
data classification and downstream filtering; span safety does not sanitize every
framework log. Existing key-based redaction is not a blanket content/DLP guarantee.

## 58 — Datadog: live integration NOT VERIFIED/BLOCKED

No Datadog configuration/account/org/site/key/agent/dashboard/monitor was found.
`infrastructure/observability/requirements.json` preserves these unknowns as null;
it is review-only, not a provisioner or connection configuration. No key requested,
printed, committed, or injected. No DD_API_KEY/OTLP header belongs in a span.

Future direction: consume the same provider-neutral OTel through an approved,
restricted Datadog Agent or Collector, not vendor logic in business services.
Datadog documents [Agent OTLP ingestion](https://docs.datadoghq.com/opentelemetry/setup/otlp_ingest_in_the_agent/).
Choose destination/site/account, signal compatibility, secure receiver/TLS,
infrastructure-only managed credential delivery, sampling, retention/cost and
safe synthetic ingestion/delivery tests before enabling it. DD_API_KEY is secret;
DD_SERVICE/DD_ENV/DD_VERSION/DD_SITE are configuration, not authorization evidence.
No direct intake URL, arbitrary endpoint, real integration or exporter is introduced.

## 59 — CloudWatch: live logging BLOCKED

Application already emits structured Logstash JSON, request IDs, safe route
templates/status/duration, auth/integration security logs, database audit records,
startup/shutdown and health responses. Health requests deliberately do not emit
request-completion logs; lack of those logs is not a failed probe.
No actual ECS awslogs configuration, approved log group/region/execution role/KMS,
retention, log ingestion, alarms or dashboards verified. RetentionDays remains null:
finite retention is required, but no arbitrary period is claimed approved/configured.

Future awslogs requires approved per-environment destinations and execution-role
log stream/write permissions; approved encrypted retention and restricted readers;
no auto-created unspecified group, no invented ARN or copied account/region.
The application needs no AWS access keys just to write stdout. Select log-driver
buffering/backpressure behavior and test failure handling before rollout. For
traces, an approved OTel/ADOT forwarding path is a separate missing decision.
This skill review uses classic CloudWatch log-group requirements, not an assertion
that a CloudWatch Omni Space or ADOT/Application Signals deployment exists.

## 60 — security monitoring: partial local signals; alerting DECISION REQUIRED

`security_signal_observed` / signalKind OBSERVATION is distinct from ordinary
`http_request_completed`. It is not a committed audit record, aggregate detector,
alert or incident. Existing audit_events retains bounded event types/outcomes and
tenant ownership; auth/integration logs and DB audits are reused, not replaced.

| Requested area | Actual evidence / gap |
| --- | --- |
| Login failure, refresh abuse/reuse/session | Existing auth failure/refresh/reuse/logout/rate-limit audit types and security logs. No suspicious-session aggregate detector or notification. |
| Authorization/cross-tenant | New denied-boundary observation and HTTP 403 signal; existing ownership regression tests. Denial alone does not prove cross-tenant attack; no repeated-attempt detector. |
| Integration | Typed provider failure/authentication-failure and URL-validation rejection observations. No raw endpoint, credential or payload. Not full transport/DNS SSRF event coverage; no repeated-failure detector. |
| AI | Typed unknown-tool, tool-limit, model/execution-timeout and generic boundary failure observations; failed model/tool wait spans. Unknown tool is an indicator, not semantic prompt-injection detection. Caught tool failures can appear as span errors/provider signals rather than propagate to an orchestration failure. No malicious-payload/prompt-text logging or semantic scanner. |
| Application | HTTP 401/403/429/5xx signals; audit-write failure signal. No spike/unusual-pattern thresholds, aggregation, delivery or incident assertions. |
| Infrastructure | ECS task/deployment health, network anomalies, IAM/CloudTrail, WAF and secret/KMS anomalies NOT VERIFIED; require actual infrastructure/event sources and permissioned ingestion. |

Alert threshold/window/destination/owner are null. Owner must approve these with
baseline traffic/expected failure rates, severity, no-data behavior, runbooks,
deduplication/rate controls and retention/cost. Test both synthetic triggering and
actual notification receipt before claiming alerting works. No production alert,
dashboard, incident, telemetry count, metric value or cost fabricated.

## Local verification

- `./gradlew test --rerun-tasks --console=plain` from backend: 319 tests/32 suites,
  zero failures/errors/skips; 13 new tests (11 SafeTelemetryTests, 2 integration).
- `./gradlew bootJar --console=plain`: PASS. Packaged OTel API present; no in-memory
  test exporter, OTLP exporter or telemetry test classes packaged.
- New tests inspect actual in-memory spans: auth/refresh/password/API-key inputs,
  provider payload, prompt/model result, exception and raw route protection;
  fixed route/method attributes, inbound parent isolation, scope cleanup, nesting,
  default no-op, security-signal MDC isolation, unmodified result/failure behavior,
  real Redis/audit aspect invocation and existing X-Request-ID response propagation.
- `node --test infrastructure/tests/*.test.mjs`: 13 PASS (10 existing + 3 new).
  `node infrastructure/validate-environments.mjs`: PASS definitions/deployment BLOCKED.
- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s .github/tests -v`: 9 PASS.
- Existing registration 7/7, authentication 12/12, authorization 16/16, tenant isolation
  12/12, audit 7/7. Existing deterministic evaluation tests remain part of the full suite.
  Evaluation files unchanged; no fresh standalone report-wrapper run claimed.
- Packaged prod-profile JAR launched locally with a random port and existing private
  local database configuration: liveness/readiness 200, protected request 401,
  X-Request-ID propagation, seven Flyway validations and graceful shutdown verified.
  Configured local secret markers absent from its logs. This is NOT AWS production.
- Initial run failed only the new test SDK bean selection; corrected test @Primary
  selection and final full run passed. Existing FSEvents watcher/CDS warnings remain
  nonfatal. No source functionality/frontend/extension/migration redesign.

No frontend/extension/Dockerfile/CI/evaluation/migration file changed. No hosted CI,
Datadog/CloudWatch/collector ingestion, metric/trace/alert delivery, cloud runtime or
new AWS costs verified. V1–V7 unchanged; no secret store or real model/provider added.
