package com.documind.ingestion.pipeline;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.documind.common.messaging.DocumentDeletedEvent;
import com.documind.ingestion.indexing.ChunkIndexer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentDeletedListenerTest {

    private final ChunkIndexer chunkIndexer = mock(ChunkIndexer.class);
    private final DocumentDeletedListener listener = new DocumentDeletedListener(chunkIndexer);

    @Test
    void purgesTheEmbeddingsCarriedByTheEvent() {
        DocumentDeletedEvent event =
                new DocumentDeletedEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        List.of("embedding-1", "embedding-2"),
                        Instant.now());

        listener.onDocumentDeleted(event);

        verify(chunkIndexer).purgeEmbeddings(List.of("embedding-1", "embedding-2"));
    }

    @Test
    void swallowsFailuresSoTheyNeverReachTheSharedDeadLetterTopic() {
        DocumentDeletedEvent event =
                new DocumentDeletedEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        List.of("embedding-1"),
                        Instant.now());
        doThrow(new RuntimeException("vector store unavailable"))
                .when(chunkIndexer)
                .purgeEmbeddings(List.of("embedding-1"));

        assertThatCode(() -> listener.onDocumentDeleted(event)).doesNotThrowAnyException();
    }
}
