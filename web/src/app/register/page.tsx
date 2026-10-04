"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { AuthFrame } from "@/components/auth-frame";
import { apiClient, ApiError } from "@/lib/api-client";
import styles from "../auth-form.module.css";

const PASSWORD_REQUIREMENT =
  "Use 12–128 characters with uppercase, lowercase, a number, and a special character.";

export default function RegisterPage() {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;

    const form = new FormData(event.currentTarget);
    const email = String(form.get("email") ?? "");
    const password = String(form.get("password") ?? "");

    setPending(true);
    setError("");
    setSuccess("");
    setFieldErrors({});

    try {
      await apiClient.register(email, password);
      setSuccess("Account created. Taking you to sign in…");
      window.setTimeout(() => router.replace("/login"), 900);
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFieldErrors(caught.fieldErrors);
        setError(
          caught.status === 409
            ? "An account with this email already exists."
            : caught.message,
        );
      } else {
        setError("Registration could not be completed. Please try again.");
      }
      setPending(false);
    }
  }

  return (
    <AuthFrame
      eyebrow="Create your account"
      title="Start securely."
      description="Registration is handled by the existing backend, including validation, tenant creation, and Argon2id password hashing."
    >
      <div className={styles.heading}>
        <h2>Register</h2>
        <p>Create an account before signing in.</p>
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
            autoComplete="new-password"
            minLength={12}
            maxLength={128}
            required
            disabled={pending}
          />
          <p className={styles.hint}>{PASSWORD_REQUIREMENT}</p>
          {fieldErrors.password && (
            <p className={styles.fieldError}>{fieldErrors.password}</p>
          )}
        </div>
        {error && (
          <p className={styles.error} role="alert">
            {error}
          </p>
        )}
        {success && (
          <p className={styles.success} role="status">
            {success}
          </p>
        )}
        <button className={styles.submit} type="submit" disabled={pending}>
          {pending ? "Creating account…" : "Create account"}
        </button>
      </form>
      <p className={styles.alternate}>
        Already registered? <Link href="/login">Sign in</Link>
      </p>
    </AuthFrame>
  );
}
