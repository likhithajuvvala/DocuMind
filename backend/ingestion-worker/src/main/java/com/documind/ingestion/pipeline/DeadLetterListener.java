package com.documind.ingestion.pipeline;

import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.messaging.KafkaTopics;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.tenant.WorkspaceContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code WorkspaceContext} is set from the event's own workspaceId before touching the
 * repositories, and cleared afterward — Kafka listener threads have no ambient workspace otherwise,
 * which would leave the persistence-layer workspace filter disabled for these lookups.
 */
@Component
public class DeadLetterListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeadLetterListener.class);
    private static final String EXHAUSTED_REASON = "Ingestion retries were exhausted";

    private final DocumentRepository documentRepository;
    private final IngestionJobTracker jobTracker;
    private final IngestionEventPublisher eventPublisher;

    public DeadLetterListener(
            DocumentRepository documentRepository,
            IngestionJobTracker jobTracker,
            IngestionEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.jobTracker = jobTracker;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(
            topics = KafkaTopics.DOCUMENT_UPLOADED_DEAD_LETTER,
            groupId = "ingestion-worker-dlt")
    public void onExhaustedIngestion(DocumentUploadedEvent event) {
        UUID documentId = event.documentId();
        WorkspaceContext.set(event.workspaceId());
        try {
            documentRepository
                    .findById(documentId)
                    .ifPresent(
                            document -> {
                                jobTracker.failLatestJob(document, EXHAUSTED_REASON);
                                eventPublisher.publishFailed(document, EXHAUSTED_REASON);
                                LOGGER.error(
                                        "Document {} moved to the dead letter topic after repeated failures",
                                        documentId);
                            });
        } finally {
            WorkspaceContext.clear();
        }
    }
}
