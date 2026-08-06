package com.documind.common.rag;

public class EmbeddingDimensionMismatchException extends RuntimeException {

    public EmbeddingDimensionMismatchException(String message) {
        super(message);
    }
}
