"use client";

import { useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { integrationApi, integrationError, validateIntegration, type IntegrationFields } from "../lib/integration-api";
import { IntegrationOverview } from "./integration-overview";
import styles from "../app/auth-form.module.css";

export function IntegrationManagement() {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const submitting = useRef(false);
  const [fields, setFields] = useState<IntegrationFields>({});
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [version, setVersion] = useState(0);
  const formRef = useRef<HTMLFormElement>(null);
  function close() {
    if (submitting.current) return;
    formRef.current?.reset(); setOpen(false); setFields({}); setError(""); setSuccess("");
  }
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (submitting.current) return;
    const form = event.currentTarget; const data = new FormData(form);
    const input = { providerKey: String(data.get("providerKey") ?? ""), displayName: String(data.get("displayName") ?? ""), baseUrl: String(data.get("baseUrl") ?? ""), enabled: data.get("enabled") === "on" };
    const invalid = validateIntegration(input); setFields(invalid); setError(""); setSuccess("");
    if (Object.keys(invalid).length) return;
    submitting.current = true; setPending(true);
    try {
      await integrationApi.create(input);
      form.reset(); setOpen(false); setVersion((previous) => previous + 1);
      setSuccess("Integration metadata saved. Credential configuration is not available yet; this does not connect a live provider.");
    } catch (caught) { const failure = integrationError(caught); setError(failure.message); setFields(failure.fields); }
    finally { submitting.current = false; setPending(false); }
  }
  return <section>
    <p>Configure metadata only. Credential configuration is not available yet. Do not enter API keys or passwords in any field.</p>
    {!open && <button type="button" className={styles.submit} onClick={() => { setOpen(true); setSuccess(""); }}>Create integration</button>}
    {open && <form ref={formRef} onSubmit={create} noValidate className={styles.form} aria-label="Create integration">
      <h2>Create integration metadata</h2>
      <div className={styles.field}><label htmlFor="providerKey">Provider key</label><input id="providerKey" name="providerKey" required maxLength={100} disabled={pending} aria-invalid={!!fields.providerKey} aria-describedby={fields.providerKey ? "providerKey-error" : undefined} />{fields.providerKey && <p id="providerKey-error" className={styles.fieldError} role="alert">{fields.providerKey}</p>}</div>
      <div className={styles.field}><label htmlFor="displayName">Display name</label><input id="displayName" name="displayName" required maxLength={200} disabled={pending} aria-invalid={!!fields.displayName} aria-describedby={fields.displayName ? "displayName-error" : undefined} />{fields.displayName && <p id="displayName-error" className={styles.fieldError} role="alert">{fields.displayName}</p>}</div>
      <div className={styles.field}><label htmlFor="baseUrl">Base URL</label><input id="baseUrl" name="baseUrl" type="url" required maxLength={2048} disabled={pending} aria-invalid={!!fields.baseUrl} aria-describedby={fields.baseUrl ? "baseUrl-error" : "baseUrl-hint"} /><p id="baseUrl-hint" className={styles.hint}>Use your documented provider base URL. HTTPS is required outside the local backend profile; private/internal addresses are rejected by the backend.</p>{fields.baseUrl && <p id="baseUrl-error" className={styles.fieldError} role="alert">{fields.baseUrl}</p>}</div>
      <label><input name="enabled" type="checkbox" defaultChecked disabled={pending} /> Enabled</label>
      {error && <p className={styles.error} role="alert">{error}</p>}
      <button type="submit" disabled={pending} className={styles.submit}>{pending ? "Saving…" : "Save integration"}</button>
      <button type="reset" disabled={pending} onClick={() => { setFields({}); setError(""); }}>Reset form</button>
      <button type="button" disabled={pending} onClick={close}>Cancel</button>
    </form>}
    {success && <p role="status" className={styles.success}>{success} <Link href="/chat">Open chat</Link></p>}
    <IntegrationOverview refreshVersion={version} />
  </section>;
}
