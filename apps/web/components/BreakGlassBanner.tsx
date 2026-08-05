"use client";

import { AlertIcon } from "@/components/Icons";

export function BreakGlassBanner({ expiresAt }: { expiresAt: number }) {
  return (
    <div className="breakGlassBanner" role="alert">
      <AlertIcon />
      <div>
        <strong>Emergency access is active</strong>
        <span>
          Access is still limited by your clinical roles. Every authorized action is specially
          tagged in the append-only audit trail. This session expires at{" "}
          {new Date(expiresAt).toLocaleTimeString([], {
            hour: "numeric",
            minute: "2-digit",
          })}.
        </span>
      </div>
    </div>
  );
}
