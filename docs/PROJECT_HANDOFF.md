# AI Order & Delivery Agent — Project Handoff

Last updated: 2026-10-04 (Asia/Kolkata)

## Current continuation — Processes 46–50 (uncommitted)

Started clean on main at locked 9c145103ffb9fcbac216ff71f639fc391c83ab54. Deterministic evidence
reporting now has 23 automated categories and schema-v2 fail-closed duplicate/parameter-set/
counter/baseline checks, with unknown report identifiers hashed. Local rendering performance has
a bounded test-only measurement (25 warm-ups, 100 iterations, concurrency one). An offline
16-driver cost inventory keeps all prices/usage/current-account cost unknown. FTA is still undefined:
DECISION REQUIRED, not implemented. Security evaluation fixed raw request-path logging to use server
route templates or UNMATCHED; authentication/ownership/provider/model architecture is unchanged.

Fresh full evaluation: 306 tests/30 suites, zero failures/errors/skips; 23 automated PASS, three
MANUAL_REVIEW, one live Bedrock BLOCKED. Standalone baseline 12 PASS; Python evaluation tests 27 PASS;
bootJar PASS. V1–V7 unchanged and local history all successful; Redis PONG. Production npm audit zero
advisories; full frontend lint dependency chain retains five high advisories (no dependency changes).
No AWS access/authentication/writes/deployment or Bedrock invocation; no image/runtime cloud audit.
Registration enumeration policy, local superuser versus production migration/runtime roles,
request-byte/edge abuse policy, deployment/TLS/ingress budget and real-model prerequisites remain open.
See docs/EVALUATION-46-50.md and evaluation/cost-drivers.json for measured scope and limits.
No commit/push performed. Earlier continuation reports below are historical, not current revalidation.

## Previous continuation — Processes 41–45

Processes 41–45 continuation starts from clean checkpoint b6c1215449cd87ae8551b1ecc195397185288029.
AWS CLI is present but STS reports missing credentials; no profile/account/region/model approval was
discovered. No AWS resources or IAM policies were created and no deployment occurred. DEV/QA now have
review-only tool-neutral infrastructure requirements under infrastructure/environments/, offline
security/isolation validation and tests. These are NOT deployable IaC or provisioned environments.
Plain CloudFormation is recommended for approval; ingress/WAF/TLS, budget, sizing and Gateway execution
timeout remain unresolved. Existing Spring profiles, frontend, extension, backend behavior and V1–V7
are unchanged. See infrastructure/README.md for evaluated resources, configuration and deployment gates.
Evaluation now has a safe JUnit evidence catalog/reporter using the existing deterministic suite;
generated output stays in ignored backend/build/reports/evaluation/. Bedrock remains BLOCKED and
unimplemented. See evaluation/README.md. This continuation does not assert cloud or production readiness.
Fresh verification: 304 backend tests, 12 standalone deterministic cases, 13 reporter tests and 10
environment-definition tests passed; bootJar and local container smoke checks passed. Full evaluation
evidence records 17 automated categories PASS, three MANUAL_REVIEW and live Bedrock BLOCKED.

This document is the durable handoff for continuing the project from another ChatGPT/Codex account. It records the repository state, implemented functionality, verified behavior, security constraints, known issues, and the intended roadmap. Treat the repository as authoritative when it differs from this document, and update this document when a later implementation changes the recorded state.

Latest verification (2026-10-04): `./gradlew test --rerun-tasks --console=plain` passed 281 tests with zero failures/errors/skips; `bootJar` passed. The 263-test baseline was independently rerun. A backend response boundary now withholds arbitrary generated prose from chat output and new assistant persistence, and masks legacy assistant/OTHER public history. Backend-only typed tool provenance and mandatory AI_RESPONSE_GROUNDING audit distinguish retrieval from answer support. All model answers remain MODEL_GENERATED_UNVERIFIED even after retrieval; stronger categories are reserved, not implemented claim classifiers. The exact unsupported Chennai claim with no tool is tested at unit and authenticated API/database layers and is never EXTERNALLY_SUPPORTED or shown/stored as an assistant answer. Normal packaged startup returns safe 503 without a model; a separate temporary test fixture exercised runtime chat/tool/provenance/persistence/isolation/request IDs. Fixtures/processes were cleaned. No real model/provider, AWS SDK, credential store, migration, cache or ingestion was added. BEDROCK CONFIGURATION BLOCKED: approved region/model/profile/authentication/IAM access absent; no real invocation. SDK/API readiness was researched using official AWS sources, not implemented. Complete factual grounding and prompt-injection protection remain unverified. See `docs/RESPONSE-GROUNDING-AND-BEDROCK-READINESS.md`, `docs/CHAT-AND-AI-SECURITY.md` and `docs/PROVIDER-EXECUTION-VERIFICATION.md`. Older frontend descriptions remain historical, not reverified in this backend batch.

Latest continuation (Processes 31–35): the 281-test baseline was rerun successfully; the first full
post-change run passed 304 tests, zero failures/errors/skips, and bootJar passed. ControlledFact now
projects existing typed order/shipment/tracking/location fields. The backend renders those values with
source times and can classify the controlled factual portion EXTERNALLY_SUPPORTED; model prose remains
withheld. Chennai/tomorrow claims cannot replace provider Bangalore or invent an ETA. Versioned JSON
assistant envelopes reuse messages.content with no migration; stored snapshots remain contextual, not
current retrieval. Twelve deterministic evaluation cases exist under the existing Gradle/JUnit harness.
Safe actual environment/.env/CLI/file-presence discovery found no AWS region, credentials configuration
or approved model/profile, so Bedrock remains BLOCKED, NOT IMPLEMENTED/NOT VERIFIED. See
CONTROLLED-FACTUAL-RENDERING.md and evaluation/README.md. This supersedes the preceding withhold-all
status; it is not a guarantee of provider truth, freshness or complete model grounding.

## 1. Project purpose and primary requirements

Latest continuation (Processes 36–40, 2026-10-04): backend regression rerun passed 304 tests,
zero failures/errors/skips; bootJar passed. Authenticated web `/chat` and local-only MV3 popup chat
now call the existing backend conversation contract. Both render controlled facts as escaped text,
not arbitrary model prose. Web and extension each pass 10 focused client tests plus typecheck/lint/build.
Web includes a tested pending-refresh/logout race guard in the existing in-memory session mechanism.
Backend and standalone Next multi-stage images build and run non-root; PostgreSQL/Redis readiness,
Flyway V1–V7, registration/login/rotation/revocation, ownership denial, missing-config failure and
SIGTERM graceful shutdown were verified. Production browser login/dashboard/integration/chat-503,
logout, unauthenticated redirect and reload-cleared session were observed. Disposable runtime
containers/users/tenant data/Redis keys were cleaned; existing infrastructure preserved.
OpenAPI 3.0.3 routing inventory validates and matches 20 existing operations; API Gateway remains
DESIGN ONLY, not deployed. Production backend/extension hostname, ingress/WAF choice and synchronous
Gateway timeout alignment require decisions. Bedrock remains blocked; no real provider/model calls
or SDK were added. Native Chrome loaded-extension execution remains NOT VERIFIED (port 8080 already
has an existing Java process, which was not stopped). Five high npm-audit findings in web lint/build
tooling remain unresolved; no forced breaking upgrade. See WEB-EXTENSION-AND-CONTAINERS.md and
API-GATEWAY-DESIGN.md. This continuation supersedes historical static-only web/extension descriptions
below. No backend application code or existing migration changed in this batch; no commit/push.

The AI Order & Delivery Agent is intended to become a production-oriented, multi-tenant web platform with a Chrome extension. A customer should eventually be able to register, authenticate, configure credentials for their own external order/tracking provider, ask natural-language questions, and receive factual order and delivery information retrieved through controlled backend tools.

The application does **not** own or define customer order/tracking APIs. Customer external systems remain the source of truth. No provider endpoint or schema may be invented. Until real provider documentation is supplied, only abstractions, mocks, DTOs, and test fixtures may be created.

Core product requirements:

- Web registration and authentication.
- A separate React web application and Chrome Manifest V3 extension.
- Strict tenant/user isolation.
- Backend-owned authorization and credential access.
- External credentials must never be exposed to the LLM, web client, extension bundle, logs, or error responses.
- The AI may select only approved, typed tools; it may not make arbitrary network calls or security decisions.
- Order, tracking, status, and package-location claims must be grounded in external provider data.
- Current and historical conversation context must never override newer authoritative provider data.
- PostgreSQL is the authoritative persistent application store; Redis is only for justified temporary/cache use.
- AWS, Bedrock, API Gateway, ECS/Fargate, external telemetry backends, evaluation, and CI/CD are planned but not implemented. A local security-observability and audit foundation is implemented.

## 2. Non-negotiable engineering and security rules

