import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { parseStreamFrame, streamAnswer } from "@/lib/answerStream";
import type { Citation } from "@/lib/types";

vi.mock("@/lib/apiClient", () => ({
  messageStreamUrl: (sessionId: string) => `http://test.local/api/chat/sessions/${sessionId}/messages`,
  refreshSession: vi.fn()
}));

vi.mock("@/lib/session", () => ({
  readAccessToken: vi.fn(() => "test-access-token"),
  clearSession: vi.fn()
}));

describe("parseStreamFrame", () => {
  it("reads the event name and payload of a frame", () => {
    const frame = 'event:token\ndata:{"text":"Hello"}';

    expect(parseStreamFrame(frame)).toEqual({ eventName: "token", payload: { text: "Hello" } });
  });

  it("joins payloads split across several data lines", () => {
    const frame = 'event:citations\ndata:{"citations":\ndata:[]}';

    expect(parseStreamFrame(frame)).toEqual({ eventName: "citations", payload: { citations: [] } });
  });

  it("ignores frames without an event name or payload", () => {
    expect(parseStreamFrame(":heartbeat")).toBeNull();
    expect(parseStreamFrame("event:token")).toBeNull();
  });
});

describe("streamAnswer", () => {
  function sseResponse(chunks: string[], status = 200): Response {
    const encoder = new TextEncoder();
    const body =
      status === 200
        ? new ReadableStream<Uint8Array>({
            start(controller) {
              chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
              controller.close();
            }
          })
        : null;
    return new Response(body, { status });
  }

  function handlers() {
    return {
      onToken: vi.fn(),
      onCitations: vi.fn(),
      onCompleted: vi.fn(),
      onFailed: vi.fn()
    };
  }

  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("dispatches tokens, citations, and completion for a normal answer", async () => {
    const citations: Citation[] = [
      { reference: 1, documentId: "d1", documentName: "contract.pdf", pageNumber: 3, relevance: 0.92 }
    ];
    fetchMock.mockResolvedValueOnce(
      sseResponse([
        'event:token\ndata:{"text":"The termination "}\n\n',
        'event:token\ndata:{"text":"clause is..."}\n\n',
        `event:citations\ndata:${JSON.stringify({ citations })}\n\n`,
        'event:completed\ndata:{"messageId":"m-1","tokenCount":42}\n\n'
      ])
    );
    const h = handlers();

    await streamAnswer("session-1", "What is the termination clause?", h);

    expect(h.onToken).toHaveBeenNthCalledWith(1, "The termination ");
    expect(h.onToken).toHaveBeenNthCalledWith(2, "clause is...");
    expect(h.onCitations).toHaveBeenCalledWith(citations);
    expect(h.onCompleted).toHaveBeenCalledWith("m-1", 42);
    expect(h.onFailed).not.toHaveBeenCalled();
  });

  it("reassembles a single SSE frame split across multiple stream chunks", async () => {
    // The frame arrives byte-by-chunk in three separate reader.read() calls, exercising the
    // buffer-accumulation logic rather than the happy path of one frame per chunk.
    fetchMock.mockResolvedValueOnce(sseResponse(['event:tok', 'en\ndata:{"te', 'xt":"partial"}\n\n']));
    const h = handlers();

    await streamAnswer("session-1", "question", h);

    expect(h.onToken).toHaveBeenCalledTimes(1);
    expect(h.onToken).toHaveBeenCalledWith("partial");
  });

  it("dispatches multiple frames delivered in a single chunk", async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(['event:token\ndata:{"text":"a"}\n\nevent:token\ndata:{"text":"b"}\n\n'])
    );
    const h = handlers();

    await streamAnswer("session-1", "question", h);

    expect(h.onToken).toHaveBeenNthCalledWith(1, "a");
    expect(h.onToken).toHaveBeenNthCalledWith(2, "b");
  });

  it("retries once after a 401 by refreshing the session, then succeeds", async () => {
    const { refreshSession } = await import("@/lib/apiClient");
    vi.mocked(refreshSession).mockResolvedValueOnce(true);
    fetchMock
      .mockResolvedValueOnce(sseResponse([], 401))
      .mockResolvedValueOnce(sseResponse(['event:completed\ndata:{"messageId":"m-2","tokenCount":5}\n\n']));
    const h = handlers();

    await streamAnswer("session-1", "question", h);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(h.onCompleted).toHaveBeenCalledWith("m-2", 5);
    expect(h.onFailed).not.toHaveBeenCalled();
  });

  it("clears the session and reports failure when refresh fails after a 401", async () => {
    const { refreshSession } = await import("@/lib/apiClient");
    const { clearSession } = await import("@/lib/session");
    vi.mocked(refreshSession).mockResolvedValueOnce(false);
    fetchMock.mockResolvedValueOnce(sseResponse([], 401));
    const h = handlers();

    await streamAnswer("session-1", "question", h);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(clearSession).toHaveBeenCalledTimes(1);
    expect(h.onFailed).toHaveBeenCalledWith("Your session has expired, please sign in again");
    expect(h.onCompleted).not.toHaveBeenCalled();
  });

  it("reports failure for a non-OK response that isn't a 401", async () => {
    fetchMock.mockResolvedValueOnce(sseResponse([], 503));
    const h = handlers();

    await streamAnswer("session-1", "question", h);

    expect(h.onFailed).toHaveBeenCalledWith("The assistant could not be reached (503)");
    expect(h.onCompleted).not.toHaveBeenCalled();
  });

  it("reports failure when the stream itself emits a failed event", async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(['event:failed\ndata:{"reason":"The model provider timed out"}\n\n'])
    );
    const h = handlers();

    await streamAnswer("session-1", "question", h);

    expect(h.onFailed).toHaveBeenCalledWith("The model provider timed out");
    expect(h.onCompleted).not.toHaveBeenCalled();
  });
});
