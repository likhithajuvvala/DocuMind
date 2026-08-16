package com.documind.common.persistence.entity;

import com.documind.common.domain.WorkspacePlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
public class WorkspaceEntity {

    @Id private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkspacePlan plan;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkspaceEntity() {}

    public WorkspaceEntity(UUID id, String name, WorkspacePlan plan, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.plan = plan;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public WorkspacePlan getPlan() {
        return plan;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changePlan(WorkspacePlan plan) {
        this.plan = plan;
    }
}
