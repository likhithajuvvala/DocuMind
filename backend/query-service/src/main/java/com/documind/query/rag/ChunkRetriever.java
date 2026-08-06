package com.documind.query.rag;

import com.documind.common.rag.ChunkMetadataKeys;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

@Component
public class ChunkRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkRetriever.class);
    private static final String OUTCOME_METRIC = "documind.retrieval.results";
    private static final String SCORE_METRIC = "documind.retrieval.best.score";
    private static final double NO_SCORE = 0.0;

    private final VectorStore vectorStore;
    private final RetrievalProperties properties;
    private final DistributionSummary bestScores;
    private final MeterRegistry meterRegistry;

    public ChunkRetriever(VectorStore vectorStore, RetrievalProperties properties, MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.bestScores = DistributionSummary.builder(SCORE_METRIC)
                .description("Similarity of the closest chunk found for a question")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public List<RetrievedChunk> retrieve(String question, UUID workspaceId, UUID documentId) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(properties.getTopK())
                .similarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)
                .filterExpression(buildFilter(workspaceId, documentId))
                .build();

        List<Document> matches = vectorStore.similaritySearch(request);
        if (matches == null || matches.isEmpty()) {
            countOutcome("no_documents");
            LOGGER.info("Workspace {} has no indexed content to answer from", workspaceId);
            return List.of();
        }

        double bestScore = matches.stream().mapToDouble(this::scoreOf).max().orElse(NO_SCORE);
        bestScores.record(bestScore);

        List<Document> relevant = matches.stream()
                .filter(match -> scoreOf(match) >= properties.getSimilarityThreshold())
                .toList();

        if (relevant.isEmpty()) {
            countOutcome("below_threshold");
            LOGGER.info(
                    "No chunk cleared the similarity threshold {} for workspace {}. The closest was {} in {} at {}. "
                            + "Lower documind.retrieval.similarity-threshold if answers are being missed.",
                    properties.getSimilarityThreshold(),
                    workspaceId,
                    String.format("%.3f", bestScore),
                    documentNameOf(matches.get(0)),
                    documentIdOf(matches.get(0)));
            return List.of();
        }

        countOutcome("grounded");
        List<RetrievedChunk> chunks = new ArrayList<>(relevant.size());
        for (int index = 0; index < relevant.size(); index++) {
            chunks.add(toRetrievedChunk(relevant.get(index), index + 1));
        }
        return List.copyOf(chunks);
    }

    private void countOutcome(String outcome) {
        meterRegistry.counter(OUTCOME_METRIC, "outcome", outcome).increment();
    }

    private double scoreOf(Document document) {
        return document.getScore() == null ? NO_SCORE : document.getScore();
    }

    private String documentNameOf(Document document) {
        return String.valueOf(document.getMetadata().get(ChunkMetadataKeys.DOCUMENT_NAME));
    }

    private String documentIdOf(Document document) {
        return String.valueOf(document.getMetadata().get(ChunkMetadataKeys.DOCUMENT_ID));
    }

    private Filter.Expression buildFilter(UUID workspaceId, UUID documentId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        var workspaceFilter = builder.eq(ChunkMetadataKeys.WORKSPACE_ID, workspaceId.toString());
        if (documentId == null) {
            return workspaceFilter.build();
        }
        return builder.and(workspaceFilter, builder.eq(ChunkMetadataKeys.DOCUMENT_ID, documentId.toString()))
                .build();
    }

    private RetrievedChunk toRetrievedChunk(Document document, int reference) {
        Map<String, Object> metadata = document.getMetadata();
        return new RetrievedChunk(
                reference,
                UUID.fromString(String.valueOf(metadata.get(ChunkMetadataKeys.DOCUMENT_ID))),
                String.valueOf(metadata.get(ChunkMetadataKeys.DOCUMENT_NAME)),
                readPageNumber(metadata),
                document.getText(),
                scoreOf(document));
    }

    private Integer readPageNumber(Map<String, Object> metadata) {
        Object pageNumber = metadata.get(ChunkMetadataKeys.PAGE_NUMBER);
        if (pageNumber instanceof Number number) {
            return number.intValue();
        }
        return pageNumber == null ? null : Integer.valueOf(pageNumber.toString());
    }
}
