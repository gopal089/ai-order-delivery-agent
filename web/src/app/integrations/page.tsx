import { AppShell } from "@/components/app-shell";
import { IntegrationManagement } from "@/components/integration-management";

export default function IntegrationsPage() {
  return (
    <AppShell
      eyebrow="Tenant-scoped resource"
      title="Integrations"
      description="This page displays only the integration metadata returned for the authenticated tenant and user. Credentials are never returned to the browser."
    >
      <IntegrationManagement />
    </AppShell>
  );
}
