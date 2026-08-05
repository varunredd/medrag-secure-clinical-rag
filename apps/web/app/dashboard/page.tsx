import Link from "next/link";

import { OverviewClient } from "@/components/OverviewClient";
import { UploadIcon } from "@/components/Icons";
import { authenticatedSession } from "@/lib/backend";

export default async function Dashboard() {
  const current = await authenticatedSession();
  const roles = current?.session.user.roles ?? [];
  const canUpload = roles.some((role) =>
    ["DOCTOR", "NURSE", "CLINIC_ADMIN"].includes(role),
  );
  const canQuery = roles.some((role) => ["DOCTOR", "NURSE"].includes(role));
  const canAdmin = roles.includes("CLINIC_ADMIN");

  return (
    <>
      <header className="pageHeader operationalHeader">
        <div>
          <p className="eyebrow">CLINICAL OPERATIONS</p>
          <h1>
            {current?.session.user.name
              ? `Good day, ${current.session.user.name.split(" ")[0]}`
              : "Clinical workspace"}
          </h1>
          <p>
            Monitor secure ingestion, resolve failures, and open evidence-scoped review.
          </p>
        </div>
        {canUpload && (
          <Link className="primary" href="/dashboard/documents">
            <UploadIcon /> Upload clinical record
          </Link>
        )}
      </header>
      <OverviewClient
        canUpload={canUpload}
        canQuery={canQuery}
        canAdmin={canAdmin}
      />
    </>
  );
}
