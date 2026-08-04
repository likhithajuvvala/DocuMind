package com.documind.common.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions")
public class ChatSessionEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatSessionEntity() {
    }

    public ChatSessionEntity(UUID id, UUID workspaceId, UUID userId, UUID documentId, String title, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.documentId = documentId;
        this.title = title;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void rename(String title) {
        this.title = title;
    }
}
