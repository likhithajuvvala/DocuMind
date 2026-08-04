package com.documind.ingestion.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.documind.common.domain.DocumentStatus;
import com.documind.common.persistence.entity.DocumentChunkEntity;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.repository.DocumentChunkRepository;
import com.documind.ingestion.chunking.TextChunk;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class ChunkIndexerTest {

    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Test
    void removesPreviousEmbeddingsBeforeIndexingAgain() {
        String staleEmbeddingId = UUID.randomUUID().toString();
        when(chunkRepository.findByDocumentId(DOCUMENT_ID)).thenReturn(List.of(existingChunk(staleEmbeddingId)));

        ChunkIndexer indexer = new ChunkIndexer(vectorStore, chunkRepository);
        indexer.index(document(), List.of(new TextChunk(0, 1, "fresh content")));

        InOrder order = inOrder(vectorStore, chunkRepository);
        order.verify(vectorStore).delete(List.of(staleEmbeddingId));
        order.verify(chunkRepository).deleteByDocumentId(DOCUMENT_ID);
        order.verify(vectorStore).add(anyList());
    }

    @Test
    void doesNotCallTheVectorStoreWhenThereIsNothingToDiscard() {
        when(chunkRepository.findByDocumentId(DOCUMENT_ID)).thenReturn(List.of());

        ChunkIndexer indexer = new ChunkIndexer(vectorStore, chunkRepository);
        int indexed = indexer.index(document(), List.of(new TextChunk(0, 1, "fresh content")));

        verify(vectorStore, never()).delete(anyList());
        assertThat(indexed).isEqualTo(1);
    }

    @Test
    void storesChunksWithTheSameIdentifiersUsedForTheEmbeddings() {
        when(chunkRepository.findByDocumentId(DOCUMENT_ID)).thenReturn(List.of());

        ChunkIndexer indexer = new ChunkIndexer(vectorStore, chunkRepository);
        indexer.index(document(), List.of(new TextChunk(0, 3, "content")));

        ArgumentCaptor<List<org.springframework.ai.document.Document>> embedded = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<DocumentChunkEntity>> persisted = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(embedded.capture());
        verify(chunkRepository).saveAll(persisted.capture());

        assertThat(persisted.getValue().get(0).getEmbeddingId())
                .isEqualTo(embedded.getValue().get(0).getId());
        assertThat(persisted.getValue().get(0).getPageNumber()).isEqualTo(3);
    }

    private DocumentEntity document() {
        return new DocumentEntity(
                DOCUMENT_ID,
                WORKSPACE_ID,
                "contract.pdf",
                "application/pdf",
                1024,
                "path/contract.pdf",
                DocumentStatus.PROCESSING,
                UUID.randomUUID(),
                Instant.now());
    }

    private DocumentChunkEntity existingChunk(String embeddingId) {
        return new DocumentChunkEntity(
                UUID.randomUUID(), DOCUMENT_ID, WORKSPACE_ID, "old content", 0, 1, embeddingId, Instant.now());
    }
}