- Work incrementally and stop after each approved task.
- Inspect existing files and tests before changing them.
- Never invent external APIs, schemas, credentials, or order/tracking facts.
- Never commit or print passwords, API keys, AWS credentials, tokens, certificates, or customer secrets.
- Never put secrets in frontend or extension code.
- Never expose customer integration credentials to an AI model.
- Never let the AI decide authentication, authorization, tenant ownership, URLs, or network permissions.
- Never access tenant-specific data using an untrusted tenant ID supplied by a client. The eventual authenticated identity must supply tenant/user context.
- Never rewrite working code or alter public contracts without approval.
- Never edit an already-applied Flyway migration. Add a new versioned migration.
- Never use Hibernate automatic schema mutation. Flyway is the only schema-management mechanism.
- Never enable PostgreSQL RLS until its authenticated database-session design is explicitly approved.
- Never assume production infrastructure, AWS credentials, AWS regions, or customer provider behavior.
- Do not modify the Chrome extension during unrelated backend tasks.
- Do not add Chrome permissions without explicit approval.
- Use fake/local-only data in tests. Never use real customer credentials or production data.

## 3. Technology stack

### Implemented locally

- macOS with OrbStack/Docker.
- Git repository on branch `main`.
- Java 21 Gradle toolchain.
- Spring Boot 4.1.1.
- Gradle wrapper 9.7.1.
- Spring Web MVC.
- Bean Validation.
- Spring Data JPA and Hibernate.
- HikariCP.
- Spring Security 7.1.1 authentication/filter-chain support and JOSE JWT signing.
- Argon2id password hashing through `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`.
- BouncyCastle `bcprov-jdk18on` 1.84.
- PostgreSQL JDBC driver 42.7.13 (resolved by Spring Boot dependency management).
- PostgreSQL 17.11 Alpine container.
- Flyway 12.4.0 with the PostgreSQL module.
- Redis 7.4.11 Alpine container.
- Docker Engine 29.4.0 and Docker Compose 5.1.2.
- OrbStack 2.2.3.
- React 19.2.8.
- Next.js 16.3.8 with TypeScript 5 for the web application.
- React 19.2.8, TypeScript 6.0.2, Vite 8.3.0, and Oxlint for the extension.
- Chrome Manifest V3 extension architecture.
- Local Node.js observed as 24.13.0 and npm 11.6.2.

The host JVM observed during diagnostics is Java 25.0.1, while the backend build explicitly requests and compiles with the Java 21 toolchain.

### Planned but not implemented

- Domain ingestion/mutations and remaining business services; protected order/shipment reads now exist.
- LangChain4j.
- Amazon Bedrock and real external provider adapters.
- Amazon API Gateway.
- ECS/Fargate, ECR, RDS, ElastiCache, Secrets Manager, KMS, IAM, VPC, security groups, CloudFront/S3, and WAF.
- OpenTelemetry, Datadog, and CloudWatch integrations.
- Infrastructure as code.
- GitHub Actions CI/CD.
- OpenAPI generation.
- Advanced/real-model AI evaluation infrastructure; a minimal deterministic baseline now exists.

## 4. Current architecture

Current implemented path:

```text
HTTP client
  -> Spring MVC registration/login/refresh/logout controllers
  -> Bean Validation
  -> registration/authentication services
  -> Spring Security DaoAuthenticationProvider / Argon2id password encoder
  -> signed short-lived JWT access tokens
  -> rotating opaque refresh tokens stored only as SHA-256 hashes
  -> Spring Data JPA repository
  -> HikariCP / PostgreSQL JDBC
  -> local PostgreSQL

Spring Boot startup
  -> Flyway validation/migration
  -> JPA EntityManagerFactory

Local web app
  -> static Next.js landing page only

Chrome extension
  -> popup/options/service worker
  -> validated backend base-URL setting in chrome.storage.sync
  -> generic API client scaffold
  -> no authentication or agent calls yet
```

Target architecture from the master specification:

```text
User
  -> Web application / Chrome extension
  -> Amazon API Gateway
  -> Spring Boot backend
  -> Authentication and authorization
  -> AI agent orchestrator
  -> LangChain4j
  -> model-provider abstraction
  -> Amazon Bedrock / Claude Sonnet initially
  -> controlled typed tools
  -> secure credential/integration layer
  -> customer's documented external order/tracking API

Backend -> PostgreSQL
Backend -> Redis where justified
Credentials -> AWS Secrets Manager and/or approved encrypted store using KMS
Telemetry -> OpenTelemetry -> Datadog + CloudWatch
```

The target architecture is a plan, not evidence that any AWS or AI component exists.

## 5. Repository and folder structure

Generated directories such as `.git/`, `.gradle/`, `backend/build/`, `node_modules/`, `web/.next/`, and `extension/dist/` are intentionally omitted from this source-oriented tree. The local `.env` is also omitted and must never be read into chat or committed.

```text
ai-order-delivery-agent/
├── .env.example
├── .github/
│   └── .gitkeep
├── .gitignore
├── README.md
├── compose.yaml
├── backend/
│   ├── .gitattributes
│   ├── .gitignore
│   ├── HELP.md
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew
│   ├── gradlew.bat
│   ├── gradle/wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── src/
│       ├── main/
│       │   ├── java/com/aiorderdeliveryagent/backend/
│       │   │   ├── BackendApplication.java
│       │   │   ├── auth/
│       │   │   │   ├── ApiError.java
│       │   │   │   ├── EmailAlreadyRegisteredException.java
│       │   │   │   ├── RegisterRequest.java
│       │   │   │   ├── RegisterResponse.java
│       │   │   │   ├── RegistrationExceptionHandler.java
│       │   │   │   ├── UserAccount.java
│       │   │   │   ├── UserAccountRepository.java
│       │   │   │   ├── UserRegistrationController.java
│       │   │   │   └── UserRegistrationService.java
│       │   │   └── config/
│       │   │       └── PasswordConfiguration.java
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-local.yml
│       │       ├── application-dev.yml
│       │       ├── application-qa.yml
│       │       ├── application-prod.yml
│       │       └── db/migration/
│       │           ├── V1__baseline.sql
│       │           ├── V2__initial_database_schema.sql
│       │           ├── V3__index_refresh_token_ownership.sql
│       │           └── V4__add_user_registration_credentials.sql
│       └── test/java/com/aiorderdeliveryagent/backend/
│           ├── BackendApplicationTests.java
│           ├── PostgreSqlConnectionTests.java
│           └── auth/
│               └── UserRegistrationIntegrationTests.java
├── docs/
│   ├── .gitkeep
│   ├── LOCAL-DEVELOPMENT.md
│   └── PROJECT_HANDOFF.md
├── evaluation/
│   └── .gitkeep
├── infrastructure/
│   └── .gitkeep
├── extension/
│   ├── .gitignore
│   ├── .oxlintrc.json
│   ├── README.md
│   ├── package.json
│   ├── package-lock.json
│   ├── popup.html
│   ├── options.html
│   ├── tsconfig.json
│   ├── tsconfig.app.json
│   ├── tsconfig.node.json
│   ├── vite.config.ts
│   ├── public/
│   │   ├── favicon.svg
│   │   ├── icons.svg
│   │   └── manifest.json
│   └── src/
│       ├── api/client.ts
│       ├── assets/hero.png
│       ├── assets/react.svg
│       ├── assets/vite.svg
│       ├── background/service-worker.ts
│       ├── components/Brand.tsx
│       ├── options/
│       │   ├── Options.tsx
│       │   ├── main.tsx
│       │   └── options.css
│       ├── popup/
│       │   ├── Popup.tsx
│       │   ├── main.tsx
│       │   └── popup.css
│       ├── shared/messages.ts
│       ├── shared/settings.ts
│       └── styles/base.css
└── web/
    ├── .gitignore
    ├── AGENTS.md
    ├── CLAUDE.md
    ├── README.md
    ├── eslint.config.mjs
    ├── next.config.ts
    ├── package.json
    ├── package-lock.json
    ├── tsconfig.json
    ├── public/
    │   ├── file.svg
    │   ├── globe.svg
    │   ├── next.svg
    │   ├── vercel.svg
    │   └── window.svg
    └── src/app/
        ├── favicon.ico
        ├── globals.css
        ├── layout.tsx
        ├── page.module.css
        └── page.tsx
```

## 6. Completed implementation steps and status

