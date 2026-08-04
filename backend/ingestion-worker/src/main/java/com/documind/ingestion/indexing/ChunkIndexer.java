package com.documind.ingestion.indexing;

import com.documind.common.persistence.entity.DocumentChunkEntity;
import com.documind.common.persistence.entity.DocumentEntity;
import com.documind.common.persistence.repository.DocumentChunkRepository;
import com.documind.common.rag.ChunkMetadataKeys;
import com.documind.ingestion.chunking.TextChunk;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChunkIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkIndexer.class);

    private final VectorStore vectorStore;
    private final DocumentChunkRepository chunkRepository;

    public ChunkIndexer(VectorStore vectorStore, DocumentChunkRepository chunkRepository) {
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public int index(DocumentEntity source, List<TextChunk> chunks) {
        discardPreviousIndex(source);

        List<Document> embeddableDocuments = new ArrayList<>(chunks.size());
        List<DocumentChunkEntity> persistedChunks = new ArrayList<>(chunks.size());
        Instant now = Instant.now();

        for (TextChunk chunk : chunks) {
            String embeddingId = UUID.randomUUID().toString();
            embeddableDocuments.add(new Document(embeddingId, chunk.text(), buildMetadata(source, chunk)));
            persistedChunks.add(new DocumentChunkEntity(
                    UUID.randomUUID(),
                    source.getId(),
                    source.getWorkspaceId(),
                    chunk.text(),
                    chunk.index(),
                    chunk.pageNumber(),
                    embeddingId,
                    now));
        }

        vectorStore.add(embeddableDocuments);
        chunkRepository.saveAll(persistedChunks);
        return persistedChunks.size();
    }

    private void discardPreviousIndex(DocumentEntity source) {
        List<String> staleEmbeddingIds = chunkRepository.findByDocumentId(source.getId()).stream()
                .map(DocumentChunkEntity::getEmbeddingId)
                .toList();

        if (!staleEmbeddingIds.isEmpty()) {
            vectorStore.delete(staleEmbeddingIds);
            LOGGER.info("Removed {} stale embeddings for document {}", staleEmbeddingIds.size(), source.getId());
        }

        chunkRepository.deleteByDocumentId(source.getId());
    }

    private Map<String, Object> buildMetadata(DocumentEntity source, TextChunk chunk) {
        return Map.of(
                ChunkMetadataKeys.WORKSPACE_ID, source.getWorkspaceId().toString(),
                ChunkMetadataKeys.DOCUMENT_ID, source.getId().toString(),
                ChunkMetadataKeys.DOCUMENT_NAME, source.getFilename(),
                ChunkMetadataKeys.PAGE_NUMBER, chunk.pageNumber(),
                ChunkMetadataKeys.CHUNK_INDEX, chunk.index());
    }
}
