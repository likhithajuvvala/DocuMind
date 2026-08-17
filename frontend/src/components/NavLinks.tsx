"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/documents", label: "Documents" },
  { href: "/chat", label: "Chat" },
  { href: "/admin", label: "Admin" }
];

export function NavLinks() {
  const pathname = usePathname();

  return (
    <div className="app-nav-links">
      {LINKS.map((link) => (
        <Link key={link.href} href={link.href} className={pathname.startsWith(link.href) ? "active" : undefined}>
          {link.label}
        </Link>
      ))}
    </div>
  );
}
