package com.documind.query.chat;

import com.documind.common.persistence.entity.ChatMessageEntity;
import com.documind.common.persistence.entity.ChatSessionEntity;
import com.documind.common.security.AuthenticatedUser;
import com.documind.query.rag.Citation;
import com.documind.query.rag.ChunkRetriever;
import com.documind.query.rag.GroundedPromptFactory;
import com.documind.query.rag.RetrievalProperties;
import com.documind.query.rag.RetrievedChunk;
import com.documind.query.usage.UsageRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AnswerStreamService {

    private static final String NO_ANSWER_RESPONSE = "The answer was not found in your documents.";
    private static final Logger LOGGER = LoggerFactory.getLogger(AnswerStreamService.class);

    private final ChatClient chatClient;
    private final ChatSessionService sessionService;
    private final ChunkRetriever chunkRetriever;
    private final GroundedPromptFactory promptFactory;
    private final RetrievalProperties retrievalProperties;
    private final UsageRecorder usageRecorder;
    private final MeterRegistry meterRegistry;

    public AnswerStreamService(
            ChatClient chatClient,
            ChatSessionService sessionService,
            ChunkRetriever chunkRetriever,
            GroundedPromptFactory promptFactory,
            RetrievalProperties retrievalProperties,
            UsageRecorder usageRecorder,
            MeterRegistry meterRegistry) {
        this.chatClient = chatClient;
        this.sessionService = sessionService;
        this.chunkRetriever = chunkRetriever;
        this.promptFactory = promptFactory;
        this.retrievalProperties = retrievalProperties;
        this.usageRecorder = usageRecorder;
        this.meterRegistry = meterRegistry;
    }

    public Flux<AnswerStreamEvent> streamAnswer(ChatSessionEntity session, String question, AuthenticatedUser user) {
        sessionService.recordUserMessage(session.getId(), question);

        List<RetrievedChunk> chunks;
        List<Message> prompt;
        List<Citation> citations;
        try {
            chunks = chunkRetriever.retrieve(question, session.getWorkspaceId(), session.getDocumentId());
            if (chunks.isEmpty()) {
                return respondWithoutGrounding(session);
            }

            citations = chunks.stream().map(chunk -> chunk.toCitation()).toList();
            prompt = promptFactory.create(
                    question,
                    chunks,
                    sessionService.loadRecentHistory(session.getId(), retrievalProperties.getHistoryMessageLimit()));
        } catch (RuntimeException exception) {
            countAnswer("failed");
            LOGGER.error("Retrieval failed for session {}", session.getId(), exception);
            return Flux.just(new AnswerStreamEvent.Failed(exception.getMessage()));
        }

        StringBuilder answer = new StringBuilder();
        Flux<AnswerStreamEvent> tokens = chatClient.prompt().messages(prompt).stream().content().map(text -> {
            answer.append(text);
            return new AnswerStreamEvent.Token(text);
        });

        return tokens.cast(AnswerStreamEvent.class)
                .concatWith(Flux.defer(() -> persistAnswer(session, user, question, answer.toString(), citations)))
                .onErrorResume(exception -> {
                    countAnswer("failed");
                    LOGGER.error("Streaming failed for session {}", session.getId(), exception);
                    return Flux.just(new AnswerStreamEvent.Failed(exception.getMessage()));
                });
    }

    private Flux<AnswerStreamEvent> persistAnswer(
            ChatSessionEntity session,
            AuthenticatedUser user,
            String question,
            String answer,
            List<Citation> citations) {
        ChatMessageEntity stored = sessionService.recordAssistantMessage(session.getId(), answer, citations);
        int tokenCount = usageRecorder.record(user, question, answer);
        countAnswer("grounded");
        return Flux.just(
                new AnswerStreamEvent.Citations(citations),
                new AnswerStreamEvent.Completed(stored.getId(), tokenCount));
    }

    private void countAnswer(String outcome) {
        meterRegistry.counter("documind.chat.answers", "outcome", outcome).increment();
    }

    private Flux<AnswerStreamEvent> respondWithoutGrounding(ChatSessionEntity session) {
        ChatMessageEntity stored =
                sessionService.recordAssistantMessage(session.getId(), NO_ANSWER_RESPONSE, List.of());
        countAnswer("not_found");
        return Flux.just(
                new AnswerStreamEvent.Token(NO_ANSWER_RESPONSE),
                new AnswerStreamEvent.Citations(List.of()),
                new AnswerStreamEvent.Completed(stored.getId(), 0));
    }
}
