"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { AuthFrame } from "@/components/auth-frame";
import { useAuth } from "@/components/auth-provider";
import { ApiError } from "@/lib/api-client";
import styles from "../auth-form.module.css";

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;

    const form = new FormData(event.currentTarget);
    const email = String(form.get("email") ?? "");
    const password = String(form.get("password") ?? "");

    setPending(true);
    setError("");
    setFieldErrors({});

    try {
      await login(email, password);
      router.replace("/dashboard");
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFieldErrors(caught.fieldErrors);
        setError(
          caught.status === 401
            ? "Email or password is incorrect."
            : caught.message,
        );
      } else {
        setError("Login could not be completed. Please try again.");
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthFrame
      eyebrow="Secure access"
      title="Welcome back."
      description="Sign in to reach tenant-scoped data through the existing Spring Security boundary."
    >
      <div className={styles.heading}>
        <h2>Sign in</h2>
        <p>Use the email and password registered with Order Agent.</p>
      </div>
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        <div className={styles.field}>
          <label htmlFor="email">Email</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            maxLength={320}
            required
            disabled={pending}
          />
          {fieldErrors.email && (
            <p className={styles.fieldError}>{fieldErrors.email}</p>
          )}
        </div>
        <div className={styles.field}>
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            maxLength={128}
            required
            disabled={pending}
          />
          {fieldErrors.password && (
            <p className={styles.fieldError}>{fieldErrors.password}</p>
          )}
        </div>
        {error && (
          <p className={styles.error} role="alert">
            {error}
          </p>
        )}
        <button className={styles.submit} type="submit" disabled={pending}>
          {pending ? "Signing in…" : "Sign in"}
        </button>
      </form>
      <p className={styles.alternate}>
        New to Order Agent? <Link href="/register">Create an account</Link>
      </p>
    </AuthFrame>
  );
}
