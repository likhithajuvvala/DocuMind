import { describe, expect, it } from "vitest";
import { parseStreamFrame } from "@/lib/answerStream";

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