| Step | Status | Notes |
|---|---|---|
| Create public project repository and local root | Complete | Repository name `ai-order-delivery-agent`; Git initialized; public GitHub remote configured. |
| Connect/verify GitHub repository | Complete | Remote is configured for `gopal089`; local `main` tracks `origin/main`; registration and Step 2 checkpoints are pushed. |
| Create monorepo structure | Complete | Backend, web, extension, evaluation, infrastructure, docs, and `.github` exist. |
| Create Java/Spring Boot backend | Complete scaffold | Java 21 toolchain, Spring Boot 4.1.1, Gradle, application starts. |
| Create React/Next.js web application | Complete scaffold | Responsive landing page exists; no registration/login/dashboard integration. |
| Create Chrome extension | Complete scaffold/build | Manifest V3 popup, options page, service worker, API client, settings validation, and build output exist. |
| Load/test extension in Chrome | Needs durable re-verification | `extension/dist/` exists. No permanent test report is stored in the repository; a new session should manually re-check unpacked loading before relying on prior UI state. |
| Create local Docker infrastructure | Complete and previously verified | PostgreSQL and Redis Compose services, persistent volumes, loopback ports, and health checks. |
| Configure application environments | Complete | `local`, `dev`, `qa`, and `prod` profiles exist; secrets are external. |
| Configure PostgreSQL/JPA | Complete | DataSource, JPA/Hibernate, PostgreSQL driver, connection test. |
| Add Flyway | Complete | Boot Flyway starter and PostgreSQL module; migrations execute and validate. |
| Create initial database schema | Complete | Ten requested tables, composite ownership constraints, indexes, cache metadata. |
| Implement user registration | Complete | Endpoint, validation, Argon2id, duplicate protection, JPA persistence, error handling, tests, docs. |
| Implement login and session lifecycle | Complete | Login, signed access tokens, hashed rotating refresh tokens, reuse detection, expiration, logout, tests, and documentation. |
| Implement authenticated context and authorization boundary | Complete | Bearer JWT validation, server-resolved tenant/user/session principal, active-session enforcement, reusable ownership guards, tests, and documentation. |
| Implement tenant/user data-isolation boundary | Complete | Authoritative ownership lookup and reusable integration/order/conversation/message/credential guards with cross-tenant and same-tenant cross-user tests. |
| Create external order provider abstraction | Complete | Provider-neutral five-operation Java interface, trusted call context, typed request/result models, safe exception hierarchy, contract tests, and no real adapter or credential handling. |
| Implement secure integration configuration | Complete | Authenticated tenant/user-scoped integration CRUD, base-URL validation, credential-store abstraction, safe audit events, V6, tests, and no real secret store or HTTP transport. |
| Implement authentication rate limiting | Complete | Atomic Redis fixed-window controls for login identity/IP and refresh session/IP, generic 429/503 responses, real-Redis tests, and no raw credentials or tokens in keys. |
| Implement security observability and audit foundation | Complete | Validated request IDs, structured JSON logs with authenticated context, centralized redaction, transactional security/integration audits, and public liveness/readiness probes. |
| Verify provider execution and secure HTTP transport | Backend boundary verified; real providers blocked | Public-address DNS validation/pinning, no redirects/proxies/retries, TLS verification, fixed timeouts, 1 MiB response cap, synthetic execution tests, no credential persistence or real adapter. |
| Credential lifecycle, execution budget, future tool boundary | Backend/test boundary verified; production/AI blocked | Scope-checked lifecycle test fixture and fail-closed rotate contract; 20-second/five-attempt provider budget; deterministic test provider; integration/session-bound tools with existing internal-order ownership checks and explicit untrusted output. No real secret store, AWS or AI framework. |
| Order/shipment/tracking domain boundary | Backend services verified; real providers/API/ingestion deferred | Scoped persisted reads and provider reference resolution; shipment/event ownership guards; explicit provider/unavailable provenance; no cache or conversation fallback; no domain HTTP controllers or migration. |
| Verify FSEvents warning | Complete | Non-fatal Gradle/macOS watcher warning; no application/runtime correctness impact. |

## 7. Database schema

All requested application tables are in the PostgreSQL `public` schema. Tenant-owned domain tables have a non-null `tenant_id`. `audit_events.tenant_id` is nullable only for truthful unauthenticated events that have no known tenant; an audit actor may never exist without a tenant. Internal primary keys use `bigint GENERATED ALWAYS AS IDENTITY`; tenant IDs use UUIDs. Timestamps use `timestamptz`.

### `users`

- `id` primary key.
- `tenant_id` non-null UUID, defaulting to `gen_random_uuid()`.
- `email` non-null text.
- `display_name` nullable text.
- `is_active` non-null boolean.
- `created_at`, `updated_at`.
- `password_hash` nullable at the database level for migration compatibility, but registration-created JPA entities require it.
- Unique `(tenant_id, id)` supports composite tenant-aware foreign keys.
- Unique tenant-scoped lowercase email index.
- Unique global `lower(btrim(email))` index for the current registration contract, which has no tenant input.
- Password-hash constraint permits only null or encoded Argon2-family strings.

### `refresh_tokens`

- `id`, `tenant_id`, `user_id`, `session_id`, `token_hash`, `expires_at`, `revoked_at`, `created_at`.
- Composite FK `(tenant_id, user_id)` to `users`.
- Only token hashes may be stored; plaintext refresh tokens are prohibited.
- Active-token expiry index and complete ownership index.
- Raw tokens are never stored. `session_id` groups rotations for reuse detection and logout revocation.

### `integrations`

- `id`, `tenant_id`, `user_id`, `provider_key`, `display_name`, nullable normalized `base_url`, nullable opaque `credential_reference`, nullable non-secret `credential_type`, `settings`, `is_enabled`, timestamps.
- Composite user ownership FK.
- `settings` must be a JSON object and is explicitly for non-secret configuration only.
- Raw integration credentials remain outside PostgreSQL; no credential-store implementation or provider adapter exists.

### `conversations`

- `id`, `tenant_id`, `user_id`, optional `title`, `status`, timestamps.
- Composite user ownership FK and recent-conversation index.

### `messages`

- `id`, `tenant_id`, `user_id`, `conversation_id`, `message_role`, `content`, optional `model_name`, `created_at`.
- Composite FK to a conversation with the same tenant and user.
- Conversation/time ordering index.

### `orders`

- `id`, `tenant_id`, `user_id`, `integration_id`, `external_order_id`, optional status and cache fields, source/fetch/cache timestamps, creation/update timestamps.
- Composite FK to an integration owned by the same tenant and user.
- Unique external order key within tenant/user/integration.
- Cached payload must be a JSON object and requires `cache_expires_at`; expiration must be after `fetched_at`.
- The external provider remains authoritative.

### `shipments`

- `id`, `tenant_id`, `user_id`, `order_id`, optional external shipment ID, carrier, tracking number, status, cache/source/fetch fields, timestamps.
- Composite FK to an order owned by the same tenant/user.
- Partial unique external shipment index, tracking lookup index, and order/time index.
- Cached payload follows the same TTL rules as orders.

### `tracking_events`

- `id`, `tenant_id`, `user_id`, `shipment_id`, optional external event ID, status, description, location, occurrence timestamp, optional source payload, creation timestamp.
- Composite FK to a shipment owned by the same tenant/user.
- Partial unique external event index and shipment/timeline index.

### `tool_executions`

- `id`, `tenant_id`, `user_id`, optional conversation and integration references, tool name/status, redacted request/response metadata, error code, start/completion timestamps.
- Tenant-aware FKs to users, conversations, and integrations.
- Metadata comments explicitly prohibit credentials and raw secrets.
- Tool execution functionality is not implemented.

### `audit_events`

- `id`, nullable `tenant_id`, nullable `actor_user_id` for unauthenticated/system events, event/resource/outcome fields, redacted metadata, occurrence time.
- Tenant-aware actor FK where an actor exists.
- V7 permits an absent tenant for events such as unknown-email login failures and enforces that an actor can exist only when a tenant exists.
- Tenant/time and actor/time indexes.
- Centralized audit production is implemented for login success/failure, refresh success/failure/reuse, logout, authentication rate limiting, and integration lifecycle events.

Database-level RLS is **not** enabled. Structural ownership uses `tenant_id`, `user_id`, composite unique keys, and composite foreign keys. Spring Security now establishes a server-resolved tenant/user/session context, and reusable ownership guards are available; individual business-domain services are not implemented yet.

## 8. Flyway migrations

Already-applied migrations are immutable. Never edit V1–V7; add V8 or later.

| Migration | Purpose | Verified state |
|---|---|---|
| `V1__baseline.sql` | Establishes the first Flyway-managed version without inventing business tables. | Applied successfully. |
| `V2__initial_database_schema.sql` | Creates users, refresh tokens, integrations, conversations, messages, orders, shipments, tracking events, tool executions, and audit events with ownership constraints and indexes. | Applied successfully. |
| `V3__index_refresh_token_ownership.sql` | Adds a full `(tenant_id, user_id)` index for refresh-token FK/ownership operations, complementing the active-token partial index. | Applied successfully. |
| `V4__add_user_registration_credentials.sql` | Adds `users.password_hash`, its Argon2-format check constraint, explanatory comment, and race-safe global normalized-email unique index. | Applied successfully. |
| `V5__add_refresh_token_sessions.sql` | Adds the non-null refresh-token `session_id` and its tenant/user/session index. | Applied successfully. |
| `V6__secure_external_integration_configuration.sql` | Adds normalized base-URL storage plus opaque credential-reference/type metadata and integrity/security constraints. | Applied successfully. |
| `V7__allow_unauthenticated_audit_events.sql` | Allows truthful unauthenticated audit events without a fabricated tenant while preventing actor-without-tenant rows. | Applied successfully. |

