package com.documind.common.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentDeletedEvent(
        UUID documentId, UUID workspaceId, List<String> embeddingIds, Instant deletedAt) {}
