package com.documind.document.ingestion;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.messaging.DocumentFailedEvent;
import com.documind.common.messaging.DocumentIndexedEvent;
import com.documind.common.messaging.KafkaTopics;
import com.documind.common.persistence.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IngestionResultListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionResultListener.class);

    private final DocumentRepository documentRepository;

    public IngestionResultListener(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @KafkaListener(topics = KafkaTopics.DOCUMENT_INDEXED, groupId = "document-service")
    @Transactional
    public void onDocumentIndexed(DocumentIndexedEvent event) {
        documentRepository.findById(event.documentId()).ifPresent(document -> {
            document.changeStatus(DocumentStatus.INDEXED);
            LOGGER.info("Document {} indexed with {} chunks", event.documentId(), event.chunkCount());
        });
    }

    @KafkaListener(topics = KafkaTopics.DOCUMENT_FAILED, groupId = "document-service")
    @Transactional
    public void onDocumentFailed(DocumentFailedEvent event) {
        documentRepository.findById(event.documentId()).ifPresent(document -> {
            document.changeStatus(DocumentStatus.FAILED);
            LOGGER.warn("Document {} failed ingestion: {}", event.documentId(), event.reason());
        });
    }
}
