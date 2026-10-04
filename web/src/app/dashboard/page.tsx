import Link from "next/link";

import { AppShell } from "@/components/app-shell";
import { IntegrationOverview } from "@/components/integration-overview";
import styles from "./page.module.css";

export default function DashboardPage() {
  return (
    <AppShell
      eyebrow="Authenticated workspace"
      title="Dashboard"
      description="A minimal proof that the browser session can reach tenant-scoped backend resources."
    >
      <section className={styles.summary}>
        <div>
          <p>Backend connection</p>
          <strong>Protected API</strong>
          <span>Bearer authentication enabled</span>
        </div>
        <div>
          <p>Session model</p>
          <strong>In memory</strong>
          <span>Cleared on reload or logout</span>
        </div>
        <div>
          <p>Data boundary</p>
          <strong>Tenant scoped</strong>
          <span>Resolved by Spring Security</span>
        </div>
      </section>

      <section className={styles.integrations}>
        <div className={styles.sectionHeader}>
          <div>
            <p>Live backend result</p>
            <h2>Integrations</h2>
          </div>
          <Link href="/integrations">View integrations →</Link>
        </div>
        <IntegrationOverview compact />
      </section>
    </AppShell>
  );
}
