import { Skeleton } from "@/components/Skeleton";

export default function ProtectedLoading() {
  return (
    <section className="panel">
      <Skeleton width="40%" height="1.25rem" />
      <div style={{ marginTop: "1rem", display: "flex", flexDirection: "column", gap: "0.6rem" }}>
        <Skeleton width="100%" />
        <Skeleton width="100%" />
        <Skeleton width="70%" />
      </div>
    </section>
  );
}
