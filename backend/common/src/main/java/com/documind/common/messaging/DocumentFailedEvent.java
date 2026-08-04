package com.documind.common.messaging;

import java.time.Instant;
import java.util.UUID;

public record DocumentFailedEvent(UUID documentId, UUID workspaceId, String reason, Instant failedAt) {
}
