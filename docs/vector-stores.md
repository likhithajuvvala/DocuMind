# Choosing a vector store

DocuMind runs against either pgvector or Qdrant. Application code never names a store — `ChunkIndexer` and `ChunkRetriever` depend on Spring AI's `VectorStore` interface — so switching is configuration only.

## Switching

```
VECTOR_STORE=pgvector      # default, no extra infrastructure
VECTOR_STORE=qdrant        # dedicated vector database
```

Qdrant additionally reads:

```
QDRANT_HOST=qdrant
QDRANT_PORT=6334
QDRANT_COLLECTION=documind
QDRANT_API_KEY=
QDRANT_USE_TLS=false
```

**The port is the gRPC port, 6334, not the 6333 REST port** you use for `curl`. Spring AI's Qdrant client speaks gRPC, so pointing it at 6333 fails to connect.

## Why the selection must be explicit

Both starters are on the classpath, and each auto-configuration is annotated `@ConditionalOnProperty(name = "spring.ai.vectorstore.type", …, matchIfMissing = true)`. With the property unset, **both** activate and which one supplies the `VectorStore` bean is arbitrary. Every service therefore sets `spring.ai.vectorstore.type` explicitly from `VECTOR_STORE`, defaulting to pgvector.

## Only the writer creates the collection

`ingestion-worker` runs with `initialize-schema: true`; `query-service` runs with `false`. When both were allowed to initialize, they raced on startup and the loser died with:

```
io.grpc.StatusRuntimeException: ALREADY_EXISTS: Wrong input: Collection `documind` already exists!
```

Spring AI checks for the collection before creating it, but the check and the create are not atomic. Keeping creation on the single writer matches how pgvector is already configured. Running more than one `ingestion-worker` replica against an uninitialised collection can still race; create the collection ahead of time in that case.

## Dimensions differ between the two

| | pgvector | Qdrant |
|---|---|---|
| Collection sizing | `EMBEDDING_DIMENSIONS` must match the model | taken from the embedding model automatically |
| Mismatch symptom | every write fails, documents dead-letter | not applicable |
| Startup guard | dimension check refuses to start | skipped, and logged as skipped |

With Qdrant the `EMBEDDING_DIMENSIONS` trap disappears: switching to `nomic-embed-text` created a 768-dimension collection with no extra configuration. The pgvector dimension guard is deliberately skipped when Qdrant is active, since comparing the model against a pgvector-only property would refuse startup for no reason.

Note that the two stores hold separate copies of the index. Switching does not migrate anything — documents indexed under one store must be re-ingested to appear in the other.

## Verified on both stores

Same demo corpus, same question, same embedding model:

| | pgvector | Qdrant |
|---|---|---|
| Ingestion | 3 documents indexed | 3 documents, 11 points |
| Answer | correct, cited | identical answer |
| Top relevance | 0.738 / 0.702 | 0.738 / 0.702 |
| Cross-tenant probe | "not found" | "not found" |

Workspace filtering is expressed as a Spring AI `Filter.Expression`, which each store translates into its own dialect, so tenant isolation holds on both.
