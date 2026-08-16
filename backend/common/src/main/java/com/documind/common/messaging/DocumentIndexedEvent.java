package com.documind.common.messaging;

import java.time.Instant;
import java.util.UUID;

public record DocumentIndexedEvent(
        UUID documentId, UUID workspaceId, int chunkCount, Instant indexedAt) {}
