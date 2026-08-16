package com.documind.query.rag;

import java.util.UUID;

public record Citation(
        int reference,
        UUID documentId,
        String documentName,
        Integer pageNumber,
        double relevance) {}
