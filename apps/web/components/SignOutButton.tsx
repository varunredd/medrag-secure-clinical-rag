"use client";

import { useState } from "react";

function navigateAway(path: string) {
  window.location.href = /^https?:\/\//i.test(path)
    ? path
    : new URL(path, window.location.origin).toString();
}

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
        navigateAway("/");
        return;
      }

      const body = (await response.json().catch(() => null)) as {
        redirectTo?: string;
      } | null;
      navigateAway(body?.redirectTo || "/");
    } catch {
      navigateAway("/");
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
