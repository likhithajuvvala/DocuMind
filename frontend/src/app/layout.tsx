import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
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
            DocuMind
          </Link>
          <nav className="app-nav">
            <Link href="/documents">Documents</Link>
            <Link href="/chat">Chat</Link>
            <Link href="/admin">Admin</Link>
            <Link href="/login">Sign in</Link>
          </nav>
        </header>
        <main className="app-main">{children}</main>
      </body>
    </html>
  );
}
