import { messageStreamUrl } from "@/lib/apiClient";
import { readAccessToken } from "@/lib/session";
import type { Citation } from "@/lib/types";

interface StreamHandlers {
  onToken: (text: string) => void;
  onCitations: (citations: Citation[]) => void;
  onCompleted: (messageId: string, tokenCount: number) => void;
  onFailed: (reason: string) => void;
}

export async function streamAnswer(
  sessionId: string,
  content: string,
  handlers: StreamHandlers,
  signal?: AbortSignal
): Promise<void> {
  const token = readAccessToken();
  const response = await fetch(messageStreamUrl(sessionId), {
    method: "POST",
    signal,
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ content })
  });

  if (!response.ok || !response.body) {
    handlers.onFailed(`The assistant could not be reached (${response.status})`);
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split("\n\n");
    buffer = frames.pop() ?? "";
    frames.forEach((frame) => dispatchFrame(frame, handlers));
  }
}

export interface StreamFrame {
  eventName: string;
  payload: Record<string, unknown>;
}

export function parseStreamFrame(frame: string): StreamFrame | null {
  const lines = frame.split("\n");
  const eventName = lines.find((line) => line.startsWith("event:"))?.slice("event:".length).trim();
  const data = lines
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice("data:".length).trim())
    .join("");

  if (!eventName || !data) {
    return null;
  }

  return { eventName, payload: JSON.parse(data) };
}

function dispatchFrame(frame: string, handlers: StreamHandlers): void {
  const parsed = parseStreamFrame(frame);
  if (!parsed) {
    return;
  }

  const { eventName, payload } = parsed;
  switch (eventName) {
    case "token":
      handlers.onToken(payload.text as string);
      break;
    case "citations":
      handlers.onCitations(payload.citations as Citation[]);
      break;
    case "completed":
      handlers.onCompleted(payload.messageId as string, payload.tokenCount as number);
      break;
    case "failed":
      handlers.onFailed(payload.reason as string);
      break;
    default:
      break;
  }
}
