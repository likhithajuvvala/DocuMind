package com.documind.query.rag;

import com.documind.common.rag.ChunkMetadataKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

@Component
public class ChunkRetriever {

    private final VectorStore vectorStore;
    private final RetrievalProperties properties;

    public ChunkRetriever(VectorStore vectorStore, RetrievalProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public List<RetrievedChunk> retrieve(String question, UUID workspaceId, UUID documentId) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(properties.getTopK())
                .similarityThreshold(properties.getSimilarityThreshold())
                .filterExpression(buildFilter(workspaceId, documentId))
                .build();

        List<Document> matches = vectorStore.similaritySearch(request);
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        List<RetrievedChunk> chunks = new ArrayList<>(matches.size());
        for (int index = 0; index < matches.size(); index++) {
            chunks.add(toRetrievedChunk(matches.get(index), index + 1));
        }
        return List.copyOf(chunks);
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
                document.getScore() == null ? 0.0 : document.getScore());
    }

    private Integer readPageNumber(Map<String, Object> metadata) {
        Object pageNumber = metadata.get(ChunkMetadataKeys.PAGE_NUMBER);
        if (pageNumber instanceof Number number) {
            return number.intValue();
        }
        return pageNumber == null ? null : Integer.valueOf(pageNumber.toString());
    }
}
