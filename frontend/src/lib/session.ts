import type { AuthenticationResult } from "@/lib/types";

const SESSION_STORAGE_KEY = "documind.session";

export function storeSession(session: AuthenticationResult): void {
  window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
}

export function readSession(): AuthenticationResult | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(SESSION_STORAGE_KEY);
  return raw ? (JSON.parse(raw) as AuthenticationResult) : null;
}

export function clearSession(): void {
  window.localStorage.removeItem(SESSION_STORAGE_KEY);
}

export function readAccessToken(): string | null {
  return readSession()?.accessToken ?? null;
}
