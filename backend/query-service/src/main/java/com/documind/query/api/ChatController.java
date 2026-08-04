package com.documind.query.api;

import com.documind.common.persistence.entity.ChatMessageEntity;
import com.documind.common.persistence.entity.ChatSessionEntity;
import com.documind.common.security.AuthenticatedUser;
import com.documind.common.security.CurrentUser;
import com.documind.query.api.dto.ChatMessageRequest;
import com.documind.query.api.dto.ChatMessageResponse;
import com.documind.query.api.dto.ChatSessionResponse;
import com.documind.query.api.dto.CreateSessionRequest;
import com.documind.query.chat.AnswerStreamEvent;
import com.documind.query.chat.AnswerStreamService;
import com.documind.query.chat.ChatSessionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatSessionService sessionService;
    private final AnswerStreamService answerStreamService;

    public ChatController(ChatSessionService sessionService, AnswerStreamService answerStreamService) {
        this.sessionService = sessionService;
        this.answerStreamService = answerStreamService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<ChatSessionResponse> createSession(@RequestBody CreateSessionRequest request) {
        AuthenticatedUser user = CurrentUser.require();
        ChatSessionEntity session = sessionService.createSession(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ChatSessionResponse.from(session));
    }

    @GetMapping("/sessions")
    public List<ChatSessionResponse> listSessions() {
        AuthenticatedUser user = CurrentUser.require();
        return sessionService.listSessions(user.workspaceId()).stream()
                .map(ChatSessionResponse::from)
                .toList();
    }

    @GetMapping("/sessions/{sessionId}")
    public List<ChatMessageResponse> sessionHistory(@PathVariable UUID sessionId) {
        AuthenticatedUser user = CurrentUser.require();
        ChatSessionEntity session = sessionService.requireSession(sessionId, user.workspaceId());
        return sessionService.loadHistory(session.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @PostMapping(value = "/sessions/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AnswerStreamEvent>> sendMessage(
            @PathVariable UUID sessionId, @Valid @RequestBody ChatMessageRequest request) {
        AuthenticatedUser user = CurrentUser.require();
        ChatSessionEntity session = sessionService.requireSession(sessionId, user.workspaceId());
        return answerStreamService
                .streamAnswer(session, request.content(), user)
                .map(event -> ServerSentEvent.builder(event)
                        .event(eventName(event))
                        .build());
    }

    private String eventName(AnswerStreamEvent event) {
        return switch (event) {
            case AnswerStreamEvent.Token ignored -> "token";
            case AnswerStreamEvent.Citations ignored -> "citations";
            case AnswerStreamEvent.Completed ignored -> "completed";
            case AnswerStreamEvent.Failed ignored -> "failed";
        };
    }

    private ChatMessageResponse toMessageResponse(ChatMessageEntity message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                sessionService.deserializeCitations(message.getCitations()),
                message.getCreatedAt());
    }
}
