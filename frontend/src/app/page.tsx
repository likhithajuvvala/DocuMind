import Link from "next/link";

export default function HomePage() {
  return (
    <section className="panel hero">
      <h1>Ask your documents directly</h1>
      <p>
        Upload contracts, reports, and internal knowledge bases, then ask questions in natural
        language. Every answer is grounded in retrieved passages and cites the source document and
        page it came from.
      </p>
      <div className="hero-actions">
        <Link href="/documents" className="btn btn-primary">
          Upload a document
        </Link>
        <Link href="/chat" className="btn btn-secondary">
          Open chat workspace
        </Link>
      </div>
    </section>
  );
}
