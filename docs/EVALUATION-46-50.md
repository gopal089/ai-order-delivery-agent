# Processes 46–50: local evidence and explicit limits

Locked starting commit: `9c145103ffb9fcbac216ff71f639fc391c83ab54`, clean `main`.
This batch does not commit, push, authenticate to AWS, invoke Bedrock, or provision infrastructure.
Results below concern this checkout and local tests, not cloud deployment or customer APIs.

## 46 — deterministic evaluation

The existing twelve-case scripted-model baseline is retained. The catalog now has 23 automated
categories, including explicit user ownership, provider deadline, tool timeout, stale history, and
model/provider unavailability. Each maps to inspected assertions in existing production-boundary
tests, not a new model or guessed provider. Agent unit tests use mocked tools. Conversation API
tests exercise Spring authentication/authorization and real local PostgreSQL persistence with
test-only model/provider fixtures. Provider deadline tests use bounded local workers.

Parameterized categories now require the exact distinct invocation set, not merely a count.
The schema-v2 reporter rejects duplicate JUnit identities, invalid roots/counters and malformed
baseline records; all twelve distinct baseline test names must correspond to the twelve emissions.
A PASS emission cannot override a failed/missing/skipped assertion or a failed execution. Tests reproduce eight defects
in the old reporter before the fixes. Unknown suite/method labels and all parameterized argument
labels are hashed before export. Catalog-approved method/suite labels are repository-owned names.
Prompts, system-out, exception messages, credentials and security-context values are not exported.
Raw Gradle/JUnit diagnostics remain local ignored artifacts; they are not safe public reports.

PASS means observed deterministic assertions passed. FAIL means invalid evidence/assertion/execution;
NOT_RUN means insufficient, missing, skipped or stale evidence. MANUAL_REVIEW and BLOCKED remain
distinct non-passing categories. Imported results without --run are explicitly not freshness-verified.
JUnit is local evidence, not a signed attestation; maliciously fabricated complete reports are not
proof of execution. No real-model accuracy, semantic quality, token usage or cost is measured.

The exact no-tool Chennai claim remains unverified and withheld, never EXTERNALLY_SUPPORTED.
Current typed provider fields can be supported by backend retrieval evidence; model prose cannot.
History/stored fields do not become fresh retrieval. Support is not independent provider truth or
a freshness guarantee. Real-model intent/semantic attacks and provider truth remain MANUAL_REVIEW.

## 47 — FTA: DECISION REQUIRED

Before source changes, repository-wide hidden/source/documentation/configuration/comment search and
tracked git grep found only two preexisting PROJECT_HANDOFF mentions, explicitly undefined.
Generated build files, dependencies, .git and local secrets were excluded from the source search.
No project definition exists. Required from owner: expansion/meaning, intended evaluation subject,
inputs/fixtures, method/rubric, acceptance criteria and required evidence. No FTA implementation,
assumed interpretation, methodology or passing result was created.

## 48 — bounded local performance

LocalRenderingPerformanceTests measures the existing ResponseGroundingBoundary on one typed
test-only package-location fixture with actual backend evidence, synthetic location and epoch time.
No HTTP/model/database/Redis/customer traffic, new framework or latency pass threshold. Single
thread, 25 warm-up invocations, 100 measured iterations. nanoTime wraps only enforce(), excluding
correctness assertions. Each result is checked for supported fixture fields and withheld model prose.
The output records total/min/max/arithmetic mean, Java version, OS and architecture. No percentiles
or request throughput are inferred. Shared-host/JIT/GC/scheduler effects and tiny fixtures limit use.

Reproduce from backend:

```sh
./gradlew test --tests '*LocalRenderingPerformanceTests' --rerun-tasks --console=plain
```

Measured locally on 2026-10-04: Java 21.0.12.1, Mac OS X, aarch64. The exact targeted command above
passed one test: total 5,350,250 ns, minimum 33,167 ns, maximum 184,375 ns,
arithmetic mean 53,502.50 ns across 100 iterations, concurrency one, 25 warm-ups.
Full-suite executions also run this bounded
test; timings differ between runs and are not production capacity estimates. Authentication, refresh,
SQL, Redis, end-to-end chat, external providers and concurrent request performance are NOT MEASURED.
Existing timeout/iteration/saturation assertions are correctness tests, not latency distributions.

