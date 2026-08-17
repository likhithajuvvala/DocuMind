import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import { NavLinks } from "@/components/NavLinks";
import { SessionNav } from "@/components/SessionNav";
import "./globals.css";

export const metadata: Metadata = {
  title: "DocuMind",
  description: "Ask questions about your documents and get cited, grounded answers"
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <header className="app-header">
          <Link href="/" className="app-brand">
            <span className="app-brand-mark">D</span>
            DocuMind
          </Link>
          <nav className="app-nav">
            <NavLinks />
            <div className="app-nav-session">
              <SessionNav />
            </div>
          </nav>
        </header>
        <main className="app-main">{children}</main>
      </body>
    </html>
  );
}
