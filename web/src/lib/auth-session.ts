export interface AuthSession {
  email: string;
  accessToken: string;
  accessTokenExpiresAt: number;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

type SessionListener = () => void;

let currentSession: AuthSession | null = null;
const listeners = new Set<SessionListener>();

export function getAuthSession(): AuthSession | null {
  return currentSession;
}

export function setAuthSession(session: AuthSession): void {
  currentSession = session;
  listeners.forEach((listener) => listener());
}

export function clearAuthSession(): void {
  if (currentSession === null) {
    return;
  }

  currentSession = null;
  listeners.forEach((listener) => listener());
}

export function subscribeToAuthSession(listener: SessionListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
