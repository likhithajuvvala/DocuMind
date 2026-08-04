package com.documind.common.messaging;

import java.time.Instant;
import java.util.UUID;

public record DocumentUploadedEvent(
        UUID documentId,
        UUID workspaceId,
        UUID uploadedBy,
        String filename,
        String contentType,
        String storagePath,
        Instant uploadedAt) {
}
