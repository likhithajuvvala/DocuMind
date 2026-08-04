package com.documind.document.api.dto;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.persistence.entity.DocumentEntity;
import java.time.Instant;
import java.util.UUID;

public record DocumentSummaryResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        UUID uploadedBy,
        Instant createdAt) {

    public static DocumentSummaryResponse from(DocumentEntity document) {
        return new DocumentSummaryResponse(
                document.getId(),
                document.getFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getUploadedBy(),
                document.getCreatedAt());
    }
}
