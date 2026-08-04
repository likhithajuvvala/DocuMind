package com.documind.document.api.dto;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.domain.IngestionStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentStatusResponse(
        UUID documentId,
        DocumentStatus status,
        IngestionStatus ingestionStatus,
        int chunkCount,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {
}
