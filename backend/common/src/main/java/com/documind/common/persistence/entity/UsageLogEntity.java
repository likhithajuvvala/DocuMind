package com.documind.common.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_logs")
public class UsageLogEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "cost_estimate", nullable = false)
    private BigDecimal costEstimate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UsageLogEntity() {
    }

    public UsageLogEntity(
            UUID id,
            UUID workspaceId,
            UUID userId,
            String modelName,
            int promptTokens,
            int completionTokens,
            BigDecimal costEstimate,
            Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.modelName = modelName;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.costEstimate = costEstimate;
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

    public String getModelName() {
        return modelName;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public BigDecimal getCostEstimate() {
        return costEstimate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
