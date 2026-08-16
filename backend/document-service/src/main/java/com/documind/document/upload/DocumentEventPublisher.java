package com.documind.document.upload;

import com.documind.common.messaging.DocumentDeletedEvent;
import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.messaging.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DocumentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DocumentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishUploaded(DocumentUploadedEvent event) {
        kafkaTemplate.send(KafkaTopics.DOCUMENT_UPLOADED, event.documentId().toString(), event);
    }

    public void publishDeleted(DocumentDeletedEvent event) {
        kafkaTemplate.send(KafkaTopics.DOCUMENT_DELETED, event.documentId().toString(), event);
    }
}