## 49 — offline cost inventory

evaluation/cost-drivers.json records sixteen possible drivers, input variables and controls, with
prices, usage, account bill and estimate explicitly null/NOT VERIFIED. Inspected sources: README,
PROJECT_HANDOFF architecture/roadmap, API-GATEWAY-DESIGN, deployment README and DEV/QA definitions.
The cost-management skill guided missing-price/account/budget separation, not a billing API call.

No resources were created and no AWS/model operations occurred in this batch, so there are no new
resource/inference charges from this work. This is NOT a statement that the user's AWS account bill
is zero. Actual balances, credits, other resources, account bill and regional prices are unverified.
Future estimates require approved region, topology, sizing/HA, usage volumes, retention, inference
model/token rates, independently sourced pricing and budget approval. No fake prices in tests.
Timeouts/tool-call caps bound work but do not guarantee a monetary spend cap. Review NAT versus
endpoints and ingress choices before pricing; do not quietly share DEV/QA stores to reduce cost.

## 50 — actual source and local security evaluation

| Area | Source / test evidence | Limits or decisions |
| --- | --- | --- |
| Authentication | Argon2id password encoding; normalized email; HS256 signature/issuer/claims; active-user/session lookup; random 32-byte refresh values stored only as SHA-256 hashes; locked rotation, reuse/session revocation; existing authentication/registration/authorization tests | Key management/rotation, live TLS and timing-side-channel resistance NOT VERIFIED |
| Abuse | Redis atomic INCR/TTL login IP/identity and refresh IP/session limits, hashed keys, failure closed; local integration/Redis-outage tests | No registration/chat global abuse limiter; public-edge/WAF policy and trusted-proxy deployment need approval |
| Ownership | TenantAuthorization plus tenant/user-scoped SQL and composite ownership FKs; foreign/missing resources denied alike; existing cross-user/tenant tests | No PostgreSQL RLS; cloud DB policy/runtime role separation NOT VERIFIED |
| Existence disclosure | Unknown-email/wrong-password/inactive login use same safe failure; missing/foreign owned resources use AccessDenied | Registration deliberately returns EMAIL_ALREADY_REGISTERED/409: enumeration policy DECISION REQUIRED; unchanged registration contract |
| Credentials | Ownership before store retrieval; backend-only material and references; zeroization/closed-material and lifecycle tests; safe errors/audits/log tests | CredentialStore and real provider adapters absent; no production secret-store or customer integration certification |
| SSRF | SecureHttpTransport checks all DNS answers/public literals and uses validated addresses; HTTPS outside local; no redirects/proxy/retry/cookies; verified TLS hostname; bounded DNS/call/deadline and 1 MiB body; local socket-mapped fixtures only | Real-provider interoperability/unusual networks and deployed egress NOT VERIFIED; configuration validator alone is not execution protection |
| AI | Closed five-tool allowlist, strict internal-ID arguments, owner checks, 3 calls, bounded workers/deadlines, untrusted history/provider data, backend provenance, controlled rendering, transaction/audit rollback | Deterministic tests only; no semantic guarantee against every real-model attack or provider misinformation |
| Workers | Model workers clear SecurityContext/MDC and model request carries no security identity; trusted backend tool/provider workers retain existing caller authentication for ownership checks, clear afterward | Existing trusted-tool boundary preserved; no NEW context exposure. A literal prohibition of all trusted-tool authentication conflicts with the locked architecture and needs separate design approval, not removal of checks |
| Logging/reporting | Bounded fix: raw request path replaced with MVC route template or UNMATCHED; regression verifies synthetic path/query content absent; reporter identifier/diagnostic sanitization; credential redaction/audit tests | Client correlation IDs remain untrusted; default controlled log paths are tested, not every arbitrary debug configuration |
| API | Explicit CORS origins, no credentialed cookies, Bearer/stateless security, minimal probes, protected aggregate health, sanitized request IDs; default security-header assertions added | HSTS/end-to-end TLS at real ingress, total JSON-byte/request limits and production origins NOT VERIFIED |
| Source/images | .env/private-key ignore rules and allowlisted backend Docker context; non-root runtime UID10001 in Dockerfile; source secret scanner | No new image built/scanned in this batch; image layers, CVEs, live runtime privileges and supply-chain attestations NOT VERIFIED |
| Database/cache | Local health and schema tests; immutable V1–V7, validate-on-migrate, clean disabled, Hibernate DDL none; loopback-only Compose published ports | Local PostgreSQL role privileges checked separately below; Redis unauthenticated LOCAL service is not a production policy. App currently migrates with its configured DB role; dedicated migration/runtime roles require approval |
| Cloud | DEV/QA review-only definitions with null approvals and deployable=false; IAM/private subnet/SG/WAF/TLS requirements only | No IAM policies/security groups/cloud resources deployed or verified. Backend port8080 TLS/cert delivery and 30s chat versus ingress deadline remain deployment blockers |

