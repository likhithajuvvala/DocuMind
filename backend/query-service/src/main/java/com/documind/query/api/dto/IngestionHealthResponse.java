package com.documind.query.api.dto;

public record IngestionHealthResponse(
        long pendingDocuments,
        long processingDocuments,
        long indexedDocuments,
        long failedDocuments) {}