Common Flyway configuration in `application.properties`:

- Enabled.
- Migrations loaded from `classpath:db/migration`.
- Validate on migrate enabled.
- Baseline-on-migrate disabled.
- Flyway `clean` disabled.

Latest verification validated all seven migrations and reported the schema up to date.

## 9. PostgreSQL configuration

Local host connection:

- Host: `localhost` / `127.0.0.1`.
- Port: `5432`.
- Database, username, and password: supplied only through ignored local environment values.
- Compose service name for future container-to-container communication: `postgres`.
- Image: `postgres:17.11-alpine3.24`.
- Persistent volume: `ai-order-delivery-agent_postgres_data`.
- Health check: `pg_isready` using container environment values.
- Port is bound to loopback only.

The backend requires `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Each application profile reads these variables. The PostgreSQL JDBC driver is present and resolves as version 42.7.13. The previously observed `Failed to determine a suitable driver class` error is resolved.

JPA/Hibernate settings:

- Open EntityManager in View disabled.
- `ddl-auto: none`.
- Hibernate JDBC timezone set to UTC.
- Flyway runs before JPA initialization.

## 10. Redis configuration

- Image: `redis:7.4.11-alpine3.21`.
- Host port: loopback-only `6379`.
- Compose service name: `redis`.
- Persistent volume: `ai-order-delivery-agent_redis_data`.
- Append-only file enabled.
- `appendfsync everysec`.
- Snapshot rule: save after 60 seconds when at least one key changed.
- Health check: `redis-cli ping`.
- Intentionally unauthenticated for loopback-only local development.
- Not suitable as written for production.
- The backend is not connected to Redis yet.

Redis must remain a cache/temporary-state service, not the authoritative business store.

## 11. Docker/OrbStack setup and commands

Prerequisite: OrbStack or Docker Desktop must be running and `docker compose version` must succeed.

From the repository root:

```sh
cp .env.example .env
```

Edit the ignored `.env` locally. Use local-only credentials and never paste or commit it.

Start services:

```sh
docker compose up -d
```

Check status:

```sh
docker compose ps
```

View logs:

```sh
docker compose logs postgres redis
docker compose logs -f postgres redis
```

Verify PostgreSQL without putting a password on the command line:

```sh
docker compose exec postgres sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT 1;"'
```

Verify Redis:

```sh
docker compose exec redis redis-cli ping
```

Stop containers while retaining volumes:

```sh
docker compose down
```

Permanently reset local data and volumes only with explicit user approval:

```sh
docker compose down --volumes
```

At handoff capture time, OrbStack was listening on `127.0.0.1:5432` and `127.0.0.1:6379`. The containers were previously verified healthy. A Docker API query from the final documentation sandbox was denied at the OrbStack socket, so a new session should run `docker compose ps` directly before relying on current container state.

## 12. Spring Boot configuration

Profiles:

- `application-local.yml`: local development; graceful shutdown timeout defaults to 20 seconds.
- `application-dev.yml`: DEV; default graceful timeout 30 seconds.
- `application-qa.yml`: QA; default graceful timeout 30 seconds.
- `application-prod.yml`: PROD; default graceful timeout 30 seconds.

Each profile supports:

- `SPRING_APPLICATION_NAME`, default `backend`.
- `SERVER_PORT`, default `8080`.
- `LOG_LEVEL_ROOT`, default `INFO`.
- `SHUTDOWN_TIMEOUT`.
- `DATABASE_URL`.
- `DATABASE_USERNAME`.
- `DATABASE_PASSWORD`.

All profiles use the same JPA safety settings and external database configuration. Environment isolation is represented by profiles only; separate DEV/QA/PROD infrastructure does not exist.

## 13. Registration and authentication implementation

Registration, login, access-token issuance, refresh-token rotation/reuse detection, logout, bearer-token request authentication, authentication rate limiting, and reusable tenant/user ownership guards are implemented. OAuth, account recovery, email verification, and business-domain authorization outside integration configuration are not implemented.

Registration flow:

1. Spring MVC receives JSON at `/api/v1/auth/register`.
2. Bean Validation checks required fields, email syntax/length, and password policy.
3. The service strips and lowercases email with `Locale.ROOT`.
4. The repository performs a case-insensitive existing-email check.
5. Spring Security Crypto encodes the password using Argon2id.
6. A new random UUID tenant ID is assigned for this self-registration model.
7. JPA inserts the user into the existing `users` table and flushes the insert.
8. The global normalized-email database index handles duplicate races.
9. The response includes only user ID, normalized email, and creation time.

Password policy:

- Required and non-blank.
- Minimum 12 characters.
- Maximum 128 characters.
- At least one lowercase letter.
- At least one uppercase letter.
- At least one digit.
- At least one non-alphanumeric, non-whitespace special character.

Argon2id implementation:

- Spring Security `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`.
- 16-byte salt.
- 32-byte hash.
- Parallelism 1.
- Memory cost `1 << 14` KiB (16 MiB).
- Two iterations.
- BouncyCastle runtime dependency.

No controller, service, entity, or exception handler logs request passwords or hashes. The response models contain no password fields.

## 14. Current API

### `POST /api/v1/auth/register`

Request:

```json
{
  "email": "user@example.com",
  "password": "<STRONG_PASSWORD>"
}
```

Successful response: `201 Created`

```json
{
  "id": 1,
  "email": "user@example.com",
  "createdAt": "2026-10-02T06:00:00Z"
}
```

Validation failure: `400 Bad Request`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Registration request is invalid",
  "fieldErrors": {
    "email": "Email must be valid"
  },
  "timestamp": "2026-10-02T06:00:00Z"
}
```

Missing/malformed JSON: `400 Bad Request`

```json
{
  "code": "INVALID_REQUEST",
  "message": "A valid JSON request body is required",
  "fieldErrors": {},
  "timestamp": "2026-10-02T06:00:00Z"
}
```

Duplicate email: `409 Conflict`

```json
{
  "code": "EMAIL_ALREADY_REGISTERED",
  "message": "An account with this email is already registered",
  "fieldErrors": {},
  "timestamp": "2026-10-02T06:00:00Z"
}
```

### `POST /api/v1/auth/login`

Accepts email/password credentials. Spring Security verifies the normalized email and existing Argon2id hash. Successful authentication returns a 15-minute HS256 JWT access token and a 30-day opaque refresh token. Unknown email, incorrect password, and inactive-user failures all return the same `401 AUTHENTICATION_FAILED` response.

### `POST /api/v1/auth/refresh`

Accepts the current opaque refresh token, revokes it, and returns a new access/refresh pair in the same session family. Unknown, expired, revoked, and reused tokens return the same generic authentication failure. Reuse revokes the full session family.

### `POST /api/v1/auth/logout`

Accepts a refresh token, revokes the full session family, and returns `204 No Content`. Logout is idempotent and does not disclose token state.

Integration metadata endpoints are protected. No order/tracking endpoint exists; the new execution service is a backend-only boundary. Bearer-token request authentication and the reusable authorization boundary are described in `docs/AUTHORIZATION.md`.

## 15. Security decisions already implemented

- `.env`, `.env.*`, keys, PEM files, PKCS files, and `secrets/` are ignored; `.env.example` is explicitly allowed.
- Passwords are one-way encoded with Argon2id before persistence.
- Access tokens are signed with an externally supplied, Base64-encoded HMAC key of at least 256 bits and expire after 15 minutes.
- Refresh tokens contain 256 random bits, expire after 30 days, rotate on use, and are stored only as SHA-256 hashes.
- Refresh-token rows are locked during rotation; reuse revokes the complete session family.
- Logout revokes the complete refresh-token session without disclosing whether the submitted token exists.
- Plaintext passwords and hashes are absent from API responses.
- The database rejects non-Argon2 credential strings when a password hash is supplied.
- Registration duplicates are checked in application code and protected by a database unique index.
- Validation and duplicate errors use safe custom responses rather than raw database exceptions.
- Chrome extension settings reject credentials in URLs, query parameters, fragments, and non-HTTPS remote URLs.
- Extension permission set is only `storage`.
- Extension stores only the backend base URL, not credentials.
- PostgreSQL and Redis are bound to localhost.
- Hibernate schema changes are disabled.
- Flyway clean is disabled.
- Integration settings are explicitly non-secret.
- Tool execution and audit metadata are documented as redacted-only.
- External order/shipment cache columns require explicit expiration metadata.
- Validated or generated request IDs are returned in `X-Request-ID`, included in API errors where practical, and placed in MDC for structured JSON logs.
- Authenticated user, tenant, and session IDs are added to structured request logs only from the trusted Spring Security principal.
- Central redaction protects sensitive field names before structured security and audit metadata is emitted.
- Security and integration audit writes share their owning transaction and fail closed if persistence fails.
- Only minimal liveness and readiness probes are public; readiness includes PostgreSQL and Redis.

