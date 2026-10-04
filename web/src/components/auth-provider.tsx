"use client";

import { createContext, useContext, useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";

import { apiClient, setSessionExpiredHandler } from "@/lib/api-client";
import {
  clearAuthSession,
  getAuthSession,
  setAuthSession,
  subscribeToAuthSession,
  type AuthSession,
} from "@/lib/auth-session";

interface AuthContextValue {
  session: AuthSession | null;
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const session = useSyncExternalStore(
    subscribeToAuthSession,
    getAuthSession,
    () => null,
  );

  useEffect(() => {
    setSessionExpiredHandler(() => router.replace("/login"));
    return () => setSessionExpiredHandler(null);
  }, [router]);

  async function login(email: string, password: string): Promise<void> {
    setAuthSession(await apiClient.login(email, password));
  }

  async function logout(): Promise<void> {
    const activeSession = getAuthSession();
    try {
      if (activeSession) {
        await apiClient.logout(activeSession.refreshToken);
      }
    } finally {
      clearAuthSession();
      router.replace("/login");
    }
  }

  return (
    <AuthContext.Provider value={{ session, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
