package com.documind.query.api.dto;

import com.documind.common.persistence.entity.ChatSessionEntity;
import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(UUID id, UUID documentId, String title, Instant createdAt) {

    public static ChatSessionResponse from(ChatSessionEntity session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getDocumentId(),
                session.getTitle(),
                session.getCreatedAt());
    }
}
