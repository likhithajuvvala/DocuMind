package com.documind.common.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "document_chunks")
@Filter(name = "workspaceFilter")
public class DocumentChunkEntity {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "text")
    private String chunkText;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "embedding_id", nullable = false)
    private String embeddingId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentChunkEntity() {
    }

    public DocumentChunkEntity(
            UUID id,
            UUID documentId,
            UUID workspaceId,
            String chunkText,
            int chunkIndex,
            Integer pageNumber,
            String embeddingId,
            Instant createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.workspaceId = workspaceId;
        this.chunkText = chunkText;
        this.chunkIndex = chunkIndex;
        this.pageNumber = pageNumber;
        this.embeddingId = embeddingId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getChunkText() {
        return chunkText;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public String getEmbeddingId() {
        return embeddingId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
