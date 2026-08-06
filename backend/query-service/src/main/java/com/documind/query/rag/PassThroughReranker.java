package com.documind.query.rag;

import java.util.List;

public class PassThroughReranker implements ChunkReranker {

    @Override
    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> chunks) {
        return chunks;
    }
}
