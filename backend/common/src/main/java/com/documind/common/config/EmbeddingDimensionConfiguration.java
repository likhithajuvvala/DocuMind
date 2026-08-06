package com.documind.common.config;

import com.documind.common.rag.EmbeddingDimensionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnClass(EmbeddingModel.class)
@ConditionalOnProperty(prefix = "documind.rag", name = "validate-dimensions", matchIfMissing = true)
public class EmbeddingDimensionConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingDimensionConfiguration.class);

    private static final String PGVECTOR = "pgvector";

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final String vectorStoreType;
    private final String tableName;
    private final int configuredDimensions;

    public EmbeddingDimensionConfiguration(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            @Value("${spring.ai.vectorstore.type:pgvector}") String vectorStoreType,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:1536}") int configuredDimensions) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.embeddingModelProvider = embeddingModelProvider;
        this.vectorStoreType = vectorStoreType;
        this.tableName = tableName;
        this.configuredDimensions = configuredDimensions;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        if (!PGVECTOR.equalsIgnoreCase(vectorStoreType)) {
            LOGGER.info(
                    "Skipping the pgvector dimension check because the active vector store is {}, which sizes its "
                            + "collection from the embedding model itself",
                    vectorStoreType);
            return;
        }

        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();

        if (jdbcTemplate == null || embeddingModel == null) {
            LOGGER.debug("Skipping embedding dimension validation because this service does not embed documents");
            return;
        }

        new EmbeddingDimensionValidator(jdbcTemplate, embeddingModel, tableName, configuredDimensions).validate();
    }
}