Not yet implemented: business-domain authorization outside integration configuration, secure-header policy review, advanced brute-force/anomaly detection, RLS, encrypted integration credentials, an external/immutable audit sink and retention policy, secret scanning CI, dependency/container scanning, TLS termination, distributed tracing/metrics export, and cloud IAM. CSRF is disabled for the stateless JSON token API because it does not use ambient cookie authentication.

## 16. Tenant-isolation design

The current registration model creates a unique `tenant_id` UUID for each registered user. The schema is capable of representing multiple users per tenant later, but no tenant-management API exists.

Every tenant-owned domain table has a non-null tenant ID; `audit_events` is the sole exception for unauthenticated events with no knowable tenant. Relationships that cross tenant-owned tables use composite ownership keys, for example:

```text
(tenant_id, user_id) -> users(tenant_id, id)
(tenant_id, user_id, integration_id) -> integrations(...)
(tenant_id, user_id, conversation_id) -> conversations(...)
(tenant_id, user_id, order_id) -> orders(...)
(tenant_id, user_id, shipment_id) -> shipments(...)
```

This prevents a child record from referencing a parent belonging to another tenant/user. It does not by itself authorize application queries. Future authenticated operations must derive identity from Spring Security, never accept authoritative tenant/user IDs from clients, and always scope repository operations.

PostgreSQL RLS was explicitly deferred. Do not add it until connection roles, session context, policy behavior, migration ownership, and test strategy are approved.

## 17. Frontend status

The `web/` project is a separate Next.js application using the App Router. It contains a polished responsive landing page explaining secure, grounded order assistance and the intended controlled data flow.

Implemented:

- Landing page.
- Responsive CSS and accessibility-oriented structure, including a skip link.
- Metadata and Geist fonts.
- Production build artifacts currently exist in `web/.next/`, indicating a prior build.

Not implemented:

- Registration screen or connection to the registration endpoint.
- Login.
- Dashboard.
- Integration configuration.
- Agent chat.
- Conversation history.
- Order/tracking views.
- Account/security settings.
- Usage/cost views.
- Frontend automated tests.

The landing page text still says registration is coming in a later phase; backend registration now exists, so changing that content should be a separate approved frontend task.

## 18. Chrome extension status

The extension is a React/TypeScript/Vite Manifest V3 scaffold.

Implemented:

- Popup page.
- Options page.
- Module service worker.
- Generic typed API client with optional bearer-token callback.
- `chrome.storage.sync` settings persistence.
- Backend base URL normalization and validation.
- HTTPS required for remote URLs; HTTP allowed only for `localhost` and `127.0.0.1`.
- Credentials, query parameters, and fragments are rejected in the configured URL.
- Only the `storage` permission is declared.
- Built output exists in `extension/dist/`, including `manifest.json`, popup/options HTML, assets, and `service-worker.js`.

Not implemented:

- Registration/login UI.
- Session/token storage.
- Agent chat.
- Order or tracking UI.
- Integration credential configuration.
- Backend connectivity request from the UI.
- Automated extension tests.

The source and build output contain no embedded credentials. Because no durable Chrome test report exists in the repository, the next account should treat unpacked-load, popup, options, and service-worker console status as needing a fresh manual check when extension work resumes.

## 19. Evaluation infrastructure status

`evaluation/` now contains README.md documenting AiEvaluationBaselineTests, an executable 12-case
catalog using the existing Gradle/JUnit runner and production orchestrator/renderer with typed mocks.
Exact pass/fail reasons, measured case latency, declared test fixture identity and null unavailable
usage/cost metadata are captured in ignored JUnit reports. This is not real-model evaluation or a
production authorization proof from mocks; full integration/security tests provide that separate evidence.
Real-model semantics/provider truth/freshness/privacy/cost require manual review. No numeric accuracy score.

The specification requires future evaluation of correctness, groundedness, tool selection/arguments, tool success, context, safety, prompt-injection resistance, unauthorized access resistance, latency, token usage, and cost.

“FTA evaluation” remains undefined. Do not invent its meaning. Ask the project owner before implementing domain-specific FTA evaluation.

## 20. Environment variables

Only variable names and placeholders are documented here. Never copy the contents of the ignored `.env` into chat or source control.

```dotenv
SPRING_PROFILES_ACTIVE=local
SPRING_APPLICATION_NAME=backend
SERVER_PORT=8080
LOG_LEVEL_ROOT=INFO
SHUTDOWN_TIMEOUT=20s

POSTGRES_DB=<LOCAL_DATABASE>
POSTGRES_USER=<LOCAL_DATABASE_USER>
POSTGRES_PASSWORD=<REDACTED>

DATABASE_URL=jdbc:postgresql://localhost:5432/<LOCAL_DATABASE>
DATABASE_USERNAME=<LOCAL_DATABASE_USER>
DATABASE_PASSWORD=<REDACTED>
```

For a backend running inside the Compose network, use host `postgres` rather than `localhost` in `DATABASE_URL`.

No AWS, Bedrock, provider API, OAuth, token-signing, Datadog, or OpenTelemetry variables are currently defined.

## 21. Development commands

### Backend

From `backend/`, load local values without printing them and map Compose variables if necessary:

```sh
set -a
source ../.env
set +a
export SPRING_PROFILES_ACTIVE=local
export DATABASE_URL="jdbc:postgresql://127.0.0.1:5432/$POSTGRES_DB"
export DATABASE_USERNAME="$POSTGRES_USER"
export DATABASE_PASSWORD="$POSTGRES_PASSWORD"
./gradlew bootRun
```

Default application port is `8080` unless `SERVER_PORT` is set.

### Web

```sh
cd web
npm install
npm run dev
npm run lint
npm run build
npm run start
```

Default Next.js development port is `3000`.

### Extension

```sh
cd extension
npm install
npm run lint
npm run build
```

Load `extension/dist/` using Chrome’s “Load unpacked” flow when manual verification is explicitly requested.

## 22. Tests and latest results

Backend integration tests require live PostgreSQL credentials. Tests are guarded by `DATABASE_PASSWORD`; if it is absent, the database-dependent tests are skipped rather than falsely failing.

Run from `backend/`:

```sh
set -a
source ../.env
set +a
export SPRING_PROFILES_ACTIVE=local
export DATABASE_URL="jdbc:postgresql://127.0.0.1:5432/$POSTGRES_DB"
export DATABASE_USERNAME="$POSTGRES_USER"
export DATABASE_PASSWORD="$POSTGRES_PASSWORD"
./gradlew test --rerun-tasks --console=plain
```

Latest verified result on 2026-10-04 after processes 21–25:

- `BackendApplicationTests`: 1 test, 0 failures.
- `PostgreSqlConnectionTests`: 3 tests, 0 failures.
- `UserRegistrationIntegrationTests`: 7 tests, 0 failures.
- `UserAuthenticationIntegrationTests`: 12 tests, 0 failures.
- `AuthorizationIntegrationTests`: 16 tests, 0 failures.
- `TenantDataIsolationIntegrationTests`: 12 tests, 0 failures.
- `ExternalOrderProviderContractTests`: 7 tests, 0 failures.
- `ExternalBaseUrlValidatorTests`: 13 tests, 0 failures.
- `ExternalIntegrationIntegrationTests`: 10 tests, 0 failures.
- `CorsIntegrationTests`: 3 tests, 0 failures.
- `AuthenticationRateLimitingIntegrationTests`: 11 tests, 0 failures.
- `AuthenticationRateLimitRedisFailureIntegrationTests`: 2 tests, 0 failures.
- `AuditEventServiceTests`: 1 test, 0 failures.
- `HealthEndpointIntegrationTests`: 4 tests, 0 failures.
- `HealthEndpointRedisFailureIntegrationTests`: 2 tests, 0 failures.
- `RequestObservabilityIntegrationTests`: 7 tests, 0 failures.
- `SecurityAuditIntegrationTests`: 7 tests, 0 failures.
- `SensitiveDataRedactorTests`: 2 tests, 0 failures.
- `SecureHttpTransportTests`: 18 tests, 0 failures.
- `ExternalOrderExecutionIntegrationTests`: 60 tests, 0 failures (including tool/domain/API/conversation and authenticated scripted-agent boundaries).
- `AgentOrchestrationTests`: 30 tests, 0 failures.
- `ConversationApiIntegrationTests`: 18 tests, 0 failures.
- `AiExecutionGuardTests`: 2 tests, 0 failures.
- `CredentialMaterialTests`: 2 tests, 0 failures.
- `CredentialLifecycleContractTests`: 6 tests, 0 failures.
- `ProviderExecutionBudgetTests`: 7 tests, 0 failures.
- Total: 263 tests, 263 passed, 0 failed/errors, 0 skipped.
- Flyway validated seven migrations.
- PostgreSQL JDBC driver, database connection, and `SELECT 1` were verified.
- Registration cases verified: success, invalid email, missing email, missing password, weak password, case-insensitive duplicate, and stored Argon2id hash rather than plaintext.
- Authentication cases verified: login success, unknown email, incorrect password, invalid/missing input, inactive user, access-token issuance, hashed refresh-token issuance, rotation, expiration, revocation, reuse detection, and logout.
- Authorization cases verified: valid bearer authentication, missing/malformed/expired/wrong-signature/wrong-issuer/missing-claim tokens, trusted context, server-side tenant derivation, client override resistance, user/session mismatch rejection, cross-tenant denial, and revoked-session rejection.
- Isolation cases verified: own-resource access, cross-tenant and same-tenant/cross-user denial, integration/order/conversation/message/credential boundaries, modification/delete authorization, and client identity override attempts.
- Provider-contract cases verified: exact operation surface, trusted identity context, typed/validated requests, immutable results, safe failure hierarchy, and absence of credential-bearing fields.
- Observability cases verified: generated/accepted/rejected request IDs, response and error correlation, MDC cleanup, trusted authenticated log context, centralized redaction, liveness/readiness behavior, database/Redis readiness, security audit identity/outcomes, refresh reuse, logout, rate limiting, and audit failure propagation.

