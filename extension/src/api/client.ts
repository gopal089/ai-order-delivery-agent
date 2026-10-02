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
    this.baseUrl = config.baseUrl.replace(/\/+$/, '')
    this.getAccessToken = config.getAccessToken
  }

  async request<TResponse>(
    path: `/${string}`,
    init: RequestInit = {},
  ): Promise<TResponse> {
    const accessToken = await this.getAccessToken?.()
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
