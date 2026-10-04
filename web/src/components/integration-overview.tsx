"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { apiClient, type Integration } from "@/lib/api-client";
import { integrationError } from "@/lib/integration-api";
import styles from "./integration-overview.module.css";

export function IntegrationOverview({ compact = false, refreshVersion = 0 }: { compact?: boolean; refreshVersion?: number }) {
  const [integrations, setIntegrations] = useState<Integration[] | null>(null);
  const [error, setError] = useState("");
  const requested = useRef<number | null>(null);
  const sequence = useRef(0);

  const loadIntegrations = useCallback(async () => {
    const current = ++sequence.current;
    setError("");
    setIntegrations(null);
    try {
      const rows = await apiClient.integrations();
      if (current === sequence.current) setIntegrations(rows);
    } catch (caught) {
      if (current === sequence.current) setError(integrationError(caught).message);
    }
  }, []);

  useEffect(() => {
    if (requested.current === refreshVersion) return;
    requested.current = refreshVersion;
    void loadIntegrations();
  }, [loadIntegrations, refreshVersion]);

  if (integrations === null && !error) {
    return <div className={styles.state}>Loading integrations…</div>;
  }

  if (error) {
    return (
      <div className={styles.state} role="alert">
        <p>{error}</p>
        <button type="button" onClick={loadIntegrations}>
          Try again
        </button>
      </div>
    );
  }

  if (integrations?.length === 0) {
    return (
      <div className={styles.empty}>
        <span>0 configured</span>
        <h2>No integrations configured yet.</h2>
        <p>
          This result came from the authenticated backend. Create integration metadata on the Integrations page to get started. Credentials and live provider connection are not configured by this form.
        </p>
      </div>
    );
  }

  return (
    <div className={compact ? styles.compactGrid : styles.grid}>
      {integrations?.map((integration) => (
        <article className={styles.card} key={integration.id}>
          <div className={styles.cardHeader}>
            <span>{integration.providerKey}</span>
            <span>{integration.enabled ? "Enabled" : "Disabled"}</span>
          </div>
          <h2>{integration.displayName}</h2>
          <p>{integration.baseUrl}</p>
          <dl>
            <div>
              <dt>Credential</dt>
              <dd>
                {integration.credentialConfigured ? "Configured" : "Not configured"}
              </dd>
            </div>
            <div>
              <dt>Updated</dt>
              <dd>{new Date(integration.updatedAt).toLocaleDateString()}</dd>
            </div>
          </dl>
        </article>
      ))}
    </div>
  );
}
