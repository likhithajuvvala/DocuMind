package com.documind.query.usage;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "documind.usage")
public class ModelPricingProperties {

    private String modelName = "unknown";
    private int charactersPerToken = 4;
    private BigDecimal promptCostPerThousandTokens = new BigDecimal("0.0005");
    private BigDecimal completionCostPerThousandTokens = new BigDecimal("0.0015");

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getCharactersPerToken() {
        return charactersPerToken;
    }

    public void setCharactersPerToken(int charactersPerToken) {
        this.charactersPerToken = charactersPerToken;
    }

    public BigDecimal getPromptCostPerThousandTokens() {
        return promptCostPerThousandTokens;
    }

    public void setPromptCostPerThousandTokens(BigDecimal promptCostPerThousandTokens) {
        this.promptCostPerThousandTokens = promptCostPerThousandTokens;
    }

    public BigDecimal getCompletionCostPerThousandTokens() {
        return completionCostPerThousandTokens;
    }

    public void setCompletionCostPerThousandTokens(BigDecimal completionCostPerThousandTokens) {
        this.completionCostPerThousandTokens = completionCostPerThousandTokens;
    }
}