No frontend or extension automated tests exist.

## 23. Known warnings

### Gradle/macOS FSEvents

During `compileJava`, Gradle may print:

```text
Caught exception: Could not start the FSEvents stream: <project path>
```

This was diagnosed without changing the project:

- It is a Gradle/macOS file-watching optimization warning, not a Spring Boot runtime error.
- Builds and all tests complete successfully.
- `bootRun` and application functionality are unaffected.
- Production runtime is unaffected because FSEvents is not part of the packaged application.
- The only potential impact is Gradle `--continuous` automatic task re-execution if file watching is unavailable. Spring Boot DevTools/automatic restart is not configured.
- A separate diagnostic reported Gradle file-system watching as active.
- Do not modify the project solely to suppress this warning.

### JVM class-data sharing

Tests may print an OpenJDK warning that class sharing is limited because the bootstrap classpath was appended. It has not caused test or application failure.

## 24. Known unresolved issues and transient local state

- Registration (`6a0721b`), authentication (`0a73b4a`), tenant isolation (`8b44c96`), and provider abstraction (`388088b`) checkpoints are pushed on `main`. Later integration, frontend/authentication, rate-limiting, and observability work remains uncommitted until the user explicitly requests a checkpoint.
- No CI, branch protection, or pull-request workflow is configured.
- `IMPLEMENTATION_STATUS.md` required by the master specification does not exist.
- Most planned documentation files do not exist.
- At handoff capture time, Java processes were listening on ports `8080` and `18080`. Port `8080` was an older verification run; port `18080` was the registration-capable verification run. The execution sandbox could not signal those child Java processes. A new local session should inspect with `lsof -nP -iTCP:8080 -sTCP:LISTEN` and `lsof -nP -iTCP:18080 -sTCP:LISTEN`, then ask the user before terminating processes if intent is unclear.
- Chrome unpacked-extension status is not durably recorded; rebuild/reload verification should be repeated when extension development resumes.
- PostgreSQL RLS is not implemented.
- The tenant model currently creates one tenant UUID per self-registered user; organization invitations/membership are not designed.
- Password hashes are nullable at database level solely to migrate safely over any pre-existing users. Registration-created JPA entities always provide a hash. Do not tighten this in an applied migration; use a new migration after deciding how non-password identities are handled.
- The global normalized-email unique index reflects the current self-registration endpoint, which does not accept tenant context. Changing to tenant-scoped duplicate rules requires explicit product/security approval and a new migration.
- Registration’s database-integrity catch is intentionally converted to a safe duplicate response; broader error taxonomy can be refined later without exposing raw database messages.
- The security-observability foundation is local only: no distributed tracing, metrics exporter, external log/audit backend, immutable audit archive, or retention/alerting policy exists.

## 25. Current ports and services

| Port | Expected service | Scope/state |
|---|---|---|
| `3000` | Next.js development server | Only when manually started. |
| `5432` | PostgreSQL | Loopback through OrbStack/Compose; listening at handoff capture. |
| `6379` | Redis | Loopback through OrbStack/Compose; listening at handoff capture. |
| `8080` | Default Spring Boot backend | A prior Java verification process was listening at handoff capture. |
| `18080` | Temporary registration verification backend | A Java verification process was listening at handoff capture. Not a configured permanent port. |

No AWS or remote deployed services exist.

## 26. Git repository and workflow state

- GitHub account: `gopal089`.
- Repository: public `ai-order-delivery-agent`.
- Remote: `https://github.com/gopal089/ai-order-delivery-agent.git`.
- Current local branch: `main`.
- Default intended branch: `main`.
- The registration baseline root commit is `6a0721b5fffa5e030db7f83706e5cc58a327b877`.
- Step 2 authentication is committed as `0a73b4a980ba57249551b9bf70abf9cedce805f6`.
- Tenant isolation is committed as `8b44c96`; the provider abstraction is committed as `388088b`.
- Local `main` tracks `origin/main`; later integration, frontend/authentication, rate-limiting, and observability work is uncommitted.

Before any future checkpoint, review generated files, confirm `.env` is ignored, run a secret scan, and exclude build/cache artifacts. Do not push, create branches, or alter GitHub settings without the user’s instruction.

Intended future workflow from the specification:

- `main`, `develop`, `feature/*`, `fix/*`, `release/*`.
- Protect `main`.
- Require pull requests and passing CI for production-bound changes.
- Production deployment requires explicit approval.

## 27. Important files

| File | Purpose |
|---|---|
| `.env.example` | Safe variable-name/template documentation; placeholders only. |
| `.gitignore` | Excludes local environment files and secret material. |
| `compose.yaml` | Local PostgreSQL/Redis services, health checks, volumes, network, ports. |
| `README.md` | Minimal root project description; currently stale. |
| `docs/LOCAL-DEVELOPMENT.md` | Local Docker setup and registration API usage. |
| `docs/PROJECT_HANDOFF.md` | This continuation document. |
| `backend/build.gradle` | Java/Spring Boot build and runtime/test dependencies. |
| `backend/settings.gradle` | Gradle project name and Foojay toolchain resolver. |
| `backend/src/main/resources/application.properties` | Common application and Flyway settings. |
| `backend/src/main/resources/application-*.yml` | Environment-specific external DataSource and runtime settings. |
| `backend/src/main/resources/db/migration/V1…V7.sql` | Immutable applied schema history. |
| `docs/AUTHENTICATION.md` | Step 2 access-token, refresh-token, rotation, reuse, and logout decisions. |
| `docs/AUTHENTICATION-RATE-LIMITING.md` | Redis key, TTL, limit, response, and failure-mode decisions for login and refresh abuse protection. |
| `docs/AUTHORIZATION.md` | Step 3 bearer validation, server-resolved identity, and reusable ownership boundary. |
| `docs/TENANT-ISOLATION.md` | Resource ownership lookup, isolation enforcement, verified cases, and deferred domain behavior. |
| `docs/INTEGRATION-CONFIGURATION.md` | Secure integration metadata, URL validation, credential abstraction, and lifecycle audit decisions. |
| `docs/SECURITY-OBSERVABILITY.md` | Request correlation, structured logging/redaction, audit semantics, and health probe behavior. |
| `docs/PROVIDER-EXECUTION-VERIFICATION.md` | Verified HTTP/execution controls, fixture design, limits, and credential/provider blockers. |
| `BackendApplication.java` | Spring Boot entry point. |
| `PasswordConfiguration.java` | Argon2id encoder and UTC clock beans. |
| `RegisterRequest.java` | Registration request and validation rules. |
| `RegisterResponse.java` | Safe registration response. |
| `UserRegistrationController.java` | `POST /api/v1/auth/register`. |
| `UserRegistrationService.java` | Normalization, duplicate check, hashing, tenant creation, persistence. |
| `UserAuthenticationController.java` | Login, refresh, and logout HTTP endpoints. |
| `UserAuthenticationService.java` | Credential authentication and transactional session lifecycle. |
| `TokenService.java` | JWT issuance plus opaque refresh-token generation and hashing. |
| `RefreshToken.java` / `RefreshTokenRepository.java` | Refresh-token persistence, row locking, and session revocation. |
| `SecurityConfiguration.java` | Stateless Spring Security credential and bearer-token configuration plus JWT cryptography/validation. |
| `AccessTokenAuthenticationConverter.java` | Resolves token user/session claims against authoritative user and active-session records. |
| `AuthenticatedUserContextProvider.java` | Supplies trusted user/tenant/session identity from Spring Security context. |
| `TenantAuthorization.java` | Reusable tenant and owner enforcement guards for future services. |
| `TenantDataAuthorizationService.java` | Reusable resource-specific isolation boundary for current tenant/user-owned tables. |
| `TenantOwnedResourceRepository.java` | Ownership-metadata-only queries for isolation checks. |
| `UserAccount.java` | JPA mapping to the existing `users` table. |
| `UserAccountRepository.java` | User persistence and case-insensitive email lookup. |
| `RegistrationExceptionHandler.java` | Safe 400/409 registration error responses. |
| `PostgreSqlConnectionTests.java` | Live connection, Flyway, and tenant-schema assertions. |
| `UserRegistrationIntegrationTests.java` | Seven required registration/security integration tests. |
| `web/src/app/page.tsx` | Current landing page. |
| `extension/public/manifest.json` | Manifest V3 definition with only `storage` permission. |
| `extension/src/background/service-worker.ts` | Settings read/write message handler. |
| `extension/src/shared/settings.ts` | Secure base-URL normalization and validation. |
| `extension/src/api/client.ts` | Generic backend API client scaffold. |
| `extension/src/popup/Popup.tsx` | Popup status/setup UI. |
| `extension/src/options/Options.tsx` | Backend base-URL options UI. |

