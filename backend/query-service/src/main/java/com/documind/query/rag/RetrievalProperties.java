package com.documind.query.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "documind.retrieval")
public class RetrievalProperties {

    private int topK = 6;
    private double similarityThreshold = 0.7;
    private int historyMessageLimit = 10;
    private String reranker = "lexical";
    private double rerankVectorWeight = 0.6;

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

    public String getReranker() {
        return reranker;
    }

    public void setReranker(String reranker) {
        this.reranker = reranker;
    }

    public double getRerankVectorWeight() {
        return rerankVectorWeight;
    }

    public void setRerankVectorWeight(double rerankVectorWeight) {
        this.rerankVectorWeight = rerankVectorWeight;
    }

    public int getHistoryMessageLimit() {
        return historyMessageLimit;
    }

    public void setHistoryMessageLimit(int historyMessageLimit) {
        this.historyMessageLimit = historyMessageLimit;
    }
}
