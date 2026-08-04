package com.documind.query.chat;

import com.documind.query.rag.Citation;
import java.util.List;
import java.util.UUID;

public sealed interface AnswerStreamEvent {

    record Token(String text) implements AnswerStreamEvent {
    }

    record Citations(List<Citation> citations) implements AnswerStreamEvent {
    }

    record Completed(UUID messageId, int tokenCount) implements AnswerStreamEvent {
    }

    record Failed(String reason) implements AnswerStreamEvent {
    }
}
