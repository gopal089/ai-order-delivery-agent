# Processes 36–40: clients and containers

## Web chat

Next.js 16.3.8/React 19.2.8 uses the existing AuthProvider, ProtectedRoute and API client. `/chat` loads owned integration metadata, creates a conversation through POST `/api/v1/conversations` with `{}`, then sends only integrationId/message to the existing messages route. Tenant/user/session identity is never supplied in a chat body. No external provider URL is called by chat. Tokens remain in memory: reload signs out; no persistent browser token storage was added.

ControlledAnswer renders typed backend factual fields and source timestamps as escaped text. Neither raw model prose nor `answer.text` is rendered as evidence. Empty facts produce a neutral unavailable message; no ETA/freshness is invented. EXTERNALLY_SUPPORTED describes backend-supported provider fields, not independent truth. The no-retrieval Chennai claim and provider HTML escaping have actual render tests. Existing single-flight refresh/logout semantics are reused, with a tested compatibility guard preventing pending refresh from restoring a logged-out session or overwriting a newer login. Chat errors use fixed status messages for 401/403/429/503/generic failure, retaining X-Request-ID. No current real model/provider is claimed; real production configuration returns 503.

## Extension

Manifest V3 popup now logs in through the same backend token contract, lists owned integrations, creates conversations and sends chat messages. Access/refresh tokens stay in popup memory; closing/reloading the popup requires login again. A 401 triggers one single-flight rotation/retry, and failed refresh/repeated 401 clears authentication. Logout clears the local session even if backend revocation cannot be reached (such failure does not prove server revocation). Password input is cleared after submission. No credentials are stored in chrome.storage; the pre-existing storage setting holds only the backend origin.

No production backend hostname was discovered/approved. This build therefore accepts ONLY `http://127.0.0.1:8080` or `http://localhost:8080` root origins, rejecting paths, userinfo, queries/fragments, other ports and arbitrary HTTPS hosts. Saved old arbitrary origins fail closed. ApiClient independently validates the origin and permits only existing auth login/refresh/logout, integration-list and conversation routes; redirects are rejected. Production origin and extension distribution ID remain DECISION REQUIRED.

The only added host permissions are loopback HTTP hosts; Chrome match patterns do not constrain ports, so application validation further pins port 8080. This is required for direct requests from extension pages; no `<all_urls>`, wildcard external domain, content script, remote script or CSP weakening was added. [Chrome cross-origin request documentation](https://developer.chrome.com/docs/extensions/develop/concepts/network-requests). Native loaded-extension execution is NOT VERIFIED; no extension was installed into the user's browser. Node contract tests do not prove Chrome host-permission execution or popup lifecycle.

## Containers and configuration

Backend Dockerfile builds with Java 21 JDK/Gradle wrapper 9.7.1, runs packaged Boot 4.1.1 JAR on Java 21 JRE as UID/GID 10001, exposes 8080, honors SIGTERM and external profile/config. It defaults to prod and does not include the compiler/Gradle in runtime. JVM defaults cap heap at 75% of available container memory and exit on OOM; deployment memory sizing remains unverified. Liveness/readiness are existing Spring probes; an orchestrator must probe them. Readiness includes PostgreSQL and Redis. Image build intentionally does not run tests: the complete host regression suite is a separate prerequisite.

Runtime needs DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, AUTH_ACCESS_TOKEN_SIGNING_KEY; Redis HOST/PORT/password and explicit APP_CORS_ALLOWED_ORIGINS are operator supplied. Do not bake runtime secrets into image/build args. Local infrastructure Compose is unchanged; containers must join its existing network and use postgres/redis hostnames, not container localhost. Production secret injection/access-control/rotation and TLS remain deployment tasks.

Web uses standalone Next production output and `node server.js` as UID/GID 1001 on port 3000. Both public/static assets are copied. `.env*`, node_modules, build outputs and private-key file types are excluded from build context. NEXT_PUBLIC_API_BASE_URL is PUBLIC, build-time inlined, not a secret and not runtime-reconfigurable. Build a separate reviewed image per approved API origin/environment; setting it at `docker run` cannot change the browser bundle. No default API URL is provided. Production builds require an explicit HTTPS origin without userinfo/query/fragment/path; local verification requires explicit ALLOW_LOCAL_API=true. Never publish the local-verification image as a production deployment.

Commands from the repository root:

```sh
docker build -t ai-order-backend:verification backend
docker build -t ai-order-web:verification \
  --build-arg NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:18088 \
  --build-arg ALLOW_LOCAL_API=true web
```

For a deployment build omit ALLOW_LOCAL_API and supply the approved public HTTPS API origin; none is assumed here. Pin reviewed base-image digests and scan images before deployment. Current images use resolved upstream Java 21/Node 24 family tags, not a guaranteed vulnerability-free release.

Verification commands from each component directory:

```sh
# backend, with existing local database/Redis environment configured
./gradlew test --rerun-tasks --console=plain
./gradlew bootJar --console=plain
# web
npm run typecheck
npm run lint
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:18088 npm run build
node --test tests/chat.test.mjs
# extension (no existing typecheck npm script)
npx tsc -b
npm run lint
npm run build
node --test tests/security.test.mjs
```

Linux `npm ci` exposed missing @emnapi/core/runtime 1.11.3 lock entries. Only those missing transitive records were added; no declared dependency/architecture upgrade. npm audit reports five high-severity findings in @next/eslint-plugin-next, eslint-config-next, braces, fast-glob and micromatch (lint/build tooling chain); no forced breaking fix was applied. This is a warning requiring a separately reviewed dependency remediation, not proof the deployed client is compromised or safe.

API Gateway is design only: see API-GATEWAY-DESIGN.md and the validated route-inventory OpenAPI. No AWS SDK/Bedrock dependency, resource or deployment was added. The 30-second Gateway timeout versus existing execution overhead is unresolved.
