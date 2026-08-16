"use client";

import { useEffect } from "react";

interface ErrorPanelProps {
  error: Error & { digest?: string };
  retry: () => void;
  title?: string;
}

export function ErrorPanel({ error, retry, title = "Something went wrong" }: ErrorPanelProps) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <section className="panel error-panel">
      <h2>{title}</h2>
      <p className="error-text">{error.message || "An unexpected error occurred."}</p>
      {error.digest && <p className="error-digest">Reference: {error.digest}</p>}
      <button type="button" onClick={retry}>
        Try again
      </button>
    </section>
  );
}
