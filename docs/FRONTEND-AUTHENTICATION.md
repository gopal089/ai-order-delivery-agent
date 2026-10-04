# Frontend authentication integration

The Next.js application calls the existing Spring Boot authentication API directly through a centralized browser API client.

## Local configuration

Set the public, non-secret API origin in `web/.env.local`:

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8080
```

`NEXT_PUBLIC_*` values are bundled into browser code. Passwords, tokens, signing keys, provider credentials, and other secrets must never be added to this file.

The backend accepts exact local development origins through `APP_CORS_ALLOWED_ORIGINS`. It permits the `Authorization` and JSON headers and the required HTTP methods. Wildcard origins and cookie credentials are not enabled.

## Session design

The backend returns bearer access and opaque refresh tokens in JSON. It does not currently provide HttpOnly-cookie authentication. The frontend therefore keeps both tokens in a single in-memory session store:

- tokens are never written to local storage, session storage, URLs, logs, or rendered UI;
- client-side navigation preserves the session;
- a full page reload clears the session and redirects protected routes to login;
- an authenticated `401` starts exactly one shared refresh request;
- a successful refresh replaces both the access token and rotated refresh token, then retries the original request once;
- a failed refresh or second `401` clears the session and redirects to login;
- logout calls the backend with the current refresh token and always clears local state.

This is the safest client-only mechanism compatible with the existing JSON token contract without redesigning backend authentication or adding cookie/CSRF behavior.

## Routes

- `/` — existing public landing page
- `/register` — real backend registration
- `/login` — real backend login
- `/dashboard` — protected connectivity summary and integration fetch
- `/integrations` — protected display of the real tenant-scoped integration response

Frontend route guards provide user experience only. Spring Security and the server-resolved tenant/user context remain the authoritative security boundary.
