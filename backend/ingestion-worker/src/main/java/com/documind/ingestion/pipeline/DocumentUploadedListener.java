package com.documind.ingestion.pipeline;

import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.messaging.KafkaTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentUploadedListener {

    private final IngestionPipeline pipeline;

    public DocumentUploadedListener(IngestionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @KafkaListener(topics = KafkaTopics.DOCUMENT_UPLOADED, groupId = "ingestion-worker")
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        pipeline.process(event);
    }
}
