"use client";

import { useEffect, useState } from "react";

export function SessionExpiryBanner({ expiresAt }: { expiresAt: number }) {
  const [remaining, setRemaining] = useState(expiresAt - Date.now());

  useEffect(() => {
    const timer = window.setInterval(
      () => setRemaining(expiresAt - Date.now()),
      15_000,
    );
    return () => window.clearInterval(timer);
  }, [expiresAt]);

  if (remaining > 5 * 60_000) {
    return null;
  }

  const minutes = Math.max(0, Math.ceil(remaining / 60_000));
  return (
    <div className="sessionBanner" role="status">
      <strong>Session ending soon.</strong>
      <span>
        {minutes > 0
          ? ` Re-authentication will be required in about ${minutes} minute${minutes === 1 ? "" : "s"}.`
          : " Re-authentication is required to continue."}
      </span>
      <a
        href={`/api/auth/login?reason=session_expired&returnTo=${encodeURIComponent("/dashboard")}`}
      >
        Re-authenticate
      </a>
    </div>
  );
}
