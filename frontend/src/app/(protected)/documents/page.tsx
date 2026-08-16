"use client";

import { useCallback, useEffect, useState } from "react";
import { listDocuments, uploadDocument } from "@/lib/apiClient";
import { Skeleton } from "@/components/Skeleton";
import type { DocumentSummary } from "@/lib/types";

const REFRESH_INTERVAL_MS = 4000;

export default function DocumentsPage() {
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const page = await listDocuments();
      setDocuments(page.content);
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to load documents");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(refresh, REFRESH_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [refresh]);

  async function handleUpload(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    setUploading(true);
    try {
      await uploadDocument(file);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Upload failed");
    } finally {
      setUploading(false);
      event.target.value = "";
    }
  }

  return (
    <>
      <section className="panel">
        <h2>Upload a document</h2>
        <p>PDF, DOCX, and plain text files are indexed asynchronously after upload.</p>
        <input type="file" onChange={handleUpload} disabled={uploading} accept=".pdf,.docx,.txt,.md" />
        {uploading && <p>Uploading…</p>}
        {error && <p className="error-text">{error}</p>}
      </section>

      <section className="panel">
        <h2>Workspace documents</h2>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
              <th>Size</th>
              <th>Uploaded</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <DocumentsTableSkeleton />
            ) : (
              <>
                {documents.map((document) => (
                  <tr key={document.id}>
                    <td>{document.filename}</td>
                    <td>
                      <span className="status-pill">{document.status}</span>
                    </td>
                    <td>{formatSize(document.sizeBytes)}</td>
                    <td>{new Date(document.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
                {documents.length === 0 && (
                  <tr>
                    <td colSpan={4}>No documents uploaded yet.</td>
                  </tr>
                )}
              </>
            )}
          </tbody>
        </table>
      </section>
    </>
  );
}

function DocumentsTableSkeleton() {
  return (
    <>
      {Array.from({ length: 3 }).map((_, index) => (
        <tr key={`skeleton-${index}`}>
          <td>
            <Skeleton width="70%" />
          </td>
          <td>
            <Skeleton width="4rem" />
          </td>
          <td>
            <Skeleton width="3rem" />
          </td>
          <td>
            <Skeleton width="6rem" />
          </td>
        </tr>
      ))}
    </>
  );
}

function formatSize(bytes: number): string {
  const megabytes = bytes / (1024 * 1024);
  return megabytes >= 1 ? `${megabytes.toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}
