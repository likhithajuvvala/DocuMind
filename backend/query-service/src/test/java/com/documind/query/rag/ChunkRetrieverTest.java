package com.documind.query.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.documind.common.rag.ChunkMetadataKeys;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class ChunkRetrieverTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void keepsOnlyChunksThatClearTheThreshold() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(chunk("clause text", 0.82), chunk("unrelated text", 0.41)));

        List<RetrievedChunk> chunks = retriever(0.7).retrieve("question", WORKSPACE_ID, null);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).reference()).isEqualTo(1);
        assertThat(chunks.get(0).relevance()).isEqualTo(0.82);
        assertThat(counter("grounded")).isEqualTo(1);
    }

    @Test
    void reportsWhenEverythingWasFilteredOutByTheThreshold() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(chunk("near miss", 0.68), chunk("further", 0.5)));

        List<RetrievedChunk> chunks = retriever(0.7).retrieve("question", WORKSPACE_ID, null);

        assertThat(chunks).isEmpty();
        assertThat(counter("below_threshold")).isEqualTo(1);
        assertThat(meterRegistry.get("documind.retrieval.best.score").summary().max())
                .isEqualTo(0.68);
    }

    @Test
    void separatesAnEmptyWorkspaceFromANearMiss() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<RetrievedChunk> chunks = retriever(0.7).retrieve("question", WORKSPACE_ID, null);

        assertThat(chunks).isEmpty();
        assertThat(counter("no_documents")).isEqualTo(1);
        assertThat(counter("below_threshold")).isZero();
    }

    @Test
    void asksTheStoreForEverythingSoNearMissesRemainVisible() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(chunk("text", 0.9)));

        retriever(0.7).retrieve("question", WORKSPACE_ID, null);

        org.mockito.ArgumentCaptor<SearchRequest> captured =
                org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        org.mockito.Mockito.verify(vectorStore).similaritySearch(captured.capture());
        assertThat(captured.getValue().getSimilarityThreshold())
                .isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
        assertThat(captured.getValue().getTopK()).isEqualTo(6);
    }

    @Test
    void numbersCitationsAfterReRankingSoTheBestChunkIsCitationOne() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(
                        List.of(
                                chunk("renewal wording", 0.80),
                                chunk("termination clause wording", 0.72)));

        List<RetrievedChunk> chunks =
                retriever(0.7, new LexicalOverlapReranker(0.6))
                        .retrieve("termination clause", WORKSPACE_ID, null);

        assertThat(chunks.get(0).text()).isEqualTo("termination clause wording");
        assertThat(chunks.get(0).reference()).isEqualTo(1);
        assertThat(chunks.get(1).reference()).isEqualTo(2);
    }

    @Test
    void recordsWhenReRankingChangedTheTopChunk() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(
                        List.of(
                                chunk("renewal wording", 0.80),
                                chunk("termination clause wording", 0.72)));

        retriever(0.7, new LexicalOverlapReranker(0.6))
                .retrieve("termination clause", WORKSPACE_ID, null);

        assertThat(
                        meterRegistry
                                .find("documind.retrieval.reranked")
                                .tag("changed_top", "true")
                                .tag("changed_order", "true")
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    @Test
    void recordsReorderingThatDidNotReachTheTopChunk() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(
                        List.of(
                                chunk("lease termination notice wording", 0.90),
                                chunk("unrelated filler", 0.70),
                                chunk("more lease termination wording", 0.60)));

        retriever(0.5, new LexicalOverlapReranker(0.6))
                .retrieve("lease termination", WORKSPACE_ID, null);

        assertThat(
                        meterRegistry
                                .find("documind.retrieval.reranked")
                                .tag("changed_order", "true")
                                .tag("changed_top", "false")
                                .counter()
                                .count())
                .isEqualTo(1);
    }

    @Test
    void numbersCitationsFromTheFilteredResults() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(
                        List.of(chunk("first", 0.9), chunk("weak", 0.2), chunk("second", 0.75)));

        List<RetrievedChunk> chunks = retriever(0.7).retrieve("question", WORKSPACE_ID, null);

        assertThat(chunks).extracting(RetrievedChunk::reference).containsExactly(1, 2);
        assertThat(chunks).extracting(RetrievedChunk::text).containsExactly("first", "second");
    }

    private double counter(String outcome) {
        return meterRegistry
                .find("documind.retrieval.results")
                .tag("outcome", outcome)
                .counters()
                .stream()
                .mapToDouble(c -> c.count())
                .sum();
    }

    private ChunkRetriever retriever(double threshold) {
        return retriever(threshold, new PassThroughReranker());
    }

    private ChunkRetriever retriever(double threshold, ChunkReranker reranker) {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setSimilarityThreshold(threshold);
        properties.setTopK(6);
        return new ChunkRetriever(vectorStore, properties, meterRegistry, reranker);
    }

    private Document chunk(String text, double score) {
        return Document.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .metadata(
                        Map.of(
                                ChunkMetadataKeys.WORKSPACE_ID,
                                WORKSPACE_ID.toString(),
                                ChunkMetadataKeys.DOCUMENT_ID,
                                DOCUMENT_ID.toString(),
                                ChunkMetadataKeys.DOCUMENT_NAME,
                                "vendor-services-agreement.md",
                                ChunkMetadataKeys.PAGE_NUMBER,
                                1))
                .score(score)
                .build();
    }
}
