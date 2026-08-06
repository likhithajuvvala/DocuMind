package com.documind.query.pii;

import java.util.Set;

/**
 * Decides whether a prompt must be redacted before it leaves the process.
 *
 * <p>The default keys off the provider: a locally hosted model never sends the text anywhere, so
 * redacting it only degrades the answer, while a hosted provider receives whatever is in the prompt.
 */
public class PiiRedactionPolicy {

    private static final Set<String> LOCAL_PROVIDERS = Set.of("ollama", "none");

    private final PiiRedactionProperties properties;
    private final String chatProvider;

    public PiiRedactionPolicy(PiiRedactionProperties properties, String chatProvider) {
        this.properties = properties;
        this.chatProvider = chatProvider == null ? "" : chatProvider.toLowerCase();
    }

    public boolean shouldRedact() {
        return switch (properties.getMode()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case THIRD_PARTY_ONLY -> !LOCAL_PROVIDERS.contains(chatProvider);
        };
    }

    public String describe() {
        return "mode=%s provider=%s redacting=%s".formatted(properties.getMode(), chatProvider, shouldRedact());
    }
}
