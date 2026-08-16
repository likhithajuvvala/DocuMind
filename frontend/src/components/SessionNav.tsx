"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { logout } from "@/lib/apiClient";
import { readSession } from "@/lib/session";

export function SessionNav() {
  const router = useRouter();
  const pathname = usePathname();
  const [email, setEmail] = useState<string | null>(null);
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    setEmail(readSession()?.email ?? null);
  }, [pathname]);

  async function handleSignOut() {
    setSigningOut(true);
    await logout();
    setEmail(null);
    setSigningOut(false);
    router.replace("/login");
  }

  if (!email) {
    return <Link href="/login">Sign in</Link>;
  }

  return (
    <>
      <span className="session-email">{email}</span>
      <button type="button" className="link-button" disabled={signingOut} onClick={handleSignOut}>
        {signingOut ? "Signing out…" : "Sign out"}
      </button>
    </>
  );
}
