package com.documind.query.rag;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reorders chunks by blending the embedding similarity with how many of the question's own terms
 * appear in the chunk. Dense retrieval matches on topic, which can rank a passage that merely
 * discusses the subject above the passage that answers the question.
 */
public class LexicalOverlapReranker implements ChunkReranker {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final int SHORTEST_MEANINGFUL_TERM = 3;
    private static final Set<String> IGNORED_TERMS = Set.of(
            "the", "and", "for", "are", "was", "were", "what", "when", "which", "who", "whom", "this", "that",
            "these", "those", "with", "from", "into", "does", "did", "has", "have", "had", "how", "why", "can",
            "could", "should", "would", "about", "there", "their", "our", "your", "its", "get", "gets",
            "give", "given", "under", "over", "per", "any", "all", "not", "but", "you", "they", "them");

    private final double vectorWeight;

    public LexicalOverlapReranker(double vectorWeight) {
        this.vectorWeight = Math.clamp(vectorWeight, 0.0, 1.0);
    }

    @Override
    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> chunks) {
        Set<String> questionTerms = termsOf(question);
        if (questionTerms.isEmpty() || chunks.size() < 2) {
            return chunks;
        }

        // Terms absent from every candidate say nothing about which chunk is better, so overlap is
        // scored relative to the best chunk in this result set rather than to the whole question.
        double bestOverlap = chunks.stream()
                .mapToDouble(chunk -> lexicalOverlap(chunk, questionTerms))
                .max()
                .orElse(0.0);
        if (bestOverlap == 0.0) {
            return chunks;
        }

        return chunks.stream()
                .sorted(Comparator.comparingDouble((RetrievedChunk chunk) -> score(chunk, questionTerms, bestOverlap))
                        .reversed())
                .toList();
    }

    /** Blended score, exposed so the retriever can log why an order changed. */
    public double score(RetrievedChunk chunk, Set<String> questionTerms, double bestOverlap) {
        double relativeOverlap = bestOverlap == 0.0 ? 0.0 : lexicalOverlap(chunk, questionTerms) / bestOverlap;
        return vectorWeight * chunk.relevance() + (1 - vectorWeight) * relativeOverlap;
    }

    public Set<String> termsOf(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
        return Arrays.stream(NON_WORD.split(normalized))
                .filter(term -> term.length() >= SHORTEST_MEANINGFUL_TERM)
                .filter(term -> !IGNORED_TERMS.contains(term))
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private double lexicalOverlap(RetrievedChunk chunk, Set<String> questionTerms) {
        Set<String> chunkTerms = termsOf(chunk.text());
        if (chunkTerms.isEmpty()) {
            return 0.0;
        }

        long matched = questionTerms.stream().filter(chunkTerms::contains).count();
        return (double) matched / questionTerms.size();
    }
}
