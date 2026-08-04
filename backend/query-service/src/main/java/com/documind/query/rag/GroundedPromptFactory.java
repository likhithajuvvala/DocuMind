package com.documind.query.rag;

import com.documind.common.domain.MessageRole;
import com.documind.common.persistence.entity.ChatMessageEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

@Component
public class GroundedPromptFactory {

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

    public List<Message> create(String question, List<RetrievedChunk> chunks, List<ChatMessageEntity> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_INSTRUCTIONS));
        history.forEach(message -> messages.add(toChatMessage(message)));
        messages.add(new UserMessage(CONTEXT_TEMPLATE.formatted(renderChunks(chunks), question)));
        return List.copyOf(messages);
    }

    private Message toChatMessage(ChatMessageEntity message) {
        return message.getRole() == MessageRole.ASSISTANT
                ? new AssistantMessage(message.getContent())
                : new UserMessage(message.getContent());
    }

    private String renderChunks(List<RetrievedChunk> chunks) {
        return chunks.stream().map(this::renderChunk).collect(Collectors.joining("\n\n"));
    }

    private String renderChunk(RetrievedChunk chunk) {
        String location = chunk.pageNumber() == null ? chunk.documentName() : chunk.documentName() + ", page "
                + chunk.pageNumber();
        return "[%d] (%s)%n%s".formatted(chunk.reference(), location, chunk.text());
    }
}