## 28. Problems encountered and resolutions

1. **Redis port conflict:** An unrelated `rate-limiter-redis` container occupied port 6379. It was stopped with user permission so this project’s Redis could bind to loopback. Do not delete unrelated resources.
2. **Initial DataSource failure:** Tests initially reported `Failed to determine a suitable driver class` because JPA was enabled while the default test context lacked the local profile/URL, and the first connection test used the wrong Java package. Tests were corrected to use the actual package, local profile, and credential gating. The PostgreSQL driver and connection are now verified.
3. **Flyway engine without Boot auto-configuration:** Adding `flyway-core` alone did not create Flyway startup beans in Spring Boot 4. It was replaced with `spring-boot-starter-flyway` plus `flyway-database-postgresql`.
4. **Already-applied migration refinement:** The initial schema’s active-token partial index did not cover all refresh-token ownership checks. Rather than modifying V2 after application, V3 added the complete ownership index.
5. **BouncyCastle version resolution:** Spring Boot’s catalog did not supply a version for `bcprov-jdk18on`; version 1.84 was explicitly pinned.
6. **Ambiguous service constructors:** A production and test-oriented constructor caused Spring to seek a default constructor. This was corrected by using one injected constructor and a UTC `Clock` bean.
7. **macOS Gradle FSEvents warning:** Diagnosed as non-fatal build-watcher behavior. Tests, `bootRun`, runtime, application functionality, and PostgreSQL all work. No suppression change was made.
8. **Temporary backend shutdown:** The sandbox could start child Java processes but could not later signal them from another sandbox process. Two local listeners may remain; inspect before starting another backend.
9. **Docker socket during final handoff inspection:** The documentation sandbox could see OrbStack’s listening ports but was denied Docker API access despite a permission request. The services had been successfully health-tested earlier; re-run `docker compose ps` in the new session.

## 29. Explicitly not implemented

- OAuth/social authentication.
- Email verification, password reset, or account recovery.
- Domain mutation/ingestion endpoints; protected order/shipment read endpoints exist.
- PostgreSQL RLS.
- Tenant membership/invitation/administration.
- Integration credential storage or encryption.
- AWS Secrets Manager/KMS.
- Real external provider adapters or API calls.
- Conversation lifecycle/delete/search APIs, separately durable tool-attempt ledger and domain ingestion/mutation services. Owned chat creation/send/history, order/shipment reads and transactional tool-summary audit exist.
- Production AI model adapter, LangChain4j, Bedrock, semantic grounding verification and persistent semantic memory. Chat send invokes bounded scripted-model-tested orchestration but fails closed without a configured model.
- API Gateway.
- AWS infrastructure/deployment.
- CI/CD and GitHub Actions.
- OpenAPI documentation.
- Distributed tracing, metrics export, external log/audit backends, alerting, audit retention, and cost instrumentation.
- Security/dependency/container/secret scanning pipeline.
- Advanced/real-model evaluation datasets or runner; the deterministic JUnit baseline exists.
- Frontend registration/login/dashboard/chat functionality.
- Extension authentication/chat/order/tracking functionality.
- Production configuration or credentials.

## 30. Original roadmap and governing specification

The master specification is organized into the following 39 sections. These remain requirements unless the project owner approves a change:

1. **Primary objective** — multi-tenant AI order/delivery web platform and extension.
2. **Non-negotiable engineering policies** — no fabricated APIs/data, no secrets, authorization in backend, incremental work.
3. **Technology stack** — Java/Spring, React/TypeScript, PostgreSQL, Redis, Docker, AWS, Bedrock, LangChain4j.
4. **High-level architecture** — clients through API Gateway/backend/security/agent/tools/integration layer.
5. **Multi-tenant model** — ownership on every important entity and identity derived from security context.
6. **Authentication** — email/password, hashing, login/logout, tokens where appropriate, rate limiting, brute-force defense.
7. **Customer API credential model** — secure credential references and no model/browser exposure.
8. **External API abstraction** — provider interface/adapters only with real documentation.
9. **AI agent role** — understand intent and summarize tool data, never authorize or invent facts.
10. **Initial agent tools** — typed order, detail, tracking, status, and location tools.
11. **Real-time tracking definition** — only provider-reported status/location/timestamp.
12. **Memory** — short-term and persistent context with privacy/retention; source data wins.
13. **Prompt architecture** — versioned prompts, model/tool/agent configuration.
14. **Agent safety rules** — approved tools, no fabrication, credential disclosure, arbitrary code/URLs, or policy bypass.
15. **Frontend** — landing, registration, login, dashboard, integrations, chat, history, order/tracking, settings, usage.
16. **Chrome extension** — secure API-based login/chat/integration/order/tracking UI.
17. **API design** — versioned REST routes behind API Gateway; no authorization bypass; OpenAPI.
18. **Database** — PostgreSQL, Flyway, normalized tenant-aware schema, deliberate cache TTL/source/invalidation.
19. **Evaluation system** — AI, tool, regression, prompt injection, performance, cost, and undefined FTA placeholder.
20. **Automated test cases** — success/failure/provider/security/cross-tenant/prompt-injection/context/tool/hallucination cases.
21. **AI evaluation** — correctness, groundedness, tools, context, safety, latency, tokens, cost.
22. **CI/CD** — build through security/evaluation/container/deployment gates.
23. **Git workflow** — branch strategy, protected main, PRs, CI, no secrets.
24. **Deployment** — ECS/Fargate with managed AWS services and reproducible IaC.
25. **Environments** — isolated DEV, QA, and PROD resources/configuration/secrets/data.
26. **Observability** — OpenTelemetry, Datadog, CloudWatch across application, AI, infrastructure, security.
27. **Cost management** — track Bedrock/AWS/Datadog usage and avoid oversized development infrastructure.
28. **Security** — authentication, authorization, isolation, validation, headers, CORS/CSRF, encryption, IAM, scanning, auditing.
29. **Prompt-injection defense** — treat all user/provider text as untrusted and non-privileged.
30. **Documentation** — architecture, security, threat model, API, development, deployment, evaluation, observability, contribution, decisions, operations.
31. **Development process** — 20 incremental phases listed below.
32. **Code quality rule** — inspect, understand, make the smallest change, test, report.
33. **Failure policy** — do not guess missing dependencies/specifications; ask and build safe abstractions.
34. **GitHub connector** — GitHub is authoritative once content is committed/pushed and access is verified.
35. **AWS credential policy** — never request/print access keys; prefer local auth and GitHub OIDC.
36. **Definition of done** — builds/tests/security/evaluation/deployment/observability/docs/rollback all verified.
37. **How to work** — implement, test, inspect, fix, document, commit, then advance; maintain implementation status.
38. **First action** — inspect repo, scaffold architecture/apps/docs/Docker/migrations/CI/status before deep features.
39. **Final principle** — backend is security/control, AI is reasoning/interface, tools are controlled actions, provider data is authoritative.

Original 20 implementation phases:

1. Repository and project scaffolding.
2. Authentication.
3. Database and migrations.
4. Integration/credential architecture.
5. External API abstraction and mocks.
6. Agent + LangChain4j + Bedrock.
7. Agent tools.
8. Memory/context.
9. Frontend.
10. Chrome extension.
11. API Gateway.
12. Security.
13. Evaluation.
14. CI/CD.
15. AWS infrastructure.
16. DEV deployment.
17. QA.
18. Production deployment.
19. Observability.
20. Optimization.

The actual work has intentionally crossed the original broad phase ordering in small approved steps: foundational database/schema work was completed before authentication was expanded beyond registration. Continue honoring the user’s one-step-at-a-time approval model rather than attempting an entire phase at once.

## 31. Decisions that must not change without approval

