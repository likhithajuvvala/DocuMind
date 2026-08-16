package com.documind.common.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

public class EmbeddingDimensionValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingDimensionValidator.class);
    private static final String STORED_DIMENSION_QUERY =
            """
            select atttypmod
            from pg_attribute
            where attrelid = to_regclass(?) and attname = 'embedding'
            """;
    private static final int UNSPECIFIED_DIMENSION = -1;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final String tableName;
    private final int configuredDimensions;

    public EmbeddingDimensionValidator(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            String tableName,
            int configuredDimensions) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.tableName = tableName;
        this.configuredDimensions = configuredDimensions;
    }

    public void validate() {
        Integer storedDimensions = readStoredDimensions();
        if (storedDimensions != null && storedDimensions != configuredDimensions) {
            throw new EmbeddingDimensionMismatchException(existingTableMessage(storedDimensions));
        }

        Integer modelDimensions = probeModelDimensions();
        if (modelDimensions != null && modelDimensions != configuredDimensions) {
            throw new EmbeddingDimensionMismatchException(modelMessage(modelDimensions));
        }

        LOGGER.info(
                "Embedding dimensions agree: configured={}, model={}, table={}",
                configuredDimensions,
                modelDimensions == null ? "unverified" : modelDimensions,
                storedDimensions == null ? "not created yet" : storedDimensions);
    }

    private Integer readStoredDimensions() {
        try {
            Integer dimension =
                    jdbcTemplate.query(
                            STORED_DIMENSION_QUERY,
                            resultSet -> resultSet.next() ? resultSet.getInt(1) : null,
                            "public." + tableName);
            return dimension == null || dimension == UNSPECIFIED_DIMENSION ? null : dimension;
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not read the dimension of the {} table", tableName, exception);
            return null;
        }
    }

    private Integer probeModelDimensions() {
        try {
            return embeddingModel.dimensions();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not reach the embedding model to confirm its dimension, continuing with the configured "
                            + "value of {}",
                    configuredDimensions,
                    exception);
            return null;
        }
    }

    private String existingTableMessage(int storedDimensions) {
        return """
                The %s table stores %d-dimension vectors but this service is configured for %d.
                Every embedding write would fail with "expected %d dimensions, not %d".
                Either set EMBEDDING_DIMENSIONS=%d to match the existing data, or drop the table and re-index:
                  drop table %s; truncate document_chunks;"""
                .formatted(
                        tableName,
                        storedDimensions,
                        configuredDimensions,
                        storedDimensions,
                        configuredDimensions,
                        storedDimensions,
                        tableName);
    }

    private String modelMessage(int modelDimensions) {
        return """
                The configured embedding model produces %d-dimension vectors but EMBEDDING_DIMENSIONS is %d.
                Set EMBEDDING_DIMENSIONS=%d, and drop the %s table if it was already created with a different size."""
                .formatted(modelDimensions, configuredDimensions, modelDimensions, tableName);
    }
}
