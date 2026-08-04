"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { hasSession } from "@/lib/session";

type GuardState = "checking" | "allowed" | "denied";

export function RequireSession({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [state, setState] = useState<GuardState>("checking");

  useEffect(() => {
    if (hasSession()) {
      setState("allowed");
      return;
    }
    setState("denied");
    router.replace("/login");
  }, [router]);

  if (state === "allowed") {
    return <>{children}</>;
  }

  return (
    <section className="panel">
      <p>{state === "checking" ? "Checking your session…" : "Redirecting to sign in…"}</p>
    </section>
  );
}
