package com.documind.query.pii;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Replaces personal data with stable placeholders before a prompt is handed to a model provider.
 *
 * <p>A redaction session is scoped to one request so that the same value always becomes the same
 * placeholder within a prompt. The model can then still reason about a value appearing twice
 * without ever receiving it.
 */
public class PiiRedactor {

    public RedactionSession newSession() {
        return new RedactionSession();
    }

    public static final class RedactionSession {

        private final Map<String, String> placeholdersByValue = new LinkedHashMap<>();
        private final Map<PiiCategory, Integer> counts = new LinkedHashMap<>();

        private RedactionSession() {}

        public String redact(String text) {
            if (text == null || text.isBlank()) {
                return text;
            }

            String redacted = text;
            for (PiiCategory category : PiiCategory.values()) {
                redacted = replaceMatches(redacted, category);
            }
            return redacted;
        }

        public Map<PiiCategory, Integer> counts() {
            return Map.copyOf(counts);
        }

        public int totalRedactions() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        private String replaceMatches(String text, PiiCategory category) {
            Matcher matcher = category.pattern().matcher(text);
            StringBuilder rewritten = new StringBuilder();

            while (matcher.find()) {
                String candidate = matcher.group();
                String replacement = category.confirms(candidate) ? placeholderFor(category, candidate) : candidate;
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(rewritten);
            return rewritten.toString();
        }

        private String placeholderFor(PiiCategory category, String value) {
            return placeholdersByValue.computeIfAbsent(value, ignored -> {
                int next = counts.merge(category, 1, Integer::sum);
                return "[%s_%d]".formatted(category.label(), next);
            });
        }
    }
}
