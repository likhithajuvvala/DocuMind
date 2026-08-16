"use client";

import { ErrorPanel } from "@/components/ErrorPanel";

// Only fires if the root layout itself throws — everything else is caught by app/error.tsx or
// (protected)/error.tsx, which render inside the root layout and so keep the header/nav visible.
// This one replaces the root layout entirely, so it has to define its own <html>/<body>.
export default function GlobalError({
  error,
  retry
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  return (
    <html lang="en">
      <body>
        <main className="app-main">
          <ErrorPanel error={error} retry={retry} title="DocuMind hit an unexpected error" />
        </main>
      </body>
    </html>
  );
}
