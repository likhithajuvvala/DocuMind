package com.documind.ingestion.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.domain.IngestionStatus;
import com.documind.common.domain.UserRole;
import com.documind.common.domain.WorkspacePlan;
import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.messaging.KafkaTopics;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.entity.IngestionJobEntity;
import com.documind.common.persistence.entity.UserEntity;
import com.documind.common.persistence.entity.WorkspaceEntity;
import com.documind.common.persistence.repository.DocumentChunkRepository;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.persistence.repository.IngestionJobRepository;
import com.documind.common.persistence.repository.UserRepository;
import com.documind.common.persistence.repository.WorkspaceRepository;
import com.documind.common.storage.ObjectStorage;
import com.documind.ingestion.support.TestEmbeddingModelConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Drives the ingestion pipeline exactly as production does: publish the same
 * {@link DocumentUploadedEvent} document-service would, over a real Kafka broker, and let the
 * app's own {@code @KafkaListener} consume it. Postgres holds the metadata and the pgvector
 * table, MinIO holds the document bytes. Only the embedding provider is swapped for a
 * deterministic local stand-in so the test does not depend on a paid API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import(TestEmbeddingModelConfiguration.class)
class IngestionPipelineIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("documind")
            .withUsername("documind")
            .withPassword("documind");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0")).withKraft();

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-11-07T00-52-20Z");

    private static final String BUCKET = "documind-test";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("documind.storage.enabled", () -> true);
        registry.add("documind.storage.endpoint", MINIO::getS3URL);
        registry.add("documind.storage.access-key", MINIO::getUserName);
        registry.add("documind.storage.secret-key", MINIO::getPassword);
        registry.add("documind.storage.bucket", () -> BUCKET);
        registry.add("spring.ai.model.embedding", () -> "none");
        registry.add("spring.ai.vectorstore.pgvector.dimensions", () -> TestEmbeddingModelConfiguration.TEST_DIMENSIONS);
    }

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private IngestionJobRepository jobRepository;

    @Autowired
    private ObjectStorage objectStorage;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID workspaceId;
    private UUID userId;
    private Consumer<String, String> eventConsumer;

    @BeforeAll
    static void containersAreHealthy() {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(KAFKA.isRunning()).isTrue();
        assertThat(MINIO.isRunning()).isTrue();
    }

    @BeforeEach
    void seedTenant() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Instant now = Instant.now();
        workspaceRepository.save(new WorkspaceEntity(workspaceId, "Integration Test Workspace", WorkspacePlan.TEAM, now));
        userRepository.save(new UserEntity(
                userId, "ingest-" + userId + "@documind.test", "hash", workspaceId, UserRole.ADMIN, now));

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-observer-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        eventConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps);
        eventConsumer.subscribe(List.of(KafkaTopics.DOCUMENT_INDEXED, KafkaTopics.DOCUMENT_FAILED));
        // Force group assignment before the pipeline publishes anything, so KafkaTestUtils.getSingleRecord
        // does not miss the record while the consumer group is still joining.
        eventConsumer.poll(Duration.ofMillis(500));
    }

    @AfterEach
    void closeConsumer() {
        if (eventConsumer != null) {
            eventConsumer.close();
        }
    }

    @Test
    void uploadedDocumentIsExtractedChunkedEmbeddedAndIndexed() {
        DocumentEntity document = givenAnUploadedDocument(
                "Master Services Agreement",
                "Termination for convenience. Either party may terminate this Agreement without cause "
                        + "by providing ninety days prior written notice to the other party. ".repeat(20));

        publishUploadedEvent(document);

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            DocumentEntity reloaded = documentRepository.findById(document.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        });

        List<com.documind.common.persistence.entity.DocumentChunkEntity> chunks =
                chunkRepository.findByDocumentId(document.getId());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getWorkspaceId()).isEqualTo(workspaceId);
            assertThat(chunk.getChunkText()).isNotBlank();
            assertThat(chunk.getEmbeddingId()).isNotBlank();
        });

        IngestionJobEntity job = jobRepository
                .findFirstByDocumentIdOrderByStartedAtDesc(document.getId())
                .orElseThrow();
        assertThat(job.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(job.getChunkCount()).isEqualTo(chunks.size());

        Long vectorRows = jdbcTemplate.queryForObject(
                "select count(*) from vector_store where metadata->>'document_id' = ?",
                Long.class,
                document.getId().toString());
        assertThat(vectorRows).as("every chunk must have a matching row in the vector store").isEqualTo(chunks.size());

        ConsumerRecord<String, String> indexedEvent =
                KafkaTestUtils.getSingleRecord(eventConsumer, KafkaTopics.DOCUMENT_INDEXED, AWAIT_TIMEOUT);
        assertThat(indexedEvent.value()).contains(document.getId().toString());
    }

    @Test
    void reIngestingASmallerDocumentDoesNotLeaveStaleVectorsBehind() {
        DocumentEntity document = givenAnUploadedDocument(
                "Long Policy",
                "Clause one about retention. Clause two about access control. Clause three about incident response. "
                        .repeat(30));

        publishUploadedEvent(document);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(
                        documentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.INDEXED));

        int firstChunkCount = chunkRepository.findByDocumentId(document.getId()).size();
        assertThat(firstChunkCount).isGreaterThan(1);

        // Replace the stored content with something short enough to produce a single chunk, then
        // re-publish the same document id, exactly as a re-upload would. The replacement text must
        // still clear the chunker's minimum-chunk-length or it would be discarded entirely, which
        // would make this test pass for the wrong reason.
        String shortReplacement = "A single short clause about termination survives after the re-upload.";
        objectStorage.store(
                document.getStoragePath(),
                new ByteArrayInputStream(shortReplacement.getBytes(StandardCharsets.UTF_8)),
                shortReplacement.getBytes(StandardCharsets.UTF_8).length,
                "text/plain");
        document.changeStatus(DocumentStatus.PENDING);
        documentRepository.save(document);

        publishUploadedEvent(document);
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            List<com.documind.common.persistence.entity.DocumentChunkEntity> chunks =
                    chunkRepository.findByDocumentId(document.getId());
            assertThat(chunks).hasSize(1);
        });

        Long vectorRows = jdbcTemplate.queryForObject(
                "select count(*) from vector_store where metadata->>'document_id' = ?",
                Long.class,
                document.getId().toString());
        assertThat(vectorRows)
                .as("the larger document's old embeddings must be gone, not just superseded")
                .isEqualTo(1);
    }

    @Test
    void unparsableDocumentsAreFailedWithoutRetryingOrIndexingAnything() {
        DocumentEntity document = givenAnUploadedDocument("Corrupt Upload", null);
        byte[] corrupt = "%PDF-1.4\nthis is not a real pdf body, no xref table, no trailer.".getBytes(StandardCharsets.UTF_8);
        objectStorage.store(document.getStoragePath(), new ByteArrayInputStream(corrupt), corrupt.length, "application/pdf");

        publishUploadedEvent(document);

        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(
                        documentRepository.findById(document.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.FAILED));

        assertThat(chunkRepository.findByDocumentId(document.getId())).isEmpty();
        IngestionJobEntity job = jobRepository
                .findFirstByDocumentIdOrderByStartedAtDesc(document.getId())
                .orElseThrow();
        assertThat(job.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(job.getErrorMessage()).isNotBlank();

        ConsumerRecord<String, String> failedEvent =
                KafkaTestUtils.getSingleRecord(eventConsumer, KafkaTopics.DOCUMENT_FAILED, AWAIT_TIMEOUT);
        assertThat(failedEvent.value()).contains(document.getId().toString());
    }

    private DocumentEntity givenAnUploadedDocument(String filename, String content) {
        UUID documentId = UUID.randomUUID();
        String storagePath = workspaceId + "/" + documentId + "/" + filename.replace(' ', '-') + ".txt";
        DocumentEntity document = documentRepository.save(new DocumentEntity(
                documentId,
                workspaceId,
                filename,
                "text/plain",
                content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length,
                storagePath,
                DocumentStatus.PENDING,
                userId,
                Instant.now()));

        if (content != null) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            objectStorage.store(storagePath, new ByteArrayInputStream(bytes), bytes.length, "text/plain");
        }
        return document;
    }

    private void publishUploadedEvent(DocumentEntity document) {
        kafkaTemplate.send(
                KafkaTopics.DOCUMENT_UPLOADED,
                document.getId().toString(),
                new DocumentUploadedEvent(
                        document.getId(),
                        document.getWorkspaceId(),
                        document.getUploadedBy(),
                        document.getFilename(),
                        document.getContentType(),
                        document.getStoragePath(),
                        Instant.now()));
    }
}
