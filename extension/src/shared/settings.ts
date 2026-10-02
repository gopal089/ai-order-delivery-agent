export type ExtensionSettings = {
  backendBaseUrl: string
}

export const DEFAULT_SETTINGS: ExtensionSettings = {
  backendBaseUrl: '',
}

export function normalizeBackendBaseUrl(value: string): string {
  return value.trim().replace(/\/+$/, '')
}

export function validateBackendBaseUrl(value: string): string | null {
  const normalized = normalizeBackendBaseUrl(value)

  if (!normalized) {
    return 'Enter the backend URL before saving.'
  }

  let url: URL

  try {
    url = new URL(normalized)
  } catch {
    return 'Enter a complete URL, including https://.'
  }

  const isSecure = url.protocol === 'https:'
  const isLocalDevelopment =
    url.protocol === 'http:' &&
    (url.hostname === 'localhost' || url.hostname === '127.0.0.1')

  if (!isSecure && !isLocalDevelopment) {
    return 'Use HTTPS, or HTTP only for localhost development.'
  }

  if (url.username || url.password || url.search || url.hash) {
    return 'Do not include credentials, query parameters, or fragments.'
  }

  return null
}
