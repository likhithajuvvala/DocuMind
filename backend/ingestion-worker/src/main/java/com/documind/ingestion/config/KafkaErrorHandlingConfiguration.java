package com.documind.ingestion.config;

import com.documind.common.error.ResourceNotFoundException;
import com.documind.common.messaging.KafkaTopics;
import com.documind.ingestion.extraction.TextExtractionException;
import java.time.Duration;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaErrorHandlingConfiguration {

    private static final long INITIAL_RETRY_INTERVAL_MILLIS = 2000;
    private static final double RETRY_MULTIPLIER = 2.0;
    private static final Duration MAX_RETRY_ELAPSED_TIME = Duration.ofMinutes(5);

    @Bean
    public DefaultErrorHandler ingestionErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) ->
                        new TopicPartition(KafkaTopics.DOCUMENT_UPLOADED_DEAD_LETTER, record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_RETRY_INTERVAL_MILLIS, RETRY_MULTIPLIER);
        backOff.setMaxElapsedTime(MAX_RETRY_ELAPSED_TIME.toMillis());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(ResourceNotFoundException.class, TextExtractionException.class);
        return errorHandler;
    }
}