No authentication, ownership, SSRF, TLS validation or tool controls were disabled. Frontend,
extension, dependencies, infrastructure definitions and Flyway migrations remain unchanged.
No exhaustive penetration test, Java dependency-CVE scan, deployed cloud audit or production
readiness certification is claimed. Scanner/test execution results are recorded below.

## Executed verification — 2026-10-04

Local PostgreSQL 17.11 and Redis 7.4.11 Compose services were running and healthy. Environment
was silently loaded from the ignored local .env with DATABASE_PASSWORD mapped to POSTGRES_PASSWORD,
DATABASE_USERNAME to POSTGRES_USER, and DATABASE_URL to loopback PostgreSQL. No secret value is copied here.

- backend: `./gradlew test --rerun-tasks --console=plain` — PASS after the logging fix.
- root: `PYTHONDONTWRITEBYTECODE=1 python3 evaluation/report.py --run --scope full` —
  PASS, 306 tests, 30 suites, zero failures/errors/skips; all 23 automated categories PASS,
  three MANUAL_REVIEW and one BLOCKED. This later fresh run includes the new header assertions.
- backend: `./gradlew bootJar --console=plain` — PASS.
- root: `PYTHONDONTWRITEBYTECODE=1 python3 evaluation/report.py --run --scope baseline` —
  PASS, twelve standalone cases, zero failures/skips; no database or AWS needed.
- root: `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s evaluation -p 'test_*.py' -v` —
  PASS, 27 tests (24 reporter, three catalog/cost-inventory).
- backend: `./gradlew test --tests '*LocalRenderingPerformanceTests' --rerun-tasks --console=plain` —
  PASS, one measurement test; exact results above.
- Registration regression: 7 passed. Authentication regression: 12 passed. Authorization: 16 passed.
  Tenant isolation: 12 passed. Auth rate limiting: 11 passed plus 2 Redis-unavailable tests.
  Request observability: 8 passed, including raw-path/query non-disclosure and security headers.
- Direct local read-only SQL: Flyway versions 1–7 all successful. Current local DB role rolsuper=true:
  not a least-privilege runtime deployment. No migration bytes changed; Redis ping returned PONG.
- gitleaks dir scan of the tracked-plus-untracked non-ignored source snapshot: zero findings.
  This is a scoped pattern scan, not a guarantee of every conceivable secret. .env is ignored.
- web: `npm audit --omit=dev --json` — exit 0, zero production dependency advisories.
  `npm audit --json` — exit 1, five high-severity lint dependency-chain advisories:
  eslint-config-next, @next/eslint-plugin-next, fast-glob, micromatch, braces.
  No package changes; remediation requires a separate frontend-toolchain task.
- Initial regression phase deliberately demonstrated eight reporter failures and a raw-path logging
  failure. They were fixed and passing reruns replace those failures; no failed result is called PASS.
- Infrastructure/frontend/extension tests not rerun: no files in those components changed.
  No container image build/layer scan or deployed cloud/runtime revalidation in this batch.

Evaluation artifacts remain ignored under backend/build/reports/evaluation. The full JSON report is
retained before targeted Gradle runs replace XML. A dirty working-tree marker and locked commit hash
identify the uncommitted run; no commit/push performed.
