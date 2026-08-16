package com.documind.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.domain.UserRole;
import com.documind.common.domain.WorkspacePlan;
import com.documind.common.persistence.entity.DocumentChunkEntity;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.entity.UserEntity;
import com.documind.common.persistence.entity.WorkspaceEntity;
import com.documind.common.persistence.repository.DocumentChunkRepository;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.persistence.repository.UserRepository;
import com.documind.common.persistence.repository.WorkspaceRepository;
import com.documind.common.testsupport.PersistenceTestApplication;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the {@code workspaceFilter} Hibernate filter actually enforces isolation, not just that
 * it's declared. The interesting case here isn't a repository method that already scopes by
 * workspace (that was already covered by call-site discipline) — it's the ones that don't: {@code
 * documentRepository.findById} and {@code chunkRepository.findByDocumentId} are inherited /
 * unscoped methods that a future call site could invoke directly with a cross-tenant id and leak
 * data, exactly the gap the persistence-layer filter exists to close.
 *
 * <p>Each repository call below is deliberately left outside any test-managed transaction — Spring
 * Data JPA repository methods already open their own transaction per call, and the whole point is
 * to prove that {@link WorkspaceScopedTransactionManager#doBegin} picks up whatever {@link
 * WorkspaceContext} holds at the moment each individual call's transaction begins.
 */
@SpringBootTest(
        classes = PersistenceTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TransactionalChunkQueries.class)
@Testcontainers
class WorkspaceIsolationIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("documind")
                    .withUsername("documind")
                    .withPassword("documind");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private DocumentRepository documentRepository;

    @Autowired private DocumentChunkRepository chunkRepository;

    @Autowired private WorkspaceRepository workspaceRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private TransactionalChunkQueries transactionalChunkQueries;

    @AfterEach
    void clearWorkspaceContext() {
        WorkspaceContext.clear();
    }

    @Test
    void
            findByIdIsBlockedForADocumentInAnotherWorkspaceEvenThoughTheCallSiteNeverMentionsWorkspace() {
        UUID workspaceA = UUID.randomUUID();
        UUID workspaceB = UUID.randomUUID();
        DocumentEntity documentInB = saveDocument(workspaceB);

        WorkspaceContext.set(workspaceA);
        assertThat(documentRepository.findById(documentInB.getId()))
                .as("an inherited, unscoped findById must not leak another workspace's row")
                .isEmpty();

        WorkspaceContext.set(workspaceB);
        assertThat(documentRepository.findById(documentInB.getId()))
                .as("the same lookup must succeed once the correct workspace is bound")
                .isPresent();
    }

    @Test
    void findByDocumentIdOnChunksIsBlockedForAnotherWorkspacesDocument() {
        // findByDocumentId is a custom derived-query method, not a base CrudRepository method, so
        // (unlike findById) it gets no transaction of its own — it's called here through a small
        // @Transactional wrapper, exactly how every real call site in this codebase already calls
        // it (DocumentLifecycleService.delete, ChunkIndexer.index), rather than bare.
        UUID workspaceA = UUID.randomUUID();
        UUID workspaceB = UUID.randomUUID();
        DocumentEntity documentInB = saveDocument(workspaceB);
        saveChunk(documentInB, workspaceB);

        WorkspaceContext.set(workspaceA);
        assertThat(transactionalChunkQueries.findByDocumentId(documentInB.getId()))
                .as("chunk lookup by documentId alone must not leak another workspace's chunks")
                .isEmpty();

        WorkspaceContext.set(workspaceB);
        assertThat(transactionalChunkQueries.findByDocumentId(documentInB.getId())).hasSize(1);
    }

    @Test
    void aRequestWithNoBoundWorkspaceSeesEverythingSinceTheFilterIsNeverEnabled() {
        DocumentEntity document = saveDocument(UUID.randomUUID());

        WorkspaceContext.clear();

        assertThat(documentRepository.findById(document.getId()))
                .as("legitimate pre-workspace flows (e.g. login) must not be broken by this filter")
                .isPresent();
    }

    private DocumentEntity saveDocument(UUID workspaceId) {
        WorkspaceContext.clear();
        UUID uploadedBy = UUID.randomUUID();
        workspaceRepository.save(
                new WorkspaceEntity(
                        workspaceId,
                        "workspace-" + workspaceId,
                        WorkspacePlan.FREE,
                        Instant.now()));
        userRepository.save(
                new UserEntity(
                        uploadedBy,
                        uploadedBy + "@documind.test",
                        "hash",
                        workspaceId,
                        UserRole.ADMIN,
                        Instant.now()));
        return documentRepository.save(
                new DocumentEntity(
                        UUID.randomUUID(),
                        workspaceId,
                        "contract.pdf",
                        "application/pdf",
                        1024,
                        workspaceId + "/doc/contract.pdf",
                        DocumentStatus.INDEXED,
                        uploadedBy,
                        Instant.now()));
    }

    private void saveChunk(DocumentEntity document, UUID workspaceId) {
        WorkspaceContext.clear();
        chunkRepository.save(
                new DocumentChunkEntity(
                        UUID.randomUUID(),
                        document.getId(),
                        workspaceId,
                        "chunk text",
                        0,
                        1,
                        UUID.randomUUID().toString(),
                        Instant.now()));
    }
}
