"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { clearSession, readSession } from "@/lib/session";

export function SessionNav() {
  const router = useRouter();
  const [email, setEmail] = useState<string | null>(null);

  useEffect(() => {
    setEmail(readSession()?.email ?? null);
  }, []);

  function handleSignOut() {
    clearSession();
    setEmail(null);
    router.replace("/login");
  }

  if (!email) {
    return <Link href="/login">Sign in</Link>;
  }

  return (
    <>
      <span className="session-email">{email}</span>
      <button type="button" className="link-button" onClick={handleSignOut}>
        Sign out
      </button>
    </>
  );
}
