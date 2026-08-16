package com.documind.query.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.documind.common.rag.ChunkMetadataKeys;
import com.documind.query.rag.support.HashingEmbeddingModel;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves workspace isolation at the layer that actually enforces it: a real pgvector similarity
 * search, not a mock. {@link ChunkRetriever} and {@link PgVectorStore} are both production
 * classes; only the embedding model is a deterministic test double, chosen so that a
 * cross-tenant chunk can be made the objectively best content match and isolation still has to
 * hold against it.
 *
 * <p>No Spring context is started here. Booting the full query-service context would require a
 * chat model, JWT configuration, and PII redaction wiring that have nothing to do with retrieval
 * filtering, so the store and retriever are constructed directly against a Testcontainers
 * Postgres instance instead.
 */
@Testcontainers
class TenantIsolationIntegrationTest {

    private static final int DIMENSIONS = 32;
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("documind")
            .withUsername("documind")
            .withPassword("documind");

    private static HikariDataSource dataSource;
    private static PgVectorStore vectorStore;

    @BeforeAll
    static void startVectorStore() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create extension if not exists vector");
        jdbcTemplate.execute("create extension if not exists \"uuid-ossp\"");

        vectorStore = PgVectorStore.builder(jdbcTemplate, new HashingEmbeddingModel(DIMENSIONS))
                .dimensions(DIMENSIONS)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
        vectorStore.afterPropertiesSet();
    }

    @AfterAll
    static void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private ChunkRetriever retriever(double similarityThreshold) {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setTopK(10);
        properties.setSimilarityThreshold(similarityThreshold);
        return new ChunkRetriever(vectorStore, properties, new SimpleMeterRegistry(), new PassThroughReranker());
    }

    private void index(UUID workspaceId, UUID documentId, String documentName, Integer page, String text) {
        vectorStore.add(List.of(new Document(
                UUID.randomUUID().toString(),
                text,
                Map.of(
                        ChunkMetadataKeys.WORKSPACE_ID, workspaceId.toString(),
                        ChunkMetadataKeys.DOCUMENT_ID, documentId.toString(),
                        ChunkMetadataKeys.DOCUMENT_NAME, documentName,
                        ChunkMetadataKeys.PAGE_NUMBER, page,
                        ChunkMetadataKeys.CHUNK_INDEX, 0))));
    }

    @Test
    void retrievalNeverReturnsAnotherWorkspacesChunkEvenWhenItIsTheBestContentMatch() {
        String question = "What is the termination notice period for the vendor agreement?";

        UUID workspaceA = UUID.randomUUID();
        UUID workspaceB = UUID.randomUUID();
        UUID competitorDocument = UUID.randomUUID();

        // Workspace B holds the best possible match for the question under this embedding model:
        // near-identical vocabulary, so its similarity to the query is close to 1.0.
        index(
                workspaceB,
                competitorDocument,
                "other-tenant-contract.md",
                1,
                "What is the termination notice period for the vendor agreement? Ninety days.");

        // Workspace A holds two chunks about unrelated subjects.
        UUID documentA1 = UUID.randomUUID();
        UUID documentA2 = UUID.randomUUID();
        index(workspaceA, documentA1, "handbook.md", 1, "Staff accrue twenty five days of paid annual leave per year.");
        index(workspaceA, documentA2, "expenses.md", 1, "Expense claims must be submitted within sixty days of purchase.");

        // Positive control: if isolation were broken, this is the chunk that would leak.
        List<RetrievedChunk> asWorkspaceB = retriever(0.0).retrieve(question, workspaceB, null);
        assertThat(asWorkspaceB).extracting(RetrievedChunk::documentId).contains(competitorDocument);

        List<RetrievedChunk> asWorkspaceA = retriever(0.0).retrieve(question, workspaceA, null);
        assertThat(asWorkspaceA).isNotEmpty();
        assertThat(asWorkspaceA)
                .as("every chunk returned for workspace A must actually belong to workspace A")
                .extracting(RetrievedChunk::documentId)
                .containsOnly(documentA1, documentA2);
        assertThat(asWorkspaceA)
                .as("workspace B's higher-scoring chunk must never appear in workspace A's results")
                .extracting(RetrievedChunk::documentId)
                .doesNotContain(competitorDocument);
    }

    @Test
    void documentScopedRetrievalExcludesOtherDocumentsInTheSameWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        UUID targetDocument = UUID.randomUUID();
        UUID otherDocument = UUID.randomUUID();

        index(workspaceId, targetDocument, "target.md", 1, "The termination clause requires ninety days notice.");
        index(workspaceId, otherDocument, "other.md", 1, "The termination clause in this contract requires sixty days notice.");

        List<RetrievedChunk> scoped =
                retriever(0.0).retrieve("What does the termination clause require?", workspaceId, targetDocument);

        assertThat(scoped).isNotEmpty();
        assertThat(scoped).extracting(RetrievedChunk::documentId).containsOnly(targetDocument);
    }

    @Test
    void aWorkspaceWithOnlyUnrelatedContentGetsNoMatchRatherThanAnotherWorkspacesAnswer() {
        String question = "What is the refund policy for cancelled orders?";

        UUID workspaceWithTheAnswer = UUID.randomUUID();
        index(
                workspaceWithTheAnswer,
                UUID.randomUUID(),
                "refunds.md",
                1,
                "What is the refund policy for cancelled orders? Full refund within thirty days.");

        UUID unrelatedWorkspace = UUID.randomUUID();
        index(
                unrelatedWorkspace,
                UUID.randomUUID(),
                "security-policy.md",
                1,
                "Passwords must be rotated every ninety days and stored using an approved secrets manager.");

        List<RetrievedChunk> results = retriever(0.6).retrieve(question, unrelatedWorkspace, null);

        assertThat(results)
                .as("a real miss must come back empty, never silently answered from another workspace's content")
                .isEmpty();
    }

    @Test
    void aWorkspaceWithNoIndexedContentAtAllReturnsNoMatchesNotAnError() {
        UUID emptyWorkspace = UUID.randomUUID();

        List<RetrievedChunk> results = retriever(0.0).retrieve("Anything at all", emptyWorkspace, null);

        assertThat(results).isEmpty();
    }
}
