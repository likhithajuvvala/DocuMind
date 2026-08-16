"use client";

import { useEffect, useState } from "react";
import { CitationList } from "@/components/CitationList";
import { Skeleton } from "@/components/Skeleton";
import { chatHistory, createChatSession, listChatSessions, listDocuments } from "@/lib/apiClient";
import { streamAnswer } from "@/lib/answerStream";
import type { ChatMessage, ChatSession, Citation, DocumentSummary } from "@/lib/types";

export default function ChatPage() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [scopedDocumentId, setScopedDocumentId] = useState<string>("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState("");
  const [streamingAnswer, setStreamingAnswer] = useState("");
  const [streamingCitations, setStreamingCitations] = useState<Citation[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadingWorkspace, setLoadingWorkspace] = useState(true);

  useEffect(() => {
    void (async () => {
      try {
        const [existingSessions, documentPage] = await Promise.all([listChatSessions(), listDocuments()]);
        setSessions(existingSessions);
        setDocuments(documentPage.content);
        if (existingSessions.length > 0) {
          setActiveSessionId(existingSessions[0].id);
        }
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "Unable to load the chat workspace");
      } finally {
        setLoadingWorkspace(false);
      }
    })();
  }, []);

  useEffect(() => {
    if (!activeSessionId) {
      setMessages([]);
      return;
    }
    void chatHistory(activeSessionId).then(setMessages).catch(() => setMessages([]));
  }, [activeSessionId]);

  async function handleNewSession() {
    try {
      const session = await createChatSession(scopedDocumentId || undefined);
      setSessions((current) => [session, ...current]);
      setActiveSessionId(session.id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to start a session");
    }
  }

  async function handleAsk(event: React.FormEvent) {
    event.preventDefault();
    if (!activeSessionId || question.trim().length === 0) {
      return;
    }

    const askedQuestion = question.trim();
    setQuestion("");
    setStreaming(true);
    setStreamingAnswer("");
    setStreamingCitations([]);
    setError(null);
    appendLocalMessage("USER", askedQuestion, []);

    await streamAnswer(activeSessionId, askedQuestion, {
      onToken: (text) => setStreamingAnswer((current) => current + text),
      onCitations: setStreamingCitations,
      onCompleted: () => {
        setStreaming(false);
        void chatHistory(activeSessionId).then(setMessages);
        setStreamingAnswer("");
        setStreamingCitations([]);
      },
      onFailed: (reason) => {
        setStreaming(false);
        setError(reason);
      }
    });
  }

  function appendLocalMessage(role: ChatMessage["role"], content: string, citations: Citation[]) {
    setMessages((current) => [
      ...current,
      { id: `local-${current.length}`, role, content, citations, createdAt: new Date().toISOString() }
    ]);
  }

  return (
    <>
      <section className="panel">
        <h2>Conversations</h2>
        {loadingWorkspace ? (
          <div className="field">
            <Skeleton width="30%" height="0.85rem" />
            <Skeleton width="100%" height="2.4rem" />
            <Skeleton width="100%" height="2.4rem" />
          </div>
        ) : (
          <>
            <div className="field">
              <label htmlFor="session">Active session</label>
              <select
                id="session"
                value={activeSessionId ?? ""}
                onChange={(event) => setActiveSessionId(event.target.value || null)}
              >
                <option value="">Select a session</option>
                {sessions.map((session) => (
                  <option key={session.id} value={session.id}>
                    {session.title}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="scope">Scope to a document</label>
              <select
                id="scope"
                value={scopedDocumentId}
                onChange={(event) => setScopedDocumentId(event.target.value)}
              >
                <option value="">All workspace documents</option>
                {documents.map((document) => (
                  <option key={document.id} value={document.id}>
                    {document.filename}
                  </option>
                ))}
              </select>
            </div>
            <button type="button" onClick={handleNewSession}>
              Start a new session
            </button>
          </>
        )}
      </section>

      <section className="panel">
        <h2>Transcript</h2>
        <div className="chat-transcript">
          {messages.map((message) => (
            <article key={message.id} className={`chat-bubble ${message.role.toLowerCase()}`}>
              {message.content}
              <CitationList citations={message.citations} />
            </article>
          ))}
          {streaming && (
            <article className="chat-bubble assistant">
              {streamingAnswer || "Thinking…"}
              <CitationList citations={streamingCitations} />
            </article>
          )}
        </div>

        <form onSubmit={handleAsk}>
          <div className="field">
            <label htmlFor="question">Question</label>
            <textarea
              id="question"
              rows={3}
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="What is the termination clause in the vendor agreement?"
            />
          </div>
          {error && <p className="error-text">{error}</p>}
          <button type="submit" disabled={streaming || !activeSessionId}>
            {streaming ? "Answering…" : "Ask"}
          </button>
        </form>
      </section>
    </>
  );
}
