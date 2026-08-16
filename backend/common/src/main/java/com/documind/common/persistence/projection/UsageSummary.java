package com.documind.common.persistence.projection;

import java.math.BigDecimal;
import java.util.UUID;

public record UsageSummary(UUID userId, long totalTokens, BigDecimal totalCost) {}
