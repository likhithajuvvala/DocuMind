package com.documind.query.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "documind.retrieval")
public class RetrievalProperties {

    private int topK = 6;
    private double similarityThreshold = 0.7;
    private int historyMessageLimit = 10;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getHistoryMessageLimit() {
        return historyMessageLimit;
    }

    public void setHistoryMessageLimit(int historyMessageLimit) {
        this.historyMessageLimit = historyMessageLimit;
    }
}
