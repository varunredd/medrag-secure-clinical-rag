import Link from "next/link";
import { redirect } from "next/navigation";

import { BreakGlassBanner } from "@/components/BreakGlassBanner";
import { SessionExpiryBanner } from "@/components/SessionExpiryBanner";
import { SidebarNav } from "@/components/SidebarNav";
import { SignOutButton } from "@/components/SignOutButton";
import { authenticatedSession } from "@/lib/backend";

export default async function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const current = await authenticatedSession();
  if (!current) {
    redirect("/api/auth/login?returnTo=/dashboard&reason=session_expired");
  }

  const roles = current.session.user.roles;
  const canQuery = roles.some((role) => ["DOCTOR", "NURSE"].includes(role));
  const canAudit = roles.some((role) =>
    ["AUDITOR", "CLINIC_ADMIN"].includes(role),
  );
  const canAdmin = roles.includes("CLINIC_ADMIN");
  const primaryRole = roles.find((role) =>
    ["DOCTOR", "NURSE", "CLINIC_ADMIN", "AUDITOR"].includes(role),
  );

  return (
    <div className="shell">
      <aside className="sidebar">
        <Link href="/dashboard" className="brand sideBrand" aria-label="MedRAG overview">
          <span className="brandMark">M</span>
          <span>
            <strong>MedRAG</strong>
            <small>Clinical workspace</small>
          </span>
        </Link>
        <SidebarNav
          canQuery={canQuery}
          canAudit={canAudit}
          canAdmin={canAdmin}
        />
        <div className="trustBoundary">
          <span className="trustDot" />
          <div>
            <strong>Private evidence boundary</strong>
            <small>Tenant-scoped retrieval enforced</small>
          </div>
        </div>
        <div className="profile">
          <div className="avatar" aria-hidden="true">
            {(current.session.user.name ?? current.session.user.email ?? "C")
              .slice(0, 1)
              .toUpperCase()}
          </div>
          <div className="profileText">
            <strong>
              {current.session.user.name ??
                current.session.user.email ??
                "Clinical user"}
            </strong>
            <span>{primaryRole?.replaceAll("_", " ") ?? "Clinical user"}</span>
            <small>{current.session.user.tenantId}</small>
          </div>
          <SignOutButton />
        </div>
      </aside>
      <div className="mainColumn">
        {current.session.user.breakGlass && (
          <BreakGlassBanner expiresAt={current.session.user.breakGlass.expiresAt} />
        )}
        <SessionExpiryBanner expiresAt={current.session.refreshExpiresAt} />
        <main className="content">{children}</main>
      </div>
    </div>
  );
}