- Do not edit V1–V7 Flyway files.
- Do not create a competing users table.
- Do not store plaintext passwords, raw refresh tokens, or provider credentials.
- Keep Argon2id for password encoding unless a security migration is explicitly designed and approved.
- Preserve the registration endpoint and safe response/error contract unless an API version/change is approved.
- Preserve case-insensitive race-safe duplicate-email handling.
- Keep Hibernate `ddl-auto` set to `none` and Flyway as the sole schema manager.
- Keep tenant/user composite ownership constraints.
- Do not enable RLS yet.
- Do not redesign bearer-token authorization, authentication rate limiting, observability, or integration configuration as a side effect of another task; do not add OAuth, AI, AWS, or API Gateway without approval.
- Do not expose integration credentials to the LLM, browser, extension, logs, or errors.
- Do not implement external provider endpoints without documentation.
- Keep external APIs as the source of truth and preserve source timestamps.
- Do not add Chrome permissions or change extension architecture without approval.
- Keep Docker services local-only and do not turn local credentials into production configuration.
- Do not create production infrastructure or assume AWS access.
- Do not suppress the harmless FSEvents warning by changing the project.
- Do not delete or terminate unrelated containers/processes without resolving exact ownership and scope.
- Do not assume files are committed or pushed; verify Git state.

## 32. Exact next implementation step

Owned chat create/send/history APIs, domain reads, bounded controlled-tool orchestration and transactional message/tool-summary audit are verified with fixtures. No production credential store, real provider/model, AWS SDK or ingestion exists. Before a Bedrock adapter, approve model/inference profile, region/data residency, authentication/IAM/model access and API/configuration, then verify SDK version/Java compatibility, timeout/error/tool schema mapping. Approve a policy for unsupported claims in arbitrary unverified prose; a test demonstrates the remaining risk. Existing credential-policy, real-provider-contract, internal-ID mapping, multi-shipment/source-age/cache decisions remain unresolved. Continue only with a separately approved task; see CHAT-AND-AI-SECURITY.md.

## CURRENT STATE

### Completed

- Local/public repository shell and monorepo directories.
- Spring Boot backend scaffold and environment profiles.
- Next.js landing page scaffold.
- Manifest V3 extension scaffold and built output.
- Docker Compose PostgreSQL and Redis infrastructure.
- PostgreSQL/JPA connection configuration.
- Flyway setup and seven applied migrations.
- Initial ten-table tenant-aware schema.
- User registration endpoint with Argon2id hashing and validation.
- Login, signed access-token issuance, rotating hashed refresh tokens, reuse detection, and logout/session revocation.
- Atomic Redis-backed login and refresh rate limits with bounded TTLs and generic 429/503 responses.
- Bearer JWT signature/issuer/time/claim validation and server-side active-session verification.
- Authenticated application context containing user, tenant, session, and authentication state.
- Reusable tenant/user ownership guards with 401/403 behavior.
- Provider-neutral `ExternalOrderProvider` contract with typed context, request/results, safe failures, and contract tests.
- Authenticated integration metadata CRUD, configuration-time URL validation, safe lifecycle audit events, and a credential-store abstraction without an implementation.
- Isolation guards for integrations, orders, conversations, messages, and future integration credentials.
- Validated request correlation, structured JSON logging with trusted security context, centralized sensitive-field redaction, transactional security/integration auditing, and minimal health probes.
- Local development documentation.
- Secure GET/HEAD HTTP transport and backend-only provider execution boundary, verified with synthetic local fixtures.
- Scoped credential rotation contract and deterministic test-only lifecycle/provider fixtures.
- Twenty-second execution deadline, five HTTP attempts, bounded worker pool and cancellation cleanup.
- Backend-bound tool interface using internal order ownership and explicitly untrusted data output, invoked by bounded model orchestration.
- Persisted order/shipment/event projections and scoped internal/provider resolution; shipment-specific current provider data with explicit unavailable/provenance semantics, no cache/memory fallback.
- Owned conversation creation/send/history APIs, atomic successful turns, safe tool-summary auditing and centralized system instructions.
- Three hundred four passing backend tests.
- Registration baseline commit `6a0721b` pushed to `origin/main`.
- Step 2 authentication commit `0a73b4a` pushed to `origin/main`.
- Diagnosis of the harmless Gradle/macOS FSEvents warning.

### Currently working

- PostgreSQL connectivity and Flyway validation.
- JPA/Hibernate startup with schema mutation disabled.
- `POST /api/v1/auth/register`.
- `POST /api/v1/auth/login`.
- `POST /api/v1/auth/refresh`.
- `POST /api/v1/auth/logout`.
- Required registration validation and safe 400/409 responses.
- Case-insensitive duplicate protection.
- Argon2id persistence without plaintext storage.
- Fifteen-minute signed access tokens and 30-day rolling refresh-token sessions.
- Refresh-token rotation, expiration, revocation, and session-wide reuse response.
- Login identity/IP and refresh session/IP abuse limits backed by the local Redis service.
- Bearer authentication for every non-auth route.
- Tenant identity derived only from the authenticated server-side user row.
- Immediate access-token rejection after session revocation.
- Cross-tenant and same-tenant/cross-user resource ownership enforcement at the service/repository boundary.
- Provider-neutral order/tracking contract without credentials, transport, vendor, cloud, or AI coupling.
- Tenant/user-scoped integration create/list/read/update/delete endpoints returning only safe metadata.
- HTTPS enforcement outside local mode and literal-address/URL-structure SSRF checks without DNS or network access.
- `X-Request-ID` correlation with safe generated fallback, API-error correlation, and MDC cleanup.
- Structured request completion logs with trusted user/tenant/session identifiers where authenticated.
- Redacted authentication and integration security events plus PostgreSQL audit rows for supported lifecycle events.
- Public minimal `/actuator/health/liveness` and `/actuator/health/readiness`; readiness depends on PostgreSQL and Redis.
- Local Redis/PostgreSQL ports and persistence configuration.
- Next.js landing page build artifacts.
- Extension build artifacts and URL settings code.

### Not implemented

- Domain ingestion/mutations/cache management and conversation lifecycle/delete/search services. Order/shipment reads and conversation create/send/history now exist.
- Actual credential storage or retrieval and credential configuration endpoints.
- Integration credentials and real provider adapters/calls.
- Production AI model/framework adapter, persistent semantic memory and real-model grounding/evaluation. Chat send invokes controlled orchestration but returns 503 without a model.
- Most functional frontend and extension screens.
- AWS, CI/CD, deployment, distributed tracing/metrics export, external observability/audit backends, cost controls, and production security hardening.

### What should be done next

1. Inspect and report the uncommitted secure-integration-configuration, frontend/authentication, rate-limiting, and observability changes.
2. Ask separately whether to create and push a checkpoint; do not assume permission.
3. Resolve the credential persistence/key-lifecycle blocker and obtain real provider documentation before implementing a store or adapter. The secure HTTP reader and guarded adapter selection already exist.
4. Preserve the authenticated context and reuse TenantDataAuthorizationService in every future protected resource service.

### Exact next Codex task/prompt

```text
Continue only with the next explicitly approved feature.

First inspect docs/PROJECT_HANDOFF.md, docs/AUTHENTICATION.md, docs/AUTHENTICATION-RATE-LIMITING.md, docs/AUTHORIZATION.md, docs/TENANT-ISOLATION.md, docs/INTEGRATION-CONFIGURATION.md, docs/SECURITY-OBSERVABILITY.md, the current Git status, V1–V7, and all current tests. Do not modify any already-applied migration.

For a protected business service, derive user, tenant, and session exclusively from AuthenticatedUserContextProvider and reuse TenantDataAuthorizationService. Never treat client-supplied tenant or user IDs as authority.

Do not implement OAuth, RLS, frontend or extension changes, integrations, AI, AWS, or unrelated functionality unless explicitly included in the approved task. Stop and report before starting another feature.
```

## CONTINUATION INSTRUCTIONS

For a new ChatGPT/Codex session:

1. Open the repository root and read this entire file before changing anything.
2. Read any repository-local agent instructions relevant to the files being changed, especially `web/AGENTS.md` for web work.
3. Run `git status --short`, `git branch --show-current`, `git log --oneline`, and `git remote -v`. Expect pushed checkpoints through the provider abstraction plus later uncommitted work until proven otherwise.
4. Never open, print, summarize, or transmit the ignored `.env`. Use `.env.example` only for variable names and placeholders.
5. Run `docker compose ps` and verify PostgreSQL/Redis health. Inspect ports 8080, 18080, and other temporary verification ports before starting another backend because test processes may remain.
6. Read the exact source, migration, configuration, and tests related to the requested task. Do not rely only on this summary.
7. Do not modify V1–V7. Any future schema change starts at V8 or later.
8. Load local environment variables without printing them, then run the full backend test suite before and after backend changes.
9. Keep the user informed and execute only the single approved implementation task. Stop and report before advancing.
10. Do not infer permission to commit, push, create a pull request, change GitHub settings, stop unrelated processes, deploy, configure AWS, or modify the extension.
11. After any completed task, update relevant documentation and this handoff/status information if requested.
12. Before reporting, run a secret scan over changed files and confirm that no actual passwords, keys, tokens, hashes, credentials, private URLs, or `.env` contents were included.

This project is an early, working local skeleton—not a complete or production-ready system. Preserve the security boundaries and incremental approval model above.
