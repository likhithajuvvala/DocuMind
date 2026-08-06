# Re-ranking retrieved chunks

Embedding search matches on topic. A passage that discusses a subject can score higher than the passage that answers the question, and because citations are numbered in retrieval order, the answer then arrives as `[3]` while a weaker passage is `[1]`.

A re-ranking step sits between retrieval and prompt construction. Chunks are fetched by embedding similarity, reordered, and only then numbered, so `[1]` is the chunk the reranker judged best.

## Configuration

```
RETRIEVAL_RERANKER=lexical      # lexical (default) or none
RETRIEVAL_RERANK_WEIGHT=0.6     # weight given to embedding similarity
```

`none` installs a pass-through implementation and keeps pure embedding order.

## How the lexical reranker scores

```
score = weight × embedding_similarity + (1 − weight) × relative_term_overlap
```

Term overlap counts how many of the question's own terms appear in the chunk, ignoring short and very common words.

Overlap is normalised **against the best chunk in the same result set** rather than against the whole question. Question terms that appear in no candidate — "vendor" and "clause" in *"What is the termination clause in the vendor agreement?"* — say nothing about which chunk is better, and dividing by the full question length let them dilute the signal until it could not overcome even a small similarity gap. In the case that motivated this, the correct chunk lost by 0.006 before normalisation and won comfortably after.

The weight keeps embeddings in charge. A chunk that merely repeats a keyword cannot displace a strong semantic match, which is covered by a test.

## What it does not do

`relevance` on a citation stays the raw embedding similarity, not the blended score, so it remains comparable with `RETRIEVAL_SIMILARITY_THRESHOLD`. A consequence is that citation relevance is no longer necessarily descending — that is re-ranking working, not a bug.

Re-ranking never widens the candidate set. It reorders what retrieval already returned, so it cannot rescue a chunk that the similarity threshold rejected.

## Observing it

`documind.retrieval.reranked` carries two tags:

| Tag | Meaning |
|---|---|
| `changed_order` | the reranker returned a different ordering |
| `changed_top` | the chunk cited as `[1]` changed |

Both matter. An early version only tracked the top, and reported `changed_top="false"` on a query where the tail had visibly been reordered — the metric said nothing was happening while the ordering had in fact changed. When the top chunk is promoted, the retriever also logs which chunk displaced which, with both similarities.

On small corpora the effect is often nil, because a 900 character chunk over a short document usually already contains the answer. That is expected: a reranker that changes nothing when retrieval was already right is behaving correctly.

## A cross-encoder instead

`ChunkReranker` is a single method. A hosted cross-encoder or an LLM-scored reranker slots in behind the same interface and the same metric, at the cost of a network call per question. The lexical implementation is the default because it needs no extra infrastructure and works offline.
