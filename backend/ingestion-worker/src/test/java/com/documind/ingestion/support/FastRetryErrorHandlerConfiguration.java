package com.documind.ingestion.support;

import com.documind.common.error.ResourceNotFoundException;
import com.documind.common.messaging.KafkaTopics;
import com.documind.ingestion.extraction.TextExtractionException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Replaces the production error handler's five-minute exponential backoff with a fixed, sub-second
 * one, so a test can observe a message actually reach the dead letter topic without waiting for the
 * real retry budget to run out.
 */
@TestConfiguration
public class FastRetryErrorHandlerConfiguration {

    private static final long RETRY_INTERVAL_MILLIS = 200L;
    private static final long MAX_RETRIES = 2L;

    @Bean
    @Primary
    public DefaultErrorHandler fastRetryIngestionErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition(
                                        KafkaTopics.DOCUMENT_UPLOADED_DEAD_LETTER,
                                        record.partition()));

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_RETRIES));
        errorHandler.addNotRetryableExceptions(
                ResourceNotFoundException.class, TextExtractionException.class);
        return errorHandler;
    }
}
