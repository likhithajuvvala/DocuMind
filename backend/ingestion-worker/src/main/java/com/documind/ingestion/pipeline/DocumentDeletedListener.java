package com.documind.ingestion.pipeline;

import com.documind.common.messaging.DocumentDeletedEvent;
import com.documind.common.messaging.KafkaTopics;
import com.documind.ingestion.indexing.ChunkIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Cleans up the vector store after a document is deleted. By the time this runs, document-service
 * has already removed the document and its Postgres chunk rows via the schema's foreign-key
 * cascades, so a failure here only orphans embeddings in the vector store rather than leaving the
 * document itself in an inconsistent state — errors are logged and swallowed rather than retried
 * through the shared dead-letter topic, which is wired to expect {@code DocumentUploadedEvent}
 * payloads, not this event type.
 */
@Component
public class DocumentDeletedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentDeletedListener.class);

    private final ChunkIndexer chunkIndexer;

    public DocumentDeletedListener(ChunkIndexer chunkIndexer) {
        this.chunkIndexer = chunkIndexer;
    }

    @KafkaListener(topics = KafkaTopics.DOCUMENT_DELETED, groupId = "ingestion-worker")
    public void onDocumentDeleted(DocumentDeletedEvent event) {
        try {
            chunkIndexer.purgeEmbeddings(event.embeddingIds());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to remove vector store embeddings for deleted document {}; they will be orphaned",
                    event.documentId(),
                    exception);
        }
    }
}
