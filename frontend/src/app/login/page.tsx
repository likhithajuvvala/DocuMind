"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { login, register } from "@/lib/apiClient";
import { storeSession } from "@/lib/session";

type Mode = "login" | "register";

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [workspaceName, setWorkspaceName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      const session =
        mode === "login"
          ? await login(email, password)
          : await register(email, password, workspaceName);
      storeSession(session);
      router.push("/documents");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Sign in failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="panel">
      <h2>{mode === "login" ? "Sign in" : "Create a workspace"}</h2>
      <form onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>
        {mode === "register" && (
          <div className="field">
            <label htmlFor="workspace">Workspace name</label>
            <input
              id="workspace"
              value={workspaceName}
              onChange={(event) => setWorkspaceName(event.target.value)}
              required
            />
          </div>
        )}
        {error && <p className="error-text">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? "Working…" : mode === "login" ? "Sign in" : "Create workspace"}
        </button>
      </form>
      <p>
        <button type="button" onClick={() => setMode(mode === "login" ? "register" : "login")}>
          {mode === "login" ? "Need a workspace?" : "Already registered?"}
        </button>
      </p>
    </section>
  );
}
