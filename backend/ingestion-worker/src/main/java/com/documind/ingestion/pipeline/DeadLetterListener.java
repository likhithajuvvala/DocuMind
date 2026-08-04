package com.documind.ingestion.pipeline;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.messaging.KafkaTopics;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.entity.IngestionJobEntity;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.persistence.repository.IngestionJobRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeadLetterListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeadLetterListener.class);
    private static final String EXHAUSTED_REASON = "Ingestion retries were exhausted";

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository jobRepository;
    private final IngestionEventPublisher eventPublisher;

    public DeadLetterListener(
            DocumentRepository documentRepository,
            IngestionJobRepository jobRepository,
            IngestionEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.jobRepository = jobRepository;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = KafkaTopics.DOCUMENT_UPLOADED_DEAD_LETTER, groupId = "ingestion-worker-dlt")
    @Transactional
    public void onExhaustedIngestion(DocumentUploadedEvent event) {
        UUID documentId = event.documentId();
        documentRepository.findById(documentId).ifPresent(document -> {
            markFailed(document);
            eventPublisher.publishFailed(document, EXHAUSTED_REASON);
            LOGGER.error("Document {} moved to the dead letter topic after repeated failures", documentId);
        });
    }

    private void markFailed(DocumentEntity document) {
        document.changeStatus(DocumentStatus.FAILED);
        documentRepository.save(document);
        jobRepository.findFirstByDocumentIdOrderByStartedAtDesc(document.getId()).ifPresent(job -> {
            job.fail(EXHAUSTED_REASON, Instant.now());
            jobRepository.save(job);
        });
    }
}
