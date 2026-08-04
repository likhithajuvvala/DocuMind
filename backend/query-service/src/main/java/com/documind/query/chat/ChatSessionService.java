package com.documind.query.chat;

import com.documind.common.domain.MessageRole;
import com.documind.common.error.ResourceNotFoundException;
import com.documind.common.persistence.entity.ChatMessageEntity;
import com.documind.common.persistence.entity.ChatSessionEntity;
import com.documind.common.persistence.repository.ChatMessageRepository;
import com.documind.common.persistence.repository.ChatSessionRepository;
import com.documind.common.security.AuthenticatedUser;
import com.documind.query.api.dto.CreateSessionRequest;
import com.documind.query.rag.Citation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatSessionService {

    private static final String DEFAULT_SESSION_TITLE = "New conversation";

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public ChatSessionService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatSessionEntity createSession(CreateSessionRequest request, AuthenticatedUser user) {
        String title = request.title() == null || request.title().isBlank() ? DEFAULT_SESSION_TITLE : request.title();
        return sessionRepository.save(new ChatSessionEntity(
                UUID.randomUUID(), user.workspaceId(), user.userId(), request.documentId(), title, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<ChatSessionEntity> listSessions(UUID workspaceId) {
        return sessionRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    @Transactional(readOnly = true)
    public ChatSessionEntity requireSession(UUID sessionId, UUID workspaceId) {
        return sessionRepository
                .findByIdAndWorkspaceId(sessionId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session " + sessionId + " was not found"));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> loadHistory(UUID sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageEntity> loadRecentHistory(UUID sessionId, int limit) {
        List<ChatMessageEntity> recent =
                messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, limit));
        Collections.reverse(recent);
        return recent;
    }

    @Transactional
    public ChatMessageEntity recordUserMessage(UUID sessionId, String content) {
        return messageRepository.save(
                new ChatMessageEntity(UUID.randomUUID(), sessionId, MessageRole.USER, content, null, Instant.now()));
    }

    @Transactional
    public ChatMessageEntity recordAssistantMessage(UUID sessionId, String content, List<Citation> citations) {
        return messageRepository.save(new ChatMessageEntity(
                UUID.randomUUID(),
                sessionId,
                MessageRole.ASSISTANT,
                content,
                serializeCitations(citations),
                Instant.now()));
    }

    public List<Citation> deserializeCitations(String citations) {
        if (citations == null || citations.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    citations, objectMapper.getTypeFactory().constructCollectionType(List.class, Citation.class));
        } catch (JsonProcessingException exception) {
            throw new CitationSerializationException("Unable to read stored citations", exception);
        }
    }

    private String serializeCitations(List<Citation> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException exception) {
            throw new CitationSerializationException("Unable to store citations", exception);
        }
    }
}
