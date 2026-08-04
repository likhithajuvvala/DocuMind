package com.documind.ingestion.pipeline;

import com.documind.common.domain.IngestionStatus;
import com.documind.common.error.ResourceNotFoundException;
import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.entity.IngestionJobEntity;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.storage.ObjectStorage;
import com.documind.ingestion.chunking.TextChunk;
import com.documind.ingestion.chunking.TextChunker;
import com.documind.ingestion.extraction.ExtractedPage;
import com.documind.ingestion.extraction.TextExtractor;
import com.documind.ingestion.indexing.ChunkIndexer;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IngestionPipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionPipeline.class);

    private final DocumentRepository documentRepository;
    private final IngestionJobTracker jobTracker;
    private final ObjectStorage objectStorage;
    private final TextExtractor textExtractor;
    private final TextChunker textChunker;
    private final ChunkIndexer chunkIndexer;
    private final IngestionEventPublisher eventPublisher;

    public IngestionPipeline(
            DocumentRepository documentRepository,
            IngestionJobTracker jobTracker,
            ObjectStorage objectStorage,
            TextExtractor textExtractor,
            TextChunker textChunker,
            ChunkIndexer chunkIndexer,
            IngestionEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.jobTracker = jobTracker;
        this.objectStorage = objectStorage;
        this.textExtractor = textExtractor;
        this.textChunker = textChunker;
        this.chunkIndexer = chunkIndexer;
        this.eventPublisher = eventPublisher;
    }

    public void process(DocumentUploadedEvent event) {
        DocumentEntity document = documentRepository
                .findById(event.documentId())
                .orElseThrow(() -> new ResourceNotFoundException("Document " + event.documentId() + " was not found"));
        IngestionJobEntity job = jobTracker.start(document);

        try {
            jobTracker.advance(job, IngestionStatus.EXTRACTING);
            List<ExtractedPage> pages;
            try (InputStream content = objectStorage.read(document.getStoragePath())) {
                pages = textExtractor.extract(content);
            }

            jobTracker.advance(job, IngestionStatus.CHUNKING);
            List<TextChunk> chunks = textChunker.chunk(pages);

            jobTracker.advance(job, IngestionStatus.EMBEDDING);
            int indexedChunks = chunkIndexer.index(document, chunks);

            jobTracker.complete(document, job, indexedChunks);
            eventPublisher.publishIndexed(document, indexedChunks);
            LOGGER.info("Indexed document {} into {} chunks", document.getId(), indexedChunks);
        } catch (Exception exception) {
            jobTracker.fail(document, job, exception.getMessage());
            eventPublisher.publishFailed(document, exception.getMessage());
            LOGGER.error("Ingestion failed for document {}", document.getId(), exception);
        }
    }
}
