"use client";

import { useEffect, useState } from "react";
import { ingestionHealth, workspaceUsage } from "@/lib/apiClient";
import { Skeleton } from "@/components/Skeleton";
import type { IngestionHealth, WorkspaceUsage } from "@/lib/types";

export default function AdminPage() {
  const [usage, setUsage] = useState<WorkspaceUsage | null>(null);
  const [health, setHealth] = useState<IngestionHealth | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    void (async () => {
      try {
        const [usageResult, healthResult] = await Promise.all([workspaceUsage(), ingestionHealth()]);
        setUsage(usageResult);
        setHealth(healthResult);
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "Unable to load administration data");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <>
      <section className="panel">
        <h2>Ingestion pipeline</h2>
        {error && <p className="error-text">{error}</p>}
        <div className="metric-grid">
          {loading ? (
            <MetricCardsSkeleton count={4} />
          ) : (
            <>
              <div className="metric-card">
                <span>Pending</span>
                <strong>{health?.pendingDocuments ?? 0}</strong>
              </div>
              <div className="metric-card">
                <span>Processing</span>
                <strong>{health?.processingDocuments ?? 0}</strong>
              </div>
              <div className="metric-card">
                <span>Indexed</span>
                <strong>{health?.indexedDocuments ?? 0}</strong>
              </div>
              <div className="metric-card">
                <span>Failed</span>
                <strong>{health?.failedDocuments ?? 0}</strong>
              </div>
            </>
          )}
        </div>
      </section>

      <section className="panel">
        <h2>Usage over the last 30 days</h2>
        <div className="metric-grid">
          {loading ? (
            <MetricCardsSkeleton count={2} />
          ) : (
            <>
              <div className="metric-card">
                <span>Total tokens</span>
                <strong>{usage?.totalTokens ?? 0}</strong>
              </div>
              <div className="metric-card">
                <span>Estimated cost</span>
                <strong>${Number(usage?.totalCost ?? 0).toFixed(4)}</strong>
              </div>
            </>
          )}
        </div>

        <table>
          <thead>
            <tr>
              <th>User</th>
              <th>Tokens</th>
              <th>Cost</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <UsageTableSkeleton />
            ) : (
              <>
                {(usage?.perUser ?? []).map((row) => (
                  <tr key={row.userId}>
                    <td>{row.userId}</td>
                    <td>{row.totalTokens}</td>
                    <td>${Number(row.totalCost).toFixed(4)}</td>
                  </tr>
                ))}
                {(usage?.perUser ?? []).length === 0 && (
                  <tr>
                    <td colSpan={3}>No usage recorded in this window.</td>
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

function MetricCardsSkeleton({ count }: { count: number }) {
  return (
    <>
      {Array.from({ length: count }).map((_, index) => (
        <div className="metric-card" key={`skeleton-metric-${index}`}>
          <Skeleton width="4rem" height="0.8rem" />
          <Skeleton width="3rem" height="1.5rem" className="metric-skeleton-value" />
        </div>
      ))}
    </>
  );
}

function UsageTableSkeleton() {
  return (
    <>
      {Array.from({ length: 3 }).map((_, index) => (
        <tr key={`skeleton-usage-${index}`}>
          <td>
            <Skeleton width="8rem" />
          </td>
          <td>
            <Skeleton width="4rem" />
          </td>
          <td>
            <Skeleton width="4rem" />
          </td>
        </tr>
      ))}
    </>
  );
}
