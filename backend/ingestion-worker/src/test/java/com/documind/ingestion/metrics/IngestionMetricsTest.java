package com.documind.ingestion.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IngestionMetricsTest {

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final IngestionMetrics metrics = new IngestionMetrics(registry);

    @Test
    void exposesTheSeriesNamesTheOverviewDashboardQueries() {
        metrics.recordIndexed(12, Duration.ofSeconds(3));
        metrics.recordPermanentFailure(Duration.ofSeconds(1));
        metrics.recordTransientFailure(Duration.ofSeconds(2));

        String scrape = registry.scrape();

        assertThat(scrape).contains("documind_ingestion_documents_total{failure=\"none\",result=\"indexed\"}");
        assertThat(scrape).contains("documind_ingestion_documents_total{failure=\"permanent\",result=\"failed\"}");
        assertThat(scrape).contains("documind_ingestion_documents_total{failure=\"transient\",result=\"failed\"}");
        assertThat(scrape).contains("documind_ingestion_chunks_total");
        assertThat(scrape).contains("documind_ingestion_duration_seconds_bucket");
    }

    @Test
    void countsChunksAndDocumentsSeparately() {
        metrics.recordIndexed(5, Duration.ofSeconds(1));
        metrics.recordIndexed(7, Duration.ofSeconds(1));

        assertThat(registry.get("documind.ingestion.documents")
                        .tag("result", "indexed")
                        .counter()
                        .count())
                .isEqualTo(2);
        assertThat(registry.get("documind.ingestion.chunks").counter().count()).isEqualTo(12);
    }
}
