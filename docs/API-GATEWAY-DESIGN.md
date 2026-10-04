# API Gateway preparation — design only

No AWS resources, deployments, regions, domains, certificates, roles, or integration targets are configured by this batch. `infrastructure/` previously contained placeholders, not an AWS deployment. The adjacent OpenAPI inventory covers 20 actual operations, not a complete DTO/client-generation contract or importable AWS deployment. Health probes are included for inventory; do not expose all Actuator endpoints through a catch-all proxy. `/actuator/health` itself requires authentication today.

## Routing and ownership

| Actual routes | Methods | Application authentication |
| --- | --- | --- |
| `/api/v1/auth/register`, `/login`, `/refresh`, `/logout` (same auth prefix) | POST | No Bearer required; refresh/logout require opaque refresh token in JSON |
| `/api/v1/integrations` | GET, POST | Bearer |
| `/api/v1/integrations/{integrationId}` | GET, PATCH, DELETE | Bearer + ownership |
| `/api/v1/orders`, `/api/v1/orders/{orderId}` | GET | Bearer + ownership |
| `/api/v1/shipments/{shipmentId}` and `/tracking-history`, `/current-status`, `/current-location` | GET | Bearer + ownership |
| `/api/v1/conversations` | POST | Bearer; exact empty JSON object |
| `/api/v1/conversations/{conversationId}/messages` | GET, POST | Bearer + ownership; POST exact integrationId/message |
| `/actuator/health/liveness`, `/actuator/health/readiness` | GET | Public minimal probes; preferably private load-balancer/orchestrator access |

No credential-management API or model/provider HTTP API is invented. Orders are application projections, not proof of a live external call. Conversations publish only controlled facts; absent model configuration yields 503.

## Control boundaries

Spring Boot MUST retain signature/issuer/expiration/claim validation, active-user/session checks, tenant/user ownership, refresh rotation/reuse detection/revocation, Redis identity/session-aware authentication rate limiting, input validation, SSRF defense, credential isolation, audit requirements and execution budgets. Pass Authorization intact to Spring; never replace it with client headers claiming identity. Existing HS256 tokens cannot be validated by the native HTTP API JWT authorizer, which currently supports RSA/JWKS. No signing key should be copied into Gateway configuration. [AWS JWT authorizer documentation](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-jwt-authorizer.html).

Gateway should enforce explicit route/method allowlisting, TLS, coarse stage/route throttles, bounded payloads and timeouts, safe correlation/access logs, and private backend routing. Preserve backend JSON/status codes and X-Request-ID; Gateway-originated 413/429/502/504 need their own safe client handling and correlation. No token, body, Cookie, Authorization or provider credential logging; disable body/data tracing. Gateway request ID and backend sanitized X-Request-ID are separate correlation fields. Client-supplied request IDs are not identity or authoritative provenance.

WAF owns edge abuse/bot/IP controls and applicable managed-rule filtering, not authentication or tenant authorization. REST API supports direct WAF integration; HTTP API does not. Choice between REST with native WAF and a separately reviewed front-door design for HTTP API remains DECISION REQUIRED. Do not silently add CloudFront or Lambda. [AWS feature comparison](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-vs-rest.html).

## CORS, network and configuration

Today Spring explicitly allows configured web origins, GET/POST/PATCH/DELETE/OPTIONS and Authorization/Content-Type/Accept/X-Request-ID, exposes X-Request-ID, and does not permit credentialed cookies. Keep Spring as the single CORS policy owner unless a reviewed Gateway-owned policy also handles preflight and error responses consistently; never wildcard production origins. Extension approved host permissions permit direct extension requests; production extension origin/hostname is not yet approved. Trusted proxy source-IP configuration requires review: never treat arbitrary forwarded client headers as the rate-limit source IP. Backend private access must prevent bypassing the edge; request sanitization still remains mandatory in Spring.

DECISION REQUIRED: approved container deployment target and private load balancer/VPC link or other supported private integration. This application is a persistent Spring HTTP server, not a Lambda handler. No public backend URL is assumed. Remove unintended stage prefixes before forwarding so Spring receives the exact existing paths. Public client-to-edge TLS is mandatory; backend TLS/certificate/SNI policy must be reviewed for the selected integration. DEV/QA/PROD need separate stage/config, origins, databases, Redis namespaces, signing secrets, audit retention and quotas. Existing Spring dev/qa/prod profiles do not create AWS environments.

## Budgets and limits

HTTP API has a non-increasable 30-second integration timeout and 10 MB payload ceiling. These are infrastructure ceilings, not this application's chosen budgets. [AWS HTTP API quotas](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-quotas.html).

Existing chat loop permits 30 seconds after synchronous preflight (provider tool 20 seconds, model attempt 5 seconds), plus locks/database/audit and network overhead. It cannot be guaranteed to finish inside a 30-second Gateway timeout. DECISION REQUIRED before deployment: reviewed shorter end-to-end synchronous budget with cancellation and headroom, or separately approved asynchronous delivery. This batch does not alter budgets or invent polling routes. Gateway retries of message POST must not create duplicate turns; no automatic replay after ambiguous timeout, since no idempotency contract exists.

Chat message is limited to 4000 characters; stored controlled assistant envelope is bounded to 8192 characters and history to 20 messages. A total JSON byte limit across all existing auth/integration/domain contracts is NOT VERIFIED and needs an approved policy, including edge enforcement for chunked requests. Do not confuse 4000 characters with 4000 bytes, or advertise the 10 MB Gateway ceiling as safe application input. Integration/schema validation and ownership checks remain in Spring even with edge validation.

OpenAPI response descriptions intentionally do not fabricate DTO schemas. Do not enable response/body transformation, caching authenticated content, or logging token responses. Production ingress/CORS/throttle/WAF/TLS/runtime behavior remains NOT VERIFIED; no deployment was performed.
