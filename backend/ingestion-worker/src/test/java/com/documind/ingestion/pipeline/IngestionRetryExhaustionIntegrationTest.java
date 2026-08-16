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
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.persistence.repository.IngestionJobRepository;
import com.documind.common.persistence.repository.UserRepository;
import com.documind.common.persistence.repository.WorkspaceRepository;
import com.documind.ingestion.support.FastRetryErrorHandlerConfiguration;
import com.documind.ingestion.support.TestEmbeddingModelConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the redelivery and dead-letter wiring against a real broker: a message that keeps failing
 * is actually retried by Kafka, not just retried in the sense that our own code calls a retry
 * method, and it actually lands on the configured dead letter topic once the budget for this test's
 * backoff is exhausted.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Import({TestEmbeddingModelConfiguration.class, FastRetryErrorHandlerConfiguration.class})
class IngestionRetryExhaustionIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("documind")
                    .withUsername("documind")
                    .withPassword("documind");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0")).withKraft();

    @Container
    static final MinIOContainer MINIO =
            new MinIOContainer("minio/minio:RELEASE.2024-11-07T00-52-20Z");

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
        registry.add(
                "spring.ai.vectorstore.pgvector.dimensions",
                () -> TestEmbeddingModelConfiguration.TEST_DIMENSIONS);
    }

    @Autowired private WorkspaceRepository workspaceRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private DocumentRepository documentRepository;

    @Autowired private IngestionJobRepository jobRepository;

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

    private UUID workspaceId;
    private UUID userId;
    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void seedTenant() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Instant now = Instant.now();
        workspaceRepository.save(
                new WorkspaceEntity(workspaceId, "Retry Test Workspace", WorkspacePlan.TEAM, now));
        userRepository.save(
                new UserEntity(
                        userId,
                        "retry-" + userId + "@documind.test",
                        "hash",
                        workspaceId,
                        UserRole.ADMIN,
                        now));

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlt-observer-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dltConsumer = new KafkaConsumer<>(consumerProps);
        dltConsumer.subscribe(
                List.of(KafkaTopics.DOCUMENT_UPLOADED_DEAD_LETTER, KafkaTopics.DOCUMENT_FAILED));
        dltConsumer.poll(Duration.ofMillis(500));
    }

    @AfterEach
    void closeConsumer() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    @Test
    void aDocumentThatKeepsFailingIsRedeliveredThenDeadLetteredAndMarkedFailed() {
        // No bytes are ever written to storage for this document, so every attempt to read them
        // throws a transient, retryable ObjectStorageException.
        UUID documentId = UUID.randomUUID();
        String storagePath = workspaceId + "/" + documentId + "/missing.txt";
        DocumentEntity document =
                documentRepository.save(
                        new DocumentEntity(
                                documentId,
                                workspaceId,
                                "missing.txt",
                                "text/plain",
                                0,
                                storagePath,
                                DocumentStatus.PENDING,
                                userId,
                                Instant.now()));

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

        ConsumerRecord<String, String> deadLettered =
                KafkaTestUtils.getSingleRecord(
                        dltConsumer, KafkaTopics.DOCUMENT_UPLOADED_DEAD_LETTER, AWAIT_TIMEOUT);
        assertThat(deadLettered.value())
                .as(
                        "the exact original event must reach the dead letter topic, not a summary of it")
                .contains(document.getId().toString());

        await().atMost(AWAIT_TIMEOUT)
                .untilAsserted(
                        () ->
                                assertThat(
                                                documentRepository
                                                        .findById(document.getId())
                                                        .orElseThrow()
                                                        .getStatus())
                                        .isEqualTo(DocumentStatus.FAILED));

        IngestionJobEntity latestJob =
                jobRepository
                        .findFirstByDocumentIdOrderByStartedAtDesc(document.getId())
                        .orElseThrow();
        assertThat(latestJob.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(latestJob.getErrorMessage()).isEqualTo("Ingestion retries were exhausted");

        ConsumerRecord<String, String> failedEvent =
                KafkaTestUtils.getSingleRecord(
                        dltConsumer, KafkaTopics.DOCUMENT_FAILED, AWAIT_TIMEOUT);
        assertThat(failedEvent.value()).contains(document.getId().toString());
    }
}
