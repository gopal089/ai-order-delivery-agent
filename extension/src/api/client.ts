import { validateBackendBaseUrl } from '../shared/settings.ts'

export type ApiClientConfig = {
  baseUrl: string
  getAccessToken?: () => Promise<string | null>
}

export class ApiClientError extends Error {
  readonly status: number
  readonly requestId: string | null

  constructor(
    message: string,
    status: number,
    requestId: string | null,
  ) {
    super(message)
    this.name = 'ApiClientError'
    this.status = status
    this.requestId = requestId
  }
}

export class ApiClient {
  private readonly baseUrl: string
  private readonly getAccessToken?: () => Promise<string | null>

  constructor(config: ApiClientConfig) {
    if (validateBackendBaseUrl(config.baseUrl)) throw new Error('Backend address is not approved.')
    this.baseUrl = new URL(config.baseUrl.trim()).origin
    this.getAccessToken = config.getAccessToken
  }

  async request<TResponse>(
    path: `/${string}`,
    init: RequestInit = {},
  ): Promise<TResponse> {
    if (!/^\/api\/v1\/(auth\/(login|refresh|logout)|integrations|conversations(\/[1-9]\d*\/messages)?)$/.test(path))
      throw new Error('Unsupported backend route.')
    const accessToken = path.startsWith('/api/v1/auth/') ? null : await this.getAccessToken?.()
    const headers = new Headers(init.headers)

    headers.set('Accept', 'application/json')

    if (init.body && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }

    if (accessToken) {
      headers.set('Authorization', `Bearer ${accessToken}`)
    }

    const response = await fetch(`${this.baseUrl}${path}`, {
      ...init,
      headers,
      credentials: 'omit',
      redirect: 'error',
      cache: 'no-store',
    })

    if (!response.ok) {
      throw new ApiClientError(
        'The backend request failed.',
        response.status,
        response.headers.get('x-request-id'),
      )
    }

    if (response.status === 204) {
      return undefined as TResponse
    }

    return (await response.json()) as TResponse
  }
}
