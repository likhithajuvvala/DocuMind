package com.documind.query.rag;

import java.util.UUID;

public record RetrievedChunk(
        int reference, UUID documentId, String documentName, Integer pageNumber, String text, double relevance) {

    public Citation toCitation() {
        return new Citation(reference, documentId, documentName, pageNumber, relevance);
    }
}
