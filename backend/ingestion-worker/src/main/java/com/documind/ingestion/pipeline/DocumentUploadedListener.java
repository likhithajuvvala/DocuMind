package com.documind.ingestion.pipeline;

import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.messaging.KafkaTopics;
import com.documind.common.tenant.WorkspaceContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code WorkspaceContext} is set from the event's own workspaceId before processing, and cleared
 * afterward — Kafka listener threads have no ambient workspace otherwise, which would leave the
 * persistence-layer workspace filter disabled for every repository call inside {@link
 * IngestionPipeline} (and everything it delegates to).
 */
@Component
public class DocumentUploadedListener {

    private final IngestionPipeline pipeline;

    public DocumentUploadedListener(IngestionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @KafkaListener(topics = KafkaTopics.DOCUMENT_UPLOADED, groupId = "ingestion-worker")
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        WorkspaceContext.set(event.workspaceId());
        try {
            pipeline.process(event);
        } finally {
            WorkspaceContext.clear();
        }
    }
}
