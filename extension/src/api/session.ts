import { ApiClient, ApiClientError } from './client.ts'

type Tokens = { accessToken: string; refreshToken: string; refreshTokenExpiresAt: string }
export type Integration = { id: number; displayName: string; enabled: boolean }
export type ChatTurn = {
  text: string; supportStatus: string; externalDataRetrieved: boolean;
  facts: { field: string; value: string | null; sourceTimestamp: string | null; valueTrust: 'EXTERNAL_UNTRUSTED' }[]
}

/** Popup-memory session only. Closing/reloading the popup requires a new login. */
export class ExtensionSession {
  private tokens: Tokens | null = null
  private refreshing: Promise<boolean> | null = null
  private client: ApiClient
  get authenticated() { return this.tokens !== null }
  constructor(baseUrl: string) { this.client = new ApiClient({ baseUrl, getAccessToken: async () => this.tokens?.accessToken ?? null }) }
  async login(email: string, password: string) {
    this.tokens = null
    this.tokens = await this.client.request<Tokens>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })
  }
  async logout() {
    const tokens = this.tokens; this.tokens = null
    if (tokens) await this.client.request('/api/v1/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken: tokens.refreshToken }) })
  }
  private async refresh(): Promise<boolean> {
    const tokens = this.tokens
    if (!tokens || Date.parse(tokens.refreshTokenExpiresAt) <= Date.now()) { this.tokens = null; return false }
    try {
      const rotated = await this.client.request<Tokens>('/api/v1/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: tokens.refreshToken }) })
      if (this.tokens !== tokens) return false // Logout/new login won a concurrent refresh.
      this.tokens = rotated; return true
    } catch { if (this.tokens === tokens) this.tokens = null; return false }
  }
  async request<T>(path: `/${string}`, init: RequestInit = {}): Promise<T> {
    if (!this.tokens) throw new ApiClientError('Sign in again.', 401, null)
    try { return await this.client.request<T>(path, init) }
    catch (error) {
      if (!(error instanceof ApiClientError) || error.status !== 401) throw error
      this.refreshing ??= this.refresh().finally(() => { this.refreshing = null })
      if (await this.refreshing) {
        try { return await this.client.request<T>(path, init) }
        catch (retryError) { if (retryError instanceof ApiClientError && retryError.status === 401) this.tokens = null; throw retryError }
      }
      throw new ApiClientError('Session expired.', 401, error.requestId)
    }
  }
}
export function safeError(error: unknown): string {
  if (!(error instanceof ApiClientError)) return 'The backend is unavailable. Check your local connection.'
  const messages: Record<number, string> = { 401: 'Sign in again. Authentication failed or expired.', 403: 'Access denied.', 429: 'Too many requests. Wait before retrying.', 503: 'AI service unavailable or not configured.' }
  return (messages[error.status] ?? 'The backend request failed.') + (error.requestId ? ` Request ID: ${error.requestId}` : '')
}
