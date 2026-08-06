# Running DocuMind without a hosted LLM

DocuMind runs end to end against a local Ollama instance, with no API key and no data leaving the machine. This was verified on Fedora with `nomic-embed-text` for embeddings and `qwen3:14b` for chat.

## 1. Pull the models

```bash
ollama pull nomic-embed-text
ollama pull qwen3:14b
```

Any chat model works. The embedding model matters more, because its output dimension has to match the vector store column.

## 2. Let the containers reach Ollama

Ollama listens on `127.0.0.1` by default, which containers cannot reach. Bind it to all interfaces:

```bash
sudo systemctl edit ollama
# [Service]
# Environment="OLLAMA_HOST=0.0.0.0"
sudo systemctl restart ollama
```

The Compose file maps `host.docker.internal` to the host gateway, which is required on Linux and already configured for the services that call Ollama.

## 3. Set the provider and the embedding dimension

```
LLM_PROVIDER=ollama
EMBEDDING_PROVIDER=ollama
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_CHAT_MODEL=qwen3:14b
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
EMBEDDING_DIMENSIONS=768
```

`EMBEDDING_DIMENSIONS` is the setting most likely to bite. `nomic-embed-text` produces 768 values per vector while the OpenAI default is 1536, and the `vector_store` table is created once with whatever dimension is configured on first startup.

`ingestion-worker` and `query-service` now check this on startup and refuse to run rather than corrupting a batch of uploads one document at a time:

```
com.documind.common.rag.EmbeddingDimensionMismatchException:
The vector_store table stores 768-dimension vectors but this service is configured for 1536.
Every embedding write would fail with "expected 768 dimensions, not 1536".
Either set EMBEDDING_DIMENSIONS=768 to match the existing data, or drop the table and re-index:
  drop table vector_store; truncate document_chunks;
```

The check compares the configured dimension against the existing table and, when the provider is reachable, against the dimension the model actually returns. An unreachable provider only logs a warning, so a briefly offline Ollama does not stop the service from booting. Set `documind.rag.validate-dimensions=false` to disable it.

Switching embedding model or provider therefore means recreating the table and re-indexing:

```sql
drop table if exists vector_store;
truncate document_chunks;
```

## 4. Tune retrieval for the model

Cosine similarity scores are not comparable across embedding models. With `nomic-embed-text`, a well-matched chunk scored 0.74 against a question whose answer it contained, close enough to the default `0.7` cut-off that a slightly different phrasing returns "The answer was not found in your documents."

Both knobs are configurable, so lower the threshold rather than accepting silent misses:

```
RETRIEVAL_SIMILARITY_THRESHOLD=0.55
RETRIEVAL_TOP_K=6
```

Raising it makes the guardrail stricter; lowering it admits weaker matches.

You no longer have to guess. When every candidate is rejected, the retriever logs the score it would have needed:

```
No chunk cleared the similarity threshold 0.7 for workspace dda29216-….
The closest was 0.633 in employee-handbook.md at 227ac9d9-….
Lower documind.retrieval.similarity-threshold if answers are being missed.
```

That case was a genuine false negative: the handbook does say "twenty-five (25) days of paid annual leave", but at 0.633 it lost to a 0.7 threshold and the user saw only "The answer was not found in your documents." Re-running the same question at 0.55 answered it correctly with citations.

Three outcomes are distinguished, both in the log and in the `documind.retrieval.results` counter, so an empty workspace never looks like a tuning problem:

| Outcome | Meaning |
|---|---|
| `grounded` | chunks cleared the threshold and were sent to the model |
| `below_threshold` | the workspace held a near match that the threshold rejected |
| `no_documents` | nothing indexed for that workspace at all |

The **Retrieval outcomes** and **Closest chunk similarity** panels on the DocuMind Overview dashboard show both, so a threshold that is too strict shows up as a rising `below_threshold` rate rather than as silent user frustration. To pick a value, compare the p50/p95 of the closest-chunk similarity against your threshold, or measure directly:

```sql
select metadata->>'document_name', 1 - (embedding <=> '[...]') as similarity
from vector_store order by embedding <=> '[...]' limit 5;
```

## What was verified on this stack

Upload through the API, asynchronous ingestion into pgvector, streamed answers with citations, the no-answer guardrail, workspace isolation, usage accounting, and the Grafana dashboard — all with no external API calls.
