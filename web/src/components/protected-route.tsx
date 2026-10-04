"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { useAuth } from "./auth-provider";
import styles from "./protected-route.module.css";

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { session } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!session) {
      router.replace("/login");
    }
  }, [router, session]);

  if (!session) {
    return (
      <main className={styles.loading} aria-live="polite">
        <span />
        Checking your session…
      </main>
    );
  }

  return children;
}
