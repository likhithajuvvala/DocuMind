package com.documind.query.api.dto;

import com.documind.common.persistence.projection.UsageSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkspaceUsageResponse(
        UUID workspaceId, Instant since, long totalTokens, BigDecimal totalCost, List<UsageSummary> perUser) {
}
