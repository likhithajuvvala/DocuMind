package com.documind.document.lifecycle;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentLifecycleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentLifecycleService.class);

    private final DocumentUploadService uploadService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ObjectStorage objectStorage;
    private final DocumentEventPublisher eventPublisher;

    public DocumentLifecycleService(
            DocumentUploadService uploadService,
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            ObjectStorage objectStorage,
            DocumentEventPublisher eventPublisher) {
        this.uploadService = uploadService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.objectStorage = objectStorage;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Deletes a document and every artifact indexing it created. The document row and its
     * Postgres-side chunk/ingestion-job rows are removed here in one transaction, relying on the
     * schema's {@code ON DELETE CASCADE} (V1__initial_schema.sql) rather than duplicating that as
     * application code. The vector store and the S3 object live outside Postgres and can't share
     * that transaction: the S3 delete is attempted synchronously and only logged on failure, since
     * the document is already gone from the user's perspective at that point, and the vector store
     * cleanup happens asynchronously in ingestion-worker — the service that already owns the
     * VectorStore client — driven by the embedding ids captured here before the chunk rows cascade
     * away.
     */
    @Transactional
    public void delete(UUID documentId, UUID workspaceId) {
        DocumentEntity document = uploadService.requireDocument(documentId, workspaceId);
        List<String> embeddingIds = chunkRepository.findByDocumentId(documentId).stream()
                .map(DocumentChunkEntity::getEmbeddingId)
                .toList();

        documentRepository.delete(document);

        deleteStorageObjectBestEffort(document.getStoragePath());
        eventPublisher.publishDeleted(new DocumentDeletedEvent(documentId, workspaceId, embeddingIds, Instant.now()));
    }

    /**
     * Re-runs ingestion from the object already stored in S3. The previous chunks and vector
     * records are deliberately not purged here: {@code ChunkIndexer} already discards a document's
     * prior index before writing the new one, so duplicating that here would just be a second,
     * redundant round trip through the same tables.
     */
    @Transactional
    public DocumentEntity reindex(UUID documentId, UUID workspaceId) {
        DocumentEntity document = uploadService.requireDocument(documentId, workspaceId);
        if (document.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentIndexingInProgressException("Document " + documentId + " is already being indexed");
        }

        document.changeStatus(DocumentStatus.PENDING);
        documentRepository.save(document);

        eventPublisher.publishUploaded(new DocumentUploadedEvent(
                document.getId(),
                document.getWorkspaceId(),
                document.getUploadedBy(),
                document.getFilename(),
                document.getContentType(),
                document.getStoragePath(),
                Instant.now()));

        return document;
    }

    private void deleteStorageObjectBestEffort(String storagePath) {
        try {
            objectStorage.delete(storagePath);
        } catch (ObjectStorageException exception) {
            LOGGER.warn(
                    "Failed to delete storage object {} after removing the document record; it will be orphaned "
                            + "until cleaned up separately",
                    storagePath,
                    exception);
        }
    }
}
