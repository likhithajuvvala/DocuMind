package com.documind.ingestion.pipeline;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.domain.IngestionStatus;
import com.documind.common.messaging.DocumentUploadedEvent;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.entity.IngestionJobEntity;
import com.documind.common.persistence.repository.DocumentRepository;
import com.documind.common.persistence.repository.IngestionJobRepository;
import com.documind.common.storage.ObjectStorage;
import com.documind.common.storage.ObjectStorageException;
import com.documind.ingestion.chunking.TextChunker;
import com.documind.ingestion.extraction.TextExtractionException;
import com.documind.ingestion.extraction.TextExtractor;
import com.documind.ingestion.indexing.ChunkIndexer;
import com.documind.ingestion.metrics.IngestionMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngestionPipelineTest {

    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    @Mock private DocumentRepository documentRepository;

    @Mock private IngestionJobRepository jobRepository;

    @Mock private IngestionJobTracker jobTracker;

    @Mock private ObjectStorage objectStorage;

    @Mock private TextExtractor textExtractor;

    @Mock private TextChunker textChunker;

    @Mock private ChunkIndexer chunkIndexer;

    @Mock private IngestionEventPublisher eventPublisher;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void skipsDocumentsThatAreAlreadyIndexed() {
        DocumentEntity document = document(DocumentStatus.INDEXED);
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(jobRepository.findFirstByDocumentIdOrderByStartedAtDesc(DOCUMENT_ID))
                .thenReturn(Optional.of(completedJob()));

        pipeline().process(event());

        verifyNoInteractions(objectStorage, textExtractor, textChunker, chunkIndexer);
        verify(jobTracker, never()).start(any());
    }

    @Test
    void rethrowsTransientFailuresSoTheMessageCanBeRetried() {
        DocumentEntity document = document(DocumentStatus.PENDING);
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(jobTracker.start(document)).thenReturn(queuedJob());
        when(objectStorage.read(anyString()))
                .thenThrow(new ObjectStorageException("bucket offline", null));

        assertThatThrownBy(() -> pipeline().process(event()))
                .isInstanceOf(RetryableIngestionException.class);

        verify(jobTracker).recordTransientFailure(any(), anyString());
        verify(eventPublisher, never()).publishFailed(any(), anyString());
    }

    @Test
    void marksUnparsableDocumentsFailedWithoutRetrying() {
        DocumentEntity document = document(DocumentStatus.PENDING);
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(jobTracker.start(document)).thenReturn(queuedJob());
        when(objectStorage.read(anyString()))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2}));
        when(textExtractor.extract(any()))
                .thenThrow(new TextExtractionException("corrupt file", null));

        pipeline().process(event());

        verify(jobTracker).fail(any(), any(), anyString());
        verify(eventPublisher).publishFailed(any(), anyString());
        verify(chunkIndexer, never()).index(any(), anyList());
    }

    private IngestionPipeline pipeline() {
        return new IngestionPipeline(
                documentRepository,
                jobRepository,
                jobTracker,
                objectStorage,
                textExtractor,
                textChunker,
                chunkIndexer,
                eventPublisher,
                new IngestionMetrics(meterRegistry));
    }

    private DocumentUploadedEvent event() {
        return new DocumentUploadedEvent(
                DOCUMENT_ID,
                WORKSPACE_ID,
                UUID.randomUUID(),
                "contract.pdf",
                "application/pdf",
                "path/contract.pdf",
                Instant.now());
    }

    private DocumentEntity document(DocumentStatus status) {
        return new DocumentEntity(
                DOCUMENT_ID,
                WORKSPACE_ID,
                "contract.pdf",
                "application/pdf",
                1024,
                "path/contract.pdf",
                status,
                UUID.randomUUID(),
                Instant.now());
    }

    private IngestionJobEntity completedJob() {
        IngestionJobEntity job = queuedJob();
        job.complete(4, Instant.now());
        return job;
    }

    private IngestionJobEntity queuedJob() {
        return new IngestionJobEntity(
                UUID.randomUUID(),
                DOCUMENT_ID,
                WORKSPACE_ID,
                IngestionStatus.QUEUED,
                Instant.now());
    }
}
