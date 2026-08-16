package com.documind.ingestion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class IngestionMetrics {

    private static final String DOCUMENTS_METRIC = "documind.ingestion.documents";
    private static final String CHUNKS_METRIC = "documind.ingestion.chunks";
    private static final String DURATION_METRIC = "documind.ingestion.duration";

    private final Counter indexedDocuments;
    private final Counter permanentFailures;
    private final Counter transientFailures;
    private final Counter indexedChunks;
    private final Timer ingestionDuration;

    public IngestionMetrics(MeterRegistry registry) {
        this.indexedDocuments =
                Counter.builder(DOCUMENTS_METRIC)
                        .description("Documents that completed the ingestion pipeline")
                        .tags("result", "indexed", "failure", "none")
                        .register(registry);
        this.permanentFailures =
                Counter.builder(DOCUMENTS_METRIC)
                        .description("Documents that failed ingestion")
                        .tags("result", "failed", "failure", "permanent")
                        .register(registry);
        this.transientFailures =
                Counter.builder(DOCUMENTS_METRIC)
                        .description("Documents that failed ingestion")
                        .tags("result", "failed", "failure", "transient")
                        .register(registry);
        this.indexedChunks =
                Counter.builder(CHUNKS_METRIC)
                        .description("Chunks written to the vector store")
                        .register(registry);
        this.ingestionDuration =
                Timer.builder(DURATION_METRIC)
                        .description("Time taken to extract, chunk, and embed a document")
                        .publishPercentileHistogram()
                        .register(registry);
    }

    public void recordIndexed(int chunkCount, Duration elapsed) {
        indexedDocuments.increment();
        indexedChunks.increment(chunkCount);
        ingestionDuration.record(elapsed);
    }

    public void recordPermanentFailure(Duration elapsed) {
        permanentFailures.increment();
        ingestionDuration.record(elapsed);
    }

    public void recordTransientFailure(Duration elapsed) {
        transientFailures.increment();
        ingestionDuration.record(elapsed);
    }
}
