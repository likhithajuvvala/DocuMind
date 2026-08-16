package com.documind.document.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.error.DocumentIndexingInProgressException;
import com.documind.common.messaging.DocumentDeletedEvent;
import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.persistence.entity.DocumentChunkEntity;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.repository.DocumentChunkRepository;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.storage.ObjectStorage;
import com.documind.common.storage.ObjectStorageException;
import com.documind.document.upload.DocumentEventPublisher;
import com.documind.document.upload.DocumentUploadService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentLifecycleServiceTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    private final DocumentUploadService uploadService = mock(DocumentUploadService.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
    private final ObjectStorage objectStorage = mock(ObjectStorage.class);
    private final DocumentEventPublisher eventPublisher = mock(DocumentEventPublisher.class);
    private final DocumentLifecycleService service =
            new DocumentLifecycleService(
                    uploadService,
                    documentRepository,
                    chunkRepository,
                    objectStorage,
                    eventPublisher);

    @Test
    void deleteRemovesTheDocumentTheStorageObjectAndPublishesTheEmbeddingIdsToPurge() {
        DocumentEntity document = document(DocumentStatus.INDEXED);
        when(uploadService.requireDocument(document.getId(), WORKSPACE_ID)).thenReturn(document);
        when(chunkRepository.findByDocumentId(document.getId()))
                .thenReturn(List.of(chunk("embedding-1"), chunk("embedding-2")));

        service.delete(document.getId(), WORKSPACE_ID);

        verify(documentRepository).delete(document);
        verify(objectStorage).delete(document.getStoragePath());

        ArgumentCaptor<DocumentDeletedEvent> published =
                ArgumentCaptor.forClass(DocumentDeletedEvent.class);
        verify(eventPublisher).publishDeleted(published.capture());
        assertThat(published.getValue().documentId()).isEqualTo(document.getId());
        assertThat(published.getValue().workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(published.getValue().embeddingIds())
                .containsExactlyInAnyOrder("embedding-1", "embedding-2");
    }

    @Test
    void deleteStillPublishesTheEventWhenTheStorageObjectIsAlreadyGone() {
        DocumentEntity document = document(DocumentStatus.INDEXED);
        when(uploadService.requireDocument(document.getId(), WORKSPACE_ID)).thenReturn(document);
        when(chunkRepository.findByDocumentId(document.getId())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new ObjectStorageException("missing", new RuntimeException()))
                .when(objectStorage)
                .delete(document.getStoragePath());

        assertThatCode(() -> service.delete(document.getId(), WORKSPACE_ID))
                .doesNotThrowAnyException();

        verify(documentRepository).delete(document);
        verify(eventPublisher).publishDeleted(any());
    }

    @Test
    void reindexResetsStatusToPendingAndRepublishesTheUploadedEvent() {
        DocumentEntity document = document(DocumentStatus.FAILED);
        when(uploadService.requireDocument(document.getId(), WORKSPACE_ID)).thenReturn(document);

        DocumentEntity result = service.reindex(document.getId(), WORKSPACE_ID);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PENDING);
        verify(documentRepository).save(document);

        ArgumentCaptor<DocumentUploadedEvent> published =
                ArgumentCaptor.forClass(DocumentUploadedEvent.class);
        verify(eventPublisher).publishUploaded(published.capture());
        assertThat(published.getValue().documentId()).isEqualTo(document.getId());
        assertThat(published.getValue().storagePath()).isEqualTo(document.getStoragePath());
    }

    @Test
    void reindexRejectsADocumentThatIsCurrentlyBeingIndexed() {
        DocumentEntity document = document(DocumentStatus.PROCESSING);
        when(uploadService.requireDocument(document.getId(), WORKSPACE_ID)).thenReturn(document);

        assertThatThrownBy(() -> service.reindex(document.getId(), WORKSPACE_ID))
                .isInstanceOf(DocumentIndexingInProgressException.class);

        verify(documentRepository, never()).save(any());
        verify(eventPublisher, never()).publishUploaded(any());
    }

    private DocumentEntity document(DocumentStatus status) {
        return new DocumentEntity(
                UUID.randomUUID(),
                WORKSPACE_ID,
                "contract.pdf",
                "application/pdf",
                1024,
                WORKSPACE_ID + "/doc/contract.pdf",
                status,
                UUID.randomUUID(),
                Instant.now());
    }

    private DocumentChunkEntity chunk(String embeddingId) {
        return new DocumentChunkEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                WORKSPACE_ID,
                "text",
                0,
                1,
                embeddingId,
                Instant.now());
    }
}
