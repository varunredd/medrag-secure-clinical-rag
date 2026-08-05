import { redirect } from "next/navigation";

import { AuditClient } from "@/components/AuditClient";
import { authenticatedSession } from "@/lib/backend";

export default async function AuditPage() {
  const current = await authenticatedSession();
  if (
    !current?.session.user.roles.some((role) =>
      ["AUDITOR", "CLINIC_ADMIN"].includes(role),
    )
  ) {
    redirect("/dashboard");
  }

  return (
    <>
      <header className="pageHeader">
        <div>
          <p className="eyebrow">ACCOUNTABILITY</p>
          <h1>Audit trail</h1>
          <p>Tenant-scoped, append-only security and clinical access events.</p>
        </div>
      </header>
      <AuditClient />
    </>
  );
}
