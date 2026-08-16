import { clearSession, readAccessToken, readRefreshToken, storeSession } from "@/lib/session";
import type {
  AuthenticationResult,
  ChatMessage,
  ChatSession,
  DocumentStatusDetail,
  DocumentSummary,
  IngestionHealth,
  Page,
  WorkspaceUsage
} from "@/lib/types";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, init: RequestInit = {}, allowRetry = true): Promise<T> {
  const response = await send(path, init);

  if (response.status === 401 && allowRetry && (await refreshSession())) {
    return request<T>(path, init, false);
  }

  if (response.status === 401) {
    clearSession();
    throw new ApiError(401, "Your session has expired, please sign in again");
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const token = readAccessToken();
  const headers = new Headers(init.headers);
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  if (init.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  return fetch(`${API_BASE_URL}${path}`, { ...init, headers });
}

export async function refreshSession(): Promise<boolean> {
  const refreshToken = readRefreshToken();
  if (!refreshToken) {
    return false;
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken })
  });

  if (!response.ok) {
    return false;
  }

  storeSession((await response.json()) as AuthenticationResult);
  return true;
}

/**
 * Revokes the current refresh token server-side so it cannot be used again, then clears local
 * state. Best-effort: if the network call fails, the session is still cleared locally, because
 * the user's intent to sign out of this browser must not depend on connectivity. The revocation
 * itself is what makes sign-out mean something beyond this tab; without it the refresh token
 * would remain valid until it naturally expired.
 */
export async function logout(): Promise<void> {
  const refreshToken = readRefreshToken();
  if (refreshToken) {
    try {
      await fetch(`${API_BASE_URL}/api/auth/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken })
      });
    } catch {
      // Network failure signing out is not actionable by the user; local state still clears below.
    }
  }
  clearSession();
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    return typeof body?.message === "string" ? body.message : response.statusText;
  } catch {
    return response.statusText;
  }
}

export function register(
  email: string,
  password: string,
  workspaceName: string
): Promise<AuthenticationResult> {
  return request<AuthenticationResult>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ email, password, workspaceName })
  });
}

export function login(email: string, password: string): Promise<AuthenticationResult> {
  return request<AuthenticationResult>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password })
  });
}

export function uploadDocument(file: File): Promise<DocumentSummary> {
  const form = new FormData();
  form.append("file", file);
  return request<DocumentSummary>("/api/documents/upload", { method: "POST", body: form });
}

export function listDocuments(page = 0, size = 20): Promise<Page<DocumentSummary>> {
  return request<Page<DocumentSummary>>(`/api/documents?page=${page}&size=${size}`);
}

export function documentStatus(documentId: string): Promise<DocumentStatusDetail> {
  return request<DocumentStatusDetail>(`/api/documents/${documentId}/status`);
}

export function createChatSession(documentId?: string, title?: string): Promise<ChatSession> {
  return request<ChatSession>("/api/chat/sessions", {
    method: "POST",
    body: JSON.stringify({ documentId: documentId ?? null, title: title ?? null })
  });
}

export function listChatSessions(): Promise<ChatSession[]> {
  return request<ChatSession[]>("/api/chat/sessions");
}

export function chatHistory(sessionId: string): Promise<ChatMessage[]> {
  return request<ChatMessage[]>(`/api/chat/sessions/${sessionId}`);
}

export function workspaceUsage(windowDays = 30): Promise<WorkspaceUsage> {
  return request<WorkspaceUsage>(`/api/admin/usage?windowDays=${windowDays}`);
}

export function ingestionHealth(): Promise<IngestionHealth> {
  return request<IngestionHealth>("/api/admin/documents/status");
}

export function messageStreamUrl(sessionId: string): string {
  return `${API_BASE_URL}/api/chat/sessions/${sessionId}/messages`;
}
