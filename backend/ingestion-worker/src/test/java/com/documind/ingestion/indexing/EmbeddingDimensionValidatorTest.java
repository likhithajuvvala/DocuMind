package com.documind.ingestion.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.documind.common.rag.EmbeddingDimensionMismatchException;
import com.documind.common.rag.EmbeddingDimensionValidator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class EmbeddingDimensionValidatorTest {

    private static final String TABLE = "vector_store";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

    @Test
    void refusesToStartWhenTheExistingTableUsesADifferentDimension() {
        storedDimension(1536);
        when(embeddingModel.dimensions()).thenReturn(768);

        assertThatThrownBy(() -> validator(768).validate())
                .isInstanceOf(EmbeddingDimensionMismatchException.class)
                .hasMessageContaining("stores 1536-dimension vectors")
                .hasMessageContaining("configured for 768")
                .hasMessageContaining("EMBEDDING_DIMENSIONS=1536")
                .hasMessageContaining("drop table vector_store");
    }

    @Test
    void refusesToStartWhenTheModelDisagreesWithTheConfiguredDimension() {
        storedDimension(null);
        when(embeddingModel.dimensions()).thenReturn(768);

        assertThatThrownBy(() -> validator(1536).validate())
                .isInstanceOf(EmbeddingDimensionMismatchException.class)
                .hasMessageContaining("produces 768-dimension vectors")
                .hasMessageContaining("EMBEDDING_DIMENSIONS is 1536");
    }

    @Test
    void startsWhenEveryDimensionAgrees() {
        storedDimension(768);
        when(embeddingModel.dimensions()).thenReturn(768);

        assertThatCode(() -> validator(768).validate()).doesNotThrowAnyException();
    }

    @Test
    void startsBeforeTheVectorTableHasBeenCreated() {
        storedDimension(null);
        when(embeddingModel.dimensions()).thenReturn(1536);

        assertThatCode(() -> validator(1536).validate()).doesNotThrowAnyException();
    }

    @Test
    void startsWhenTheEmbeddingModelCannotBeReached() {
        storedDimension(768);
        when(embeddingModel.dimensions()).thenThrow(new IllegalStateException("provider unavailable"));

        assertThatCode(() -> validator(768).validate()).doesNotThrowAnyException();
    }

    @Test
    void reportsTheStoredDimensionEvenWhenTheModelIsUnreachable() {
        storedDimension(1536);
        when(embeddingModel.dimensions()).thenThrow(new IllegalStateException("provider unavailable"));

        assertThatThrownBy(() -> validator(768).validate())
                .isInstanceOf(EmbeddingDimensionMismatchException.class)
                .hasMessageContaining("stores 1536-dimension vectors");
    }

    @Test
    void treatsAColumnWithoutADeclaredDimensionAsUnknown() {
        storedDimension(-1);
        when(embeddingModel.dimensions()).thenReturn(768);

        assertThat(validator(768)).isNotNull();
        assertThatCode(() -> validator(768).validate()).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private void storedDimension(Integer dimension) {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
                .thenReturn(dimension);
    }

    private EmbeddingDimensionValidator validator(int configuredDimensions) {
        return new EmbeddingDimensionValidator(jdbcTemplate, embeddingModel, TABLE, configuredDimensions);
    }
}
