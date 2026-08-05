"use client";

import { useEffect, useState } from "react";

export function SessionExpiryBanner({ expiresAt }: { expiresAt: number }) {
  const [remaining, setRemaining] = useState<number | null>(null);

  useEffect(() => {
    const tick = () => setRemaining(expiresAt - Date.now());
    const initial = window.setTimeout(tick, 0);
    const timer = window.setInterval(tick, 15_000);
    return () => {
      window.clearTimeout(initial);
      window.clearInterval(timer);
    };
  }, [expiresAt]);

  if (remaining === null || remaining > 5 * 60_000) {
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
