# Integration UI and conversation-history boundary

Verified locally on 2026-10-04. This change does not add backend endpoints, credentials, a model, external calls or infrastructure.

## Integration metadata

Authenticated `/integrations` now creates metadata through the existing `POST /api/v1/integrations`. The exact fields are `providerKey`, `displayName`, `baseUrl`, and `enabled`. Required values are validated before submission, but backend URL/security validation remains authoritative. The existing authenticated API client supplies authentication; the form supplies no tenant/user/session IDs or credential references. A synchronous submission guard and disabled controls prevent overlapping saves. Success resets/closes the form and reloads the owned integration list. Reset restores blank fields and Enabled=true; Cancel resets and closes without saving. Errors use fixed safe messages and preserve request IDs.

Credential configuration is unavailable: there is no production CredentialStore implementation or credential-creation contract. Saving metadata does not connect a live provider. Never place credentials in metadata fields. Multiple rows with the same provider key are currently allowed by the backend; the UI does not invent uniqueness rules.

Enabled saved integrations appear in chat using their actual backend IDs. Disabled integrations are omitted. Only the application backend is called; no external vendor URL is contacted by this UI.

## Conversations: missing list capability

The existing backend exposes `POST /api/v1/conversations`, `GET /api/v1/conversations/{conversationId}/messages`, and `POST /api/v1/conversations/{conversationId}/messages`. It does **not** expose a conversation-list GET endpoint; an authenticated `GET /api/v1/conversations` returned 405 during verification.

Per the task's explicit stop condition, no conversation browser/list, fabricated history, arbitrary-ID entry UI, or new backend listing endpoint was implemented. A later separately approved backend capability is required before authenticated history selection/reopening can be built. Known-ID history remains authorized by the backend.

New conversation clears the frontend conversation ID, displayed turns, draft and error, shows explicit new-draft feedback, and preserves integration selection. It neither calls DELETE nor immediately creates a database row. The next Send lazily creates a conversation with the existing empty-body POST. Previous persisted rows remain. Without a configured production model, Send returns the expected safe 503 and persists no user/assistant messages; this is not a successful AI answer.

Conversations and messages reside in PostgreSQL `public.conversations` and `public.messages`, not Redis or browser storage. Redis handles authentication rate limiting. History content cannot be demonstrated end-to-end with successful model turns in the current unconfigured runtime.

## Verification

`node --test tests/*.test.mjs`: 23 passed, 0 failed/skipped. `npm run typecheck`, `npm run lint`, and production build passed. Existing tests were retained; contract/validation/error/list-refresh/send-gating/reset tests were added without new dependencies.

Actual production localhost containers used backend 18088 and web 18089 with disposable users. Browser checks exercised login, empty integration state, required validation, Reset, Cancel, successful metadata save/list refresh, server rejection of a private base URL with request ID, pending disabled controls, enabled toggle, chat selection, disabled Send, actual Send/503, New conversation reset, retry while backend unavailable, retry recovery after backend restart, navigation and Sign out. Disabled integration was excluded from chat. Integration CRUD and known-ID history were checked through real endpoints; a second user received 403 for another user's integration GET/PATCH/DELETE and history GET.

Database assertions verified metadata persistence, two distinct browser-created conversation rows retained, and zero messages after both 503 sends. All disposable tenant data, accounts, verification containers and scoped Redis rate-limit keys were removed afterward; existing infrastructure/data was preserved. Flyway validated V1–V7; no migrations were edited. Runtime configured/synthetic secret-marker scan and browser console JWT/password-marker scan found zero matches. These scans are bounded checks, not a guarantee against every possible secret.

Not verified: history-list UI, successful persisted model conversations, full browser 401/403 paths, forced simultaneous rapid double-click under an artificially delayed network, real credential storage/provider execution, Bedrock. Safe status-error mappings and identity-field stripping have unit coverage. The backend full Gradle suite was not rerun for this frontend-only batch; live registration/login/refresh/logout and ownership checks passed.
