"use client";

import { ErrorPanel } from "@/components/ErrorPanel";

export default function ProtectedError({
  error,
  retry
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  return <ErrorPanel error={error} retry={retry} title="This page couldn't load" />;
}
