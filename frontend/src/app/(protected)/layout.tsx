import type { ReactNode } from "react";
import { RequireSession } from "@/components/RequireSession";

export default function ProtectedLayout({ children }: { children: ReactNode }) {
  return <RequireSession>{children}</RequireSession>;
}
