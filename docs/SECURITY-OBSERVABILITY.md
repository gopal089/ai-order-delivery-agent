# Security observability and audit foundation

## Request IDs and log context

`RequestIdFilter` assigns every HTTP request an identifier and returns it in `X-Request-ID`. A caller-supplied value is accepted only when it is 1–64 characters, begins with an alphanumeric character, and otherwise contains only letters, digits, `.`, `_`, `:`, or `-`. Invalid, oversized, or missing values are replaced with a random UUID.

The request ID is placed in SLF4J MDC for the duration of the request and removed in a `finally` block. After bearer authentication, the server-established `userId`, `tenantId`, and `sessionId` are added to MDC; client headers, parameters, and request bodies are never used as identity context. Request-completion logs contain the request ID, method, path without query parameters, response status, duration, and authenticated identity when available. Health probe requests are omitted from completion logs to avoid probe noise.

Console logging uses Spring Boot's Logstash JSON format by default. `LOG_STRUCTURED_FORMAT` can select another supported structured format. The JSON output includes timestamp, level, logger, message, MDC, and SLF4J key/value fields.

## Sensitive-data redaction

`SensitiveDataRedactor` and `SafeSecurityLogger` provide the centralized application-security logging path. Field names containing password, token, authorization, API key, secret, credential, or cookie markers are replaced with `[REDACTED]` before logging. Authentication and integration security events use fixed messages and bounded safe metadata; they never log request bodies, query parameters, authorization/cookie headers, emails, passwords, token values, credential references, or provider payloads.

## Audit events

`AuditEventService` is the single database writer for security and integration audit records. It writes only enum-controlled event types and outcomes plus redacted metadata. The current events are:

- `AUTH_LOGIN_SUCCESS`, `AUTH_LOGIN_FAILURE`;
- `AUTH_REFRESH_SUCCESS`, `AUTH_REFRESH_FAILURE`;
- `AUTH_REFRESH_REUSE_DETECTED`;
- `AUTH_LOGOUT`;
- `AUTH_RATE_LIMITED`;
- `INTEGRATION_CREATED`, `INTEGRATION_UPDATED`, `INTEGRATION_DELETED`.

Credential-reference lifecycle events are intentionally absent because no credential configuration operation exists yet.

Authenticated records derive tenant/user/session identity from server-side authentication or refresh-token records. Unknown login/refresh attempts use null tenant and actor values; they never fabricate an identity. V7 makes `audit_events.tenant_id` nullable for this case and adds a constraint requiring a tenant whenever `actor_user_id` is present. The request ID and coarse fixed endpoint/reason categories are stored in redacted JSON metadata. Passwords, hashes, access/refresh tokens, JWTs, credentials, headers, and request bodies are prohibited.

## Audit transaction behavior

Audit writes require an existing transaction and participate in the same transaction as the operation:

- successful login/refresh token persistence and integration create/update/delete roll back if their required audit write fails;
- logout revocation rolls back if its audit write fails;
- authentication-failure, reuse, and rate-limit transactions are configured to commit their audit records while preserving the intended 401/429 response;
- audit database/serialization failures are logged as `audit_event_write_failed` using safe metadata and are rethrown, never silently swallowed.

This avoids returning tokens or committing integration changes without their corresponding audit record and avoids independent nested audit transactions.

## Health endpoints

Only the following probes are public:

- `GET /actuator/health/liveness` — process/application liveness only; it does not depend on PostgreSQL or Redis.
- `GET /actuator/health/readiness` — includes Spring readiness state, PostgreSQL, and Redis.

The general `/actuator/health` surface still requires authentication. No other Actuator endpoint is exposed. Probe responses use `UP`/`DOWN` only: component details, URLs, hosts, credentials, stack traces, environment values, beans, mappings, loggers, and configuration properties are not exposed.

## Local verification

```bash
docker compose up -d
docker compose ps

cd backend
set -a
source ../.env
set +a
./gradlew test
./gradlew bootRun
```

In another terminal:

```bash
curl -i http://127.0.0.1:8080/actuator/health/liveness
curl -i http://127.0.0.1:8080/actuator/health/readiness
curl -i -H 'X-Request-ID: test-request-id' \
  http://127.0.0.1:8080/actuator/health/liveness
```

The last response must echo `X-Request-ID: test-request-id`.

## Current limitations

- MDC is request-thread scoped; future asynchronous work must propagate only an explicitly selected safe context.
- The direct servlet path is logged without query parameters. Future endpoints must continue avoiding body/header logging.
- Database-backed audit reliability currently shares the application database transaction; an external immutable audit sink is not implemented.
- Repeated blocked requests produce audit rows and must be covered by future retention/archival and infrastructure-level volumetric controls.
