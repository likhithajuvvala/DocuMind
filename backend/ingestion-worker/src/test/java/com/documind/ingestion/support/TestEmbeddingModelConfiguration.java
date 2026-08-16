package com.documind.ingestion.support;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Replaces the real OpenAI/Ollama embedding auto-configuration with a deterministic local model,
 * activated by setting {@code spring.ai.model.embedding=none} in the test so neither provider's
 * auto-configuration runs and this becomes the only {@link EmbeddingModel} bean in the context.
 */
@TestConfiguration
public class TestEmbeddingModelConfiguration {

    public static final int TEST_DIMENSIONS = 32;

    @Bean
    public EmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel(TEST_DIMENSIONS);
    }
}
