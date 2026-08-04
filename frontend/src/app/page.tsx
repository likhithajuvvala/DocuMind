import Link from "next/link";

export default function HomePage() {
  return (
    <section className="panel">
      <h1>Ask your documents directly</h1>
      <p>
        Upload contracts, reports, and internal knowledge bases, then ask questions in natural
        language. Every answer is grounded in retrieved passages and cites the source document and
        page it came from.
      </p>
      <p>
        Start by <Link href="/documents">uploading a document</Link>, then open the{" "}
        <Link href="/chat">chat workspace</Link>.
      </p>
    </section>
  );
}
