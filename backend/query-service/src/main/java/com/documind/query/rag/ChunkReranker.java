package com.documind.query.rag;

import java.util.List;

public interface ChunkReranker {

    List<RetrievedChunk> rerank(String question, List<RetrievedChunk> chunks);
}
