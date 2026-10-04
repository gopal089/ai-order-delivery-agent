"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { useAuth } from "./auth-provider";
import { ProtectedRoute } from "./protected-route";
import styles from "./app-shell.module.css";

interface AppShellProps {
  eyebrow: string;
  title: string;
  description: string;
  children: React.ReactNode;
}

export function AppShell({
  eyebrow,
  title,
  description,
  children,
}: AppShellProps) {
  const { session, logout } = useAuth();
  const pathname = usePathname();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogout() {
    if (loggingOut) return;
    setLoggingOut(true);
    await logout();
  }

  return (
    <ProtectedRoute>
      <div className={styles.page}>
        <header className={styles.header}>
          <Link className={styles.brand} href="/dashboard">
            <span aria-hidden="true">OA</span>
            Order Agent
          </Link>
          <nav className={styles.nav} aria-label="Application navigation">
            <Link
              className={pathname === "/dashboard" ? styles.active : undefined}
              href="/dashboard"
            >
              Dashboard
            </Link>
            <Link
              className={pathname === "/integrations" ? styles.active : undefined}
              href="/integrations"
            >
              Integrations
            </Link>
            <Link className={pathname === "/chat" ? styles.active : undefined} href="/chat">Chat</Link>
            <button type="button" onClick={handleLogout} disabled={loggingOut}>
              {loggingOut ? "Signing out…" : "Sign out"}
            </button>
          </nav>
        </header>

        <main className={styles.main}>
          <section className={styles.intro}>
            <div>
              <p className={styles.eyebrow}>{eyebrow}</p>
              <h1>{title}</h1>
              <p className={styles.description}>{description}</p>
            </div>
            <div className={styles.identity}>
              <span>Authenticated</span>
              <strong>{session?.email}</strong>
              <small>Session held in memory</small>
            </div>
          </section>
          {children}
        </main>
      </div>
    </ProtectedRoute>
  );
}
