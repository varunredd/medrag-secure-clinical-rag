import { redirect } from "next/navigation";

import { ComplianceRequestsClient } from "@/components/ComplianceRequestsClient";
import { TenantSettingsClient } from "@/components/TenantSettingsClient";
import { authenticatedSession } from "@/lib/backend";

export default async function TenantSettingsPage() {
  const current = await authenticatedSession();
  if (!current?.session.user.roles.includes("CLINIC_ADMIN")) {
    redirect("/dashboard");
  }

  return (
    <>
      <header className="pageHeader operationalHeader">
        <div>
          <p className="eyebrow">TENANT GOVERNANCE</p>
          <h1>Clinic settings</h1>
          <p>
            Configure retention, ingestion limits, and private model routing without exposing credentials to the browser.
          </p>
        </div>
      </header>
      <TenantSettingsClient />
      <div className="settingsStandalone">
        <ComplianceRequestsClient />
      </div>
    </>
  );
}
