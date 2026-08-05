"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";

import {
  AskIcon,
  AuditIcon,
  DocumentIcon,
  OverviewIcon,
  SettingsIcon,
} from "@/components/Icons";
import { clinicalFetch } from "@/lib/clinical-fetch";

type Counts = { clinicName?: string; documents?: { total: number; failed: number }; pipeline?: { dead: number } };

export function SidebarNav({
  canQuery,
  canAudit,
  canAdmin,
}: {
  canQuery: boolean;
  canAudit: boolean;
  canAdmin: boolean;
}) {
  const pathname = usePathname();
  const [counts, setCounts] = useState<Counts>({});

  useEffect(() => {
    let active = true;
    const load = async () => {
      const response = await clinicalFetch(
        "/api/backend/api/v1/operations/overview",
      ).catch(() => null);
      if (active && response?.ok) {
        setCounts((await response.json()) as Counts);
      }
    };
    void load();
    const timer = window.setInterval(() => void load(), 20_000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  const links = [
    {
      href: "/dashboard",
      label: "Overview",
      icon: OverviewIcon,
      show: true,
    },
    {
      href: "/dashboard/documents",
      label: "Clinical records",
      icon: DocumentIcon,
      show: true,
      badge: counts.documents?.failed || counts.documents?.total,
      alert: Boolean(counts.documents?.failed),
    },
    {
      href: "/dashboard/query",
      label: "Ask MedRAG",
      icon: AskIcon,
      show: canQuery,
    },
    {
      href: "/dashboard/audit",
      label: "Audit trail",
      icon: AuditIcon,
      show: canAudit,
    },
    {
      href: "/dashboard/operations",
      label: "Platform readiness",
      icon: OverviewIcon,
      show: canAudit,
      badge: counts.pipeline?.dead,
      alert: Boolean(counts.pipeline?.dead),
    },
    {
      href: "/dashboard/settings",
      label: "Clinic settings",
      icon: SettingsIcon,
      show: canAdmin,
      badge: counts.pipeline?.dead,
      alert: Boolean(counts.pipeline?.dead),
    },
  ];

  return (
    <nav aria-label="Clinical workspace">
      <span className="navSectionLabel">{counts.clinicName || "Workspace"}</span>
      {links
        .filter((link) => link.show)
        .map((link) => {
          const active =
            link.href === "/dashboard"
              ? pathname === link.href
              : pathname.startsWith(link.href);
          const Icon = link.icon;
          return (
            <Link
              key={link.href}
              href={link.href}
              className={active ? "active" : ""}
              aria-current={active ? "page" : undefined}
            >
              <Icon className="navIcon" />
              <span>{link.label}</span>
              {Boolean(link.badge) && (
                <span className={link.alert ? "navBadge alert" : "navBadge"}>
                  {link.badge}
                </span>
              )}
            </Link>
          );
        })}
    </nav>
  );
}
