package com.documind.ingestion.pipeline;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.domain.IngestionStatus;
import com.documind.common.error.ResourceNotFoundException;
import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.entity.IngestionJobEntity;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.persistence.repository.IngestionJobRepository;
import com.documind.common.storage.ObjectStorage;
import com.documind.ingestion.chunking.TextChunk;
import com.documind.ingestion.chunking.TextChunker;
import com.documind.ingestion.extraction.ExtractedPage;
import com.documind.ingestion.extraction.TextExtractionException;
import com.documind.ingestion.extraction.TextExtractor;
import com.documind.ingestion.indexing.ChunkIndexer;
import com.documind.ingestion.metrics.IngestionMetrics;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class IngestionPipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionPipeline.class);
    private static final String DOCUMENT_ID_KEY = "document_id";
    private static final String WORKSPACE_ID_KEY = "workspace_id";

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository jobRepository;
    private final IngestionJobTracker jobTracker;
    private final ObjectStorage objectStorage;
    private final TextExtractor textExtractor;
    private final TextChunker textChunker;
    private final ChunkIndexer chunkIndexer;
    private final IngestionEventPublisher eventPublisher;
    private final IngestionMetrics metrics;

    public IngestionPipeline(
            DocumentRepository documentRepository,
            IngestionJobRepository jobRepository,
            IngestionJobTracker jobTracker,
            ObjectStorage objectStorage,
            TextExtractor textExtractor,
            TextChunker textChunker,
            ChunkIndexer chunkIndexer,
            IngestionEventPublisher eventPublisher,
            IngestionMetrics metrics) {
        this.documentRepository = documentRepository;
        this.jobRepository = jobRepository;
        this.jobTracker = jobTracker;
        this.objectStorage = objectStorage;
        this.textExtractor = textExtractor;
        this.textChunker = textChunker;
        this.chunkIndexer = chunkIndexer;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    public void process(DocumentUploadedEvent event) {
        MDC.put(DOCUMENT_ID_KEY, event.documentId().toString());
        MDC.put(WORKSPACE_ID_KEY, event.workspaceId().toString());
        try {
            processDocument(event);
        } finally {
            MDC.remove(DOCUMENT_ID_KEY);
            MDC.remove(WORKSPACE_ID_KEY);
        }
    }

    private void processDocument(DocumentUploadedEvent event) {
        DocumentEntity document =
                documentRepository
                        .findById(event.documentId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Document "
                                                        + event.documentId()
                                                        + " was not found"));

        if (alreadyIndexed(document)) {
            LOGGER.info("Skipping document {} because it is already indexed", document.getId());
            return;
        }

        IngestionJobEntity job = jobTracker.start(document);
        long startedAt = System.nanoTime();

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
            metrics.recordIndexed(indexedChunks, elapsedSince(startedAt));
            LOGGER.info("Indexed document {} into {} chunks", document.getId(), indexedChunks);
        } catch (TextExtractionException exception) {
            jobTracker.fail(document, job, exception.getMessage());
            eventPublisher.publishFailed(document, exception.getMessage());
            metrics.recordPermanentFailure(elapsedSince(startedAt));
            LOGGER.error(
                    "Document {} cannot be parsed and will not be retried",
                    document.getId(),
                    exception);
        } catch (Exception exception) {
            jobTracker.recordTransientFailure(job, exception.getMessage());
            metrics.recordTransientFailure(elapsedSince(startedAt));
            LOGGER.warn(
                    "Ingestion of document {} failed and will be retried",
                    document.getId(),
                    exception);
            throw new RetryableIngestionException(
                    "Ingestion failed for document " + document.getId(), exception);
        }
    }

    private Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private boolean alreadyIndexed(DocumentEntity document) {
        if (document.getStatus() != DocumentStatus.INDEXED) {
            return false;
        }
        return jobRepository
                .findFirstByDocumentIdOrderByStartedAtDesc(document.getId())
                .filter(job -> job.getStatus() == IngestionStatus.COMPLETED)
                .isPresent();
    }
}
