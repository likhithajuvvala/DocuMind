package com.documind.ingestion.pipeline;

import com.documind.common.messaging.DocumentFailedEvent;
import com.documind.common.messaging.DocumentIndexedEvent;
import com.documind.common.messaging.KafkaTopics;
import com.documind.common.persistence.entity.DocumentEntity;
import java.time.Instant;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class IngestionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public IngestionEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishIndexed(DocumentEntity document, int chunkCount) {
        kafkaTemplate.send(
                KafkaTopics.DOCUMENT_INDEXED,
                document.getId().toString(),
                new DocumentIndexedEvent(
                        document.getId(), document.getWorkspaceId(), chunkCount, Instant.now()));
    }

    public void publishFailed(DocumentEntity document, String reason) {
        kafkaTemplate.send(
                KafkaTopics.DOCUMENT_FAILED,
                document.getId().toString(),
                new DocumentFailedEvent(
                        document.getId(), document.getWorkspaceId(), reason, Instant.now()));
    }
}
