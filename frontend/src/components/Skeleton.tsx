export function Skeleton({
  width,
  height = "1rem",
  className = ""
}: {
  width?: string;
  height?: string;
  className?: string;
}) {
  return <span className={`skeleton ${className}`.trim()} style={{ width, height }} aria-hidden="true" />;
}
