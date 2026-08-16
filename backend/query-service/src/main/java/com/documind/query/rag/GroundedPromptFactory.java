package com.documind.query.rag;

import com.documind.common.domain.MessageRole;
import com.documind.common.persistence.entity.ChatMessageEntity;
import com.documind.query.pii.PiiCategory;
import com.documind.query.pii.PiiRedactionPolicy;
import com.documind.query.pii.PiiRedactor;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

@Component
public class GroundedPromptFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroundedPromptFactory.class);
    private static final String REDACTION_METRIC = "documind.pii.redactions";

    private final PiiRedactor redactor;
    private final PiiRedactionPolicy policy;
    private final MeterRegistry meterRegistry;

    public GroundedPromptFactory(
            PiiRedactor redactor, PiiRedactionPolicy policy, MeterRegistry meterRegistry) {
        this.redactor = redactor;
        this.policy = policy;
        this.meterRegistry = meterRegistry;
        LOGGER.info("Prompt redaction configured: {}", policy.describe());
    }

    private static final String SYSTEM_INSTRUCTIONS =
            """
            You are DocuMind, an assistant that answers questions strictly from the provided document excerpts.
            Follow these rules without exception:
            - Use only the excerpts below as your source of truth; never rely on outside knowledge.
            - Cite every claim with the bracketed reference of the excerpt that supports it, for example [1].
            - If the excerpts do not contain the answer, reply exactly: The answer was not found in your documents.
            - Keep answers concise and quote exact wording when the question concerns specific clauses or figures.
            """;

    private static final String CONTEXT_TEMPLATE =
            """
            Document excerpts:
            %s

            Question: %s
            """;

    public List<Message> create(
            String question, List<RetrievedChunk> chunks, List<ChatMessageEntity> history) {
        if (!policy.shouldRedact()) {
            return assemble(question, renderChunks(chunks), history, text -> text);
        }

        PiiRedactor.RedactionSession session = redactor.newSession();
        List<Message> messages = assemble(question, renderChunks(chunks), history, session::redact);
        recordRedactions(session);
        return messages;
    }

    private List<Message> assemble(
            String question,
            String renderedChunks,
            List<ChatMessageEntity> history,
            java.util.function.UnaryOperator<String> sanitize) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_INSTRUCTIONS));
        history.forEach(message -> messages.add(toChatMessage(message, sanitize)));
        messages.add(
                new UserMessage(
                        CONTEXT_TEMPLATE.formatted(
                                sanitize.apply(renderedChunks), sanitize.apply(question))));
        return List.copyOf(messages);
    }

    private void recordRedactions(PiiRedactor.RedactionSession session) {
        if (session.totalRedactions() == 0) {
            return;
        }

        for (Map.Entry<PiiCategory, Integer> entry : session.counts().entrySet()) {
            meterRegistry
                    .counter(REDACTION_METRIC, "category", entry.getKey().label())
                    .increment(entry.getValue());
        }
        // Counts only. Logging the values would defeat the point of removing them.
        LOGGER.info(
                "Redacted {} values from the prompt before calling the model",
                session.totalRedactions());
    }

    private Message toChatMessage(
            ChatMessageEntity message, java.util.function.UnaryOperator<String> sanitize) {
        String content = sanitize.apply(message.getContent());
        return message.getRole() == MessageRole.ASSISTANT
                ? new AssistantMessage(content)
                : new UserMessage(content);
    }

    private String renderChunks(List<RetrievedChunk> chunks) {
        return chunks.stream().map(this::renderChunk).collect(Collectors.joining("\n\n"));
    }

    private String renderChunk(RetrievedChunk chunk) {
        String location =
                chunk.pageNumber() == null
                        ? chunk.documentName()
                        : chunk.documentName() + ", page " + chunk.pageNumber();
        return "[%d] (%s)%n%s".formatted(chunk.reference(), location, chunk.text());
    }
}
