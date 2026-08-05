import { redirect } from "next/navigation";

import { OperationsHealthClient } from "@/components/OperationsHealthClient";
import { authenticatedSession } from "@/lib/backend";

export default async function OperationsPage() {
  const current = await authenticatedSession();
  if (
    !current?.session.user.roles.some((role) =>
      ["CLINIC_ADMIN", "AUDITOR"].includes(role),
    )
  ) {
    redirect("/dashboard");
  }

  return (
    <>
      <header className="pageHeader operationalHeader">
        <div>
          <p className="eyebrow">OPERATOR CONSOLE</p>
          <h1>Platform readiness</h1>
          <p>
            Read-only dependency health and correlation support codes for clinical operations.
          </p>
        </div>
      </header>
      <OperationsHealthClient />
    </>
  );
}
