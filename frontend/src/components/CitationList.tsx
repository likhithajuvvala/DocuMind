import type { Citation } from "@/lib/types";

export function CitationList({ citations }: { citations: Citation[] }) {
  if (citations.length === 0) {
    return null;
  }

  return (
    <ol className="citation-list">
      {citations.map((citation) => (
        <li key={`${citation.documentId}-${citation.reference}`}>
          [{citation.reference}] {citation.documentName}
          {citation.pageNumber !== null ? `, page ${citation.pageNumber}` : ""}
        </li>
      ))}
    </ol>
  );
}
