"use client";

import { useState } from "react";

export function SignOutButton() {
  const [busy, setBusy] = useState(false);

  async function signOut() {
    if (busy) {
      return;
    }
    setBusy(true);
    try {
      const response = await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          Accept: "application/json",
          "x-medrag-client": "1",
        },
      });

      if (!response.ok) {
        window.location.href = "/";
        return;
      }

      const body = (await response.json().catch(() => null)) as {
        redirectTo?: string;
      } | null;
      window.location.href = body?.redirectTo || "/";
    } catch {
      window.location.href = "/";
    }
  }

  return (
    <button
      type="button"
      className="textButton"
      disabled={busy}
      onClick={() => void signOut()}
    >
      {busy ? "Signing out…" : "Sign out"}
    </button>
  );
}
