# Redis-backed authentication rate limiting

Login and refresh abuse protection uses Spring Data Redis and a centralized `AuthenticationRateLimiter`. JWT validation, PostgreSQL refresh-token storage, rotation, reuse detection, and logout behavior are unchanged.

## Protected endpoints

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`

Registration, logout, and already-authenticated bearer requests are not dependent on the rate limiter.

## Atomic algorithm and TTL

Each request executes one Redis Lua script per applicable bucket. The script atomically increments the counter, assigns its TTL only when the key is first created, and returns the count and remaining TTL. Requests above the configured maximum receive `429 Too Many Requests` and a `Retry-After` header. Blocked requests do not extend the original fixed window.

Successful login deletes only that normalized identity's login counter. The IP request bucket is deliberately not reset, preventing successful credentials from bypassing IP-level spray protection. Refresh counters are not reset after rotation because the session UUID remains stable across the refresh-token family.

## Key strategy

All keys are bounded by TTL and use this namespace by default:

```text
auth:ratelimit:login:identity:<sha256(normalized-email)>
auth:ratelimit:login:ip:<sha256-direct-peer-address>
auth:ratelimit:refresh:session:<sha256-server-session-uuid>
auth:ratelimit:refresh:ip:<sha256-direct-peer-address>
```

The backend uses the servlet connection's direct peer address. It does not trust client-supplied forwarding headers. A future trusted reverse-proxy deployment must establish forwarded-address handling at the infrastructure/framework boundary before those headers are used.

Passwords are never passed to the rate limiter. Raw emails and IP addresses are hashed before becoming key components. Refresh rate limiting uses the server-side session UUID resolved from the hashed refresh-token database record; raw access and refresh tokens never appear in Redis keys or values.

## Defaults

| Bucket | Maximum | Fixed window | Purpose |
|---|---:|---:|---|
| Login identity | 5 | 15 minutes | Brute-force protection for known and unknown normalized emails |
| Login IP | 60 | 1 minute | Password-spray and automated request protection |
| Refresh session | 30 | 1 minute | Stable session-family protection across token rotation |
| Refresh IP | 120 | 1 minute | Automated refresh-endpoint protection |

The identity limit deliberately uses the longer window to slow targeted password guessing. The higher, shorter IP limits provide spray and automation protection without making ordinary local use impractical, while refresh limits allow normal client retry behavior. These are development-safe baselines and should be reviewed against observed production traffic before deployment.

All values and the namespace are configurable through the `AUTH_RATE_LIMIT_*` environment variables documented in `.env.example`. Redis uses `REDIS_HOST`, `REDIS_PORT`, and optional `REDIS_PASSWORD`, with configurable two-second connect/command timeouts.

## Redis failure behavior

Login and refresh fail closed with a generic `503 AUTHENTICATION_TEMPORARILY_UNAVAILABLE` when the Redis decision cannot be obtained. This prevents a Redis outage from becoming an unlimited brute-force bypass. Registration, logout, and requests using an already-issued valid access token continue to use their existing paths, so Redis failure does not disable the entire application.

No Redis errors, keys, counters, credentials, passwords, or tokens are returned to clients.

## Local verification

Start the local services and load the root environment before running tests:

```bash
docker compose up -d
docker compose ps

cd backend
set -a
source ../.env
set +a
./gradlew test
```
