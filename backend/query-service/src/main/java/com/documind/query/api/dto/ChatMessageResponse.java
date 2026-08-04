package com.documind.query.api.dto;

import com.documind.common.domain.MessageRole;
import com.documind.query.rag.Citation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id, MessageRole role, String content, List<Citation> citations, Instant createdAt) {
}
