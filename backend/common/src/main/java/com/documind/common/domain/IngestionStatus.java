package com.documind.common.domain;

public enum IngestionStatus {
    QUEUED,
    EXTRACTING,
    CHUNKING,
    EMBEDDING,
    COMPLETED,
    FAILED
}
