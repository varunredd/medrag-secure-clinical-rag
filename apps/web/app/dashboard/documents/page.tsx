import { DocumentsClient } from "@/components/DocumentsClient";
import { authenticatedSession } from "@/lib/backend";

export default async function DocumentsPage() {
  const current = await authenticatedSession();
  const roles = current?.session.user.roles ?? [];
  const canUpload = roles.some((role) =>
    ["DOCTOR", "NURSE", "CLINIC_ADMIN"].includes(role),
  );
  const canRetry = roles.some((role) =>
    ["DOCTOR", "CLINIC_ADMIN"].includes(role),
  );
  const canDelete = roles.includes("CLINIC_ADMIN");

  return (
    <>
      <header className="pageHeader operationalHeader">
        <div>
          <p className="eyebrow">DOCUMENT OPERATIONS</p>
          <h1>Clinical records</h1>
          <p>
            Upload, monitor, retry, and govern the records available to the private retrieval boundary.
          </p>
        </div>
      </header>
      <DocumentsClient
        canUpload={canUpload}
        canRetry={canRetry}
        canDelete={canDelete}
        canGovern={canDelete}
      />
    </>
  );
}
