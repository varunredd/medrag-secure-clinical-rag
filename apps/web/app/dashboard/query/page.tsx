import { redirect } from "next/navigation";

import { QueryClient } from "@/components/QueryClient";
import { authenticatedSession } from "@/lib/backend";

export default async function QueryPage() {
  const current = await authenticatedSession();
  if (
    !current?.session.user.roles.some((role) =>
      ["DOCTOR", "NURSE"].includes(role),
    )
  ) {
    redirect("/dashboard");
  }

  return (
    <>
      <header className="pageHeader operationalHeader">
        <div>
          <p className="eyebrow">EVIDENCE-SCOPED REVIEW</p>
          <h1>Ask MedRAG</h1>
          <p>
            Select the exact clinical records first, then review every generated statement against its source evidence.
          </p>
        </div>
      </header>
      <QueryClient />
    </>
  );
}
