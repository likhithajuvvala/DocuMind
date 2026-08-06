package com.documind.query.usage;

import com.documind.common.persistence.entity.UsageLogEntity;
import com.documind.common.persistence.repository.UsageLogRepository;
import com.documind.common.security.AuthenticatedUser;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageRecorder {

    private static final BigDecimal TOKENS_PER_PRICING_UNIT = new BigDecimal("1000");
    private static final int COST_SCALE = 6;

    private static final String TOKENS_METRIC = "documind.llm.tokens";
    private static final String COST_METRIC = "documind.llm.cost";

    private final UsageLogRepository usageLogRepository;
    private final ModelPricingProperties pricing;
    private final MeterRegistry meterRegistry;

    public UsageRecorder(
            UsageLogRepository usageLogRepository, ModelPricingProperties pricing, MeterRegistry meterRegistry) {
        this.usageLogRepository = usageLogRepository;
        this.pricing = pricing;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public int record(AuthenticatedUser user, String prompt, String completion) {
        int promptTokens = estimateTokens(prompt);
        int completionTokens = estimateTokens(completion);
        BigDecimal cost = estimateCost(promptTokens, completionTokens);

        usageLogRepository.save(new UsageLogEntity(
                UUID.randomUUID(),
                user.workspaceId(),
                user.userId(),
                pricing.getModelName(),
                promptTokens,
                completionTokens,
                cost,
                Instant.now()));

        publishMetrics(user, promptTokens, completionTokens, cost);
        return promptTokens + completionTokens;
    }

    private void publishMetrics(AuthenticatedUser user, int promptTokens, int completionTokens, BigDecimal cost) {
        String workspace = user.workspaceId().toString();

        meterRegistry
                .counter(TOKENS_METRIC, "workspace", workspace, "model", pricing.getModelName(), "kind", "prompt")
                .increment(promptTokens);
        meterRegistry
                .counter(TOKENS_METRIC, "workspace", workspace, "model", pricing.getModelName(), "kind", "completion")
                .increment(completionTokens);
        meterRegistry
                .counter(COST_METRIC, "workspace", workspace, "model", pricing.getModelName())
                .increment(cost.doubleValue());
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / pricing.getCharactersPerToken());
    }

    private BigDecimal estimateCost(int promptTokens, int completionTokens) {
        BigDecimal promptCost = pricing.getPromptCostPerThousandTokens()
                .multiply(BigDecimal.valueOf(promptTokens))
                .divide(TOKENS_PER_PRICING_UNIT, COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal completionCost = pricing.getCompletionCostPerThousandTokens()
                .multiply(BigDecimal.valueOf(completionTokens))
                .divide(TOKENS_PER_PRICING_UNIT, COST_SCALE, RoundingMode.HALF_UP);
        return promptCost.add(completionCost);
    }
}
