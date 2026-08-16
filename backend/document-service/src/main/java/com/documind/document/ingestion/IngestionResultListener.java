package com.documind.document.ingestion;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.messaging.DocumentFailedEvent;
import com.documind.common.messaging.DocumentIndexedEvent;
import com.documind.common.messaging.KafkaTopics;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.tenant.WorkspaceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code WorkspaceContext} is set from each event's own workspaceId before touching the repository,
 * and cleared afterward — mirroring what {@code JwtAuthenticationFilter} does on the HTTP request
 * thread. Kafka listener threads otherwise have no ambient workspace at all, which would leave
 * {@code documentRepository.findById} (unscoped by itself) running with the persistence-layer
 * workspace filter disabled.
 */
@Component
public class IngestionResultListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionResultListener.class);

    private final DocumentRepository documentRepository;

    public IngestionResultListener(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @KafkaListener(topics = KafkaTopics.DOCUMENT_INDEXED, groupId = "document-service")
    public void onDocumentIndexed(DocumentIndexedEvent event) {
        WorkspaceContext.set(event.workspaceId());
        try {
            documentRepository
                    .findById(event.documentId())
                    .ifPresent(
                            document -> {
                                document.changeStatus(DocumentStatus.INDEXED);
                                documentRepository.save(document);
                                LOGGER.info(
                                        "Document {} indexed with {} chunks",
                                        event.documentId(),
                                        event.chunkCount());
                            });
        } finally {
            WorkspaceContext.clear();
        }
    }

    @KafkaListener(topics = KafkaTopics.DOCUMENT_FAILED, groupId = "document-service")
    public void onDocumentFailed(DocumentFailedEvent event) {
        WorkspaceContext.set(event.workspaceId());
        try {
            documentRepository
                    .findById(event.documentId())
                    .ifPresent(
                            document -> {
                                document.changeStatus(DocumentStatus.FAILED);
                                documentRepository.save(document);
                                LOGGER.warn(
                                        "Document {} failed ingestion: {}",
                                        event.documentId(),
                                        event.reason());
                            });
        } finally {
            WorkspaceContext.clear();
        }
    }
}
