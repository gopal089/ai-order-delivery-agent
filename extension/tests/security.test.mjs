import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import { validateBackendBaseUrl } from '../src/shared/settings.ts'
import { ApiClient, ApiClientError } from '../src/api/client.ts'
import { ExtensionSession, safeError } from '../src/api/session.ts'
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })
test('only exact approved local backend origins are accepted', () => {
  for (const url of ['http://127.0.0.1:8080', 'http://localhost:8080/']) assert.equal(validateBackendBaseUrl(url), null)
  for (const url of ['https://customer.example', 'http://127.0.0.1:18088', 'http://localhost:8080/api', 'http://localhost:8080?token=x', 'http://user:secret@localhost:8080', 'http://localhost:8080#x', 'http://localhost.evil:8080']) assert.ok(validateBackendBaseUrl(url))
})
test('arbitrary routes and redirects are blocked; only backend bearer is sent', async () => {
  const client = new ApiClient({ baseUrl: 'http://localhost:8080', getAccessToken: async () => 'synthetic-test-access' })
  let calls = 0
  globalThis.fetch = async (url, options) => {
    calls++; assert.equal(url, 'http://localhost:8080/api/v1/integrations')
    assert.equal(options.headers.get('Authorization'), 'Bearer synthetic-test-access')
    assert.equal(options.credentials, 'omit'); assert.equal(options.redirect, 'error'); assert.equal(options.cache, 'no-store')
    return Response.json([])
  }
  await assert.rejects(client.request('//customer.example/api'))
  await assert.rejects(client.request('/api/v1/conversations/1/messages?tenant_id=2'))
  assert.equal(calls, 0); await client.request('/api/v1/integrations'); assert.equal(calls, 1)
})
test('401 refresh rotates tokens once then retries without forwarding bearer to refresh', async () => {
  const session = new ExtensionSession('http://localhost:8080'); let requests = 0; let refreshes = 0
  const tokens = (accessToken, refreshToken) => ({ accessToken, refreshToken, refreshTokenExpiresAt: new Date(Date.now() + 60000).toISOString() })
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/login')) return Response.json(tokens('synthetic-old', 'synthetic-refresh-old'))
    if (url.endsWith('/refresh')) {
      refreshes++; assert.equal(options.headers.get('Authorization'), null)
      assert.deepEqual(JSON.parse(options.body), { refreshToken: 'synthetic-refresh-old' })
      return Response.json(tokens('synthetic-new', 'synthetic-refresh-new'))
    }
    requests++; if (requests === 1) return new Response(null, { status: 401 })
    assert.equal(options.headers.get('Authorization'), 'Bearer synthetic-new'); return Response.json([])
  }
  await session.login('fixture@example.test', 'synthetic-test-only'); await session.request('/api/v1/integrations')
  assert.equal(requests, 2); assert.equal(refreshes, 1); assert.equal(session.authenticated, true)
})
test('logout clears popup session even when backend fails', async () => {
  const session = new ExtensionSession('http://localhost:8080')
  globalThis.fetch = async (url) => url.endsWith('/login') ? Response.json({ accessToken: 'synthetic', refreshToken: 'synthetic', refreshTokenExpiresAt: new Date(Date.now()+60000).toISOString() }) : new Response(null, { status: 503 })
  await session.login('fixture@example.test', 'synthetic-test-only'); await assert.rejects(session.logout())
  assert.equal(session.authenticated, false); await assert.rejects(session.request('/api/v1/integrations'), { status: 401 })
})
for (const status of [401, 403, 429, 503, 500]) test(`safe ${status} handling preserves request ID without backend exception text`, () => {
  const text = safeError(new ApiClientError('sensitive-backend-detail', status, 'fixture-request'))
  assert.ok(text.includes('fixture-request')); assert.ok(!text.includes('sensitive-backend-detail'))
})
test('built MV3 manifest uses only narrow loopback hosts and existing files', () => {
  const manifest = JSON.parse(readFileSync(new URL('../dist/manifest.json', import.meta.url)))
  assert.equal(manifest.manifest_version, 3); assert.deepEqual(manifest.permissions, ['storage'])
  assert.deepEqual(manifest.host_permissions, ['http://127.0.0.1/*', 'http://localhost/*'])
  assert.equal(manifest.content_security_policy, undefined)
  for (const file of [manifest.action.default_popup, manifest.options_ui.page, manifest.background.service_worker]) assert.ok(existsSync(new URL(`../dist/${file}`, import.meta.url)))
})
