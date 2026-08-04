package com.documind.common.persistence.entity;

import com.documind.common.domain.IngestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_jobs")
public class IngestionJobEntity {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected IngestionJobEntity() {
    }

    public IngestionJobEntity(UUID id, UUID documentId, UUID workspaceId, IngestionStatus status, Instant startedAt) {
        this.id = id;
        this.documentId = documentId;
        this.workspaceId = workspaceId;
        this.status = status;
        this.chunkCount = 0;
        this.startedAt = startedAt;
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

    public IngestionStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void advanceTo(IngestionStatus status) {
        this.status = status;
    }

    public void complete(int chunkCount, Instant finishedAt) {
        this.status = IngestionStatus.COMPLETED;
        this.chunkCount = chunkCount;
        this.finishedAt = finishedAt;
    }

    public void noteRetryableError(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void fail(String errorMessage, Instant finishedAt) {
        this.status = IngestionStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = finishedAt;
    }
}
