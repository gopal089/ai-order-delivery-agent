import {
  clearAuthSession,
  getAuthSession,
  setAuthSession,
  type AuthSession,
} from "./auth-session";

export interface ApiErrorPayload {
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly fieldErrors: Record<string, string> = {},
    public readonly requestId: string | null = null,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface RegisterResponse {
  id: number;
  email: string;
  createdAt: string;
}

export interface TokenResponse {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresInSeconds: number;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

export interface Integration {
  id: number;
  providerKey: string;
  displayName: string;
  baseUrl: string;
  enabled: boolean;
  credentialConfigured: boolean;
  credentialType: string | null;
  createdAt: string;
  updatedAt: string;
}

interface RequestOptions {
  authenticated?: boolean;
  retryAfterRefresh?: boolean;
}

type SessionExpiredHandler = () => void;

const configuredBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
const API_BASE_URL = configuredBaseUrl?.replace(/\/$/, "") ?? "";

let refreshPromise: Promise<boolean> | null = null;
let sessionExpiredHandler: SessionExpiredHandler | null = null;

export function setSessionExpiredHandler(
  handler: SessionExpiredHandler | null,
): void {
  sessionExpiredHandler = handler;
}

function assertConfigured(): void {
  if (!API_BASE_URL) {
    throw new ApiError(
      0,
      "API_CONFIGURATION_ERROR",
      "The API base URL is not configured.",
    );
  }
}

function createSession(email: string, tokens: TokenResponse): AuthSession {
  return {
    email,
    accessToken: tokens.accessToken,
    accessTokenExpiresAt:
      Date.now() + tokens.accessTokenExpiresInSeconds * 1000,
    refreshToken: tokens.refreshToken,
    refreshTokenExpiresAt: tokens.refreshTokenExpiresAt,
  };
}

async function parseError(response: Response): Promise<ApiError> {
  let payload: ApiErrorPayload = {};

  try {
    payload = (await response.json()) as ApiErrorPayload;
  } catch {
    // Security entry points may intentionally return an empty response body.
  }

  return new ApiError(
    response.status,
    payload.code ?? "REQUEST_FAILED",
    payload.message ?? "The request could not be completed.",
    payload.fieldErrors ?? {},
    response.headers.get("x-request-id"),
  );
}

async function performRefresh(): Promise<boolean> {
  const session = getAuthSession();
  if (!session || Date.parse(session.refreshTokenExpiresAt) <= Date.now()) {
    clearAuthSession();
    sessionExpiredHandler?.();
    return false;
  }

  try {
    const tokens = await request<TokenResponse>(
      "/api/v1/auth/refresh",
      {
        method: "POST",
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      },
      { retryAfterRefresh: false },
    );
    // A pending refresh must not restore a session after logout or replace a newer login.
    if (getAuthSession() !== session) return false;
    setAuthSession(createSession(session.email, tokens));
    return true;
  } catch {
    if (getAuthSession() === session) {
      clearAuthSession();
      sessionExpiredHandler?.();
    }
    return false;
  }
}

async function refreshSession(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null;
    });
  }

  return refreshPromise;
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {},
): Promise<T> {
  assertConfigured();

  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (options.authenticated) {
    const session = getAuthSession();
    if (!session) {
      throw new ApiError(401, "SESSION_REQUIRED", "Please sign in to continue.");
    }
    headers.set("Authorization", `Bearer ${session.accessToken}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
    });
  } catch {
    throw new ApiError(
      0,
      "NETWORK_ERROR",
      "The backend is unavailable. Check that Spring Boot is running.",
    );
  }

  const canRefresh =
    response.status === 401 &&
    options.authenticated === true &&
    options.retryAfterRefresh !== false;

  if (canRefresh) {
    if (await refreshSession()) {
      return request<T>(path, init, {
        authenticated: true,
        retryAfterRefresh: false,
      });
    }
    throw new ApiError(401, "SESSION_EXPIRED", "Your session has expired.", {}, response.headers.get("x-request-id"));
  }

  if (!response.ok) {
    if (response.status === 401 && options.authenticated) {
      clearAuthSession();
      sessionExpiredHandler?.();
    }
    throw await parseError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export const apiClient = {
  get<T>(path: string, authenticated = false): Promise<T> {
    return request<T>(path, { method: "GET" }, { authenticated });
  },

  post<T, B>(path: string, body: B, authenticated = false): Promise<T> {
    return request<T>(
      path,
      { method: "POST", body: JSON.stringify(body) },
      { authenticated },
    );
  },

  patch<T, B>(path: string, body: B): Promise<T> {
    return request<T>(
      path,
      { method: "PATCH", body: JSON.stringify(body) },
      { authenticated: true },
    );
  },

  delete(path: string): Promise<void> {
    return request<void>(path, { method: "DELETE" }, { authenticated: true });
  },

  register(email: string, password: string): Promise<RegisterResponse> {
    return this.post<RegisterResponse, { email: string; password: string }>(
      "/api/v1/auth/register",
      { email, password },
    );
  },

  async login(email: string, password: string): Promise<AuthSession> {
    const tokens = await this.post<
      TokenResponse,
      { email: string; password: string }
    >("/api/v1/auth/login", { email, password });
    return createSession(email.trim().toLowerCase(), tokens);
  },

  async logout(refreshToken: string): Promise<void> {
    await this.post<void, { refreshToken: string }>(
      "/api/v1/auth/logout",
      { refreshToken },
    );
  },

  integrations(): Promise<Integration[]> {
    return this.get<Integration[]>("/api/v1/integrations", true);
  },
};
