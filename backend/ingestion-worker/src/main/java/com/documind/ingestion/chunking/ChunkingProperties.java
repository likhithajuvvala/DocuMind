package com.documind.ingestion.chunking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "documind.ingestion.chunking")
public class ChunkingProperties {

    private int chunkSize = 900;
    private int overlap = 150;
    private int minimumChunkLength = 40;

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getOverlap() {
        return overlap;
    }

    public void setOverlap(int overlap) {
        this.overlap = overlap;
    }

    public int getMinimumChunkLength() {
        return minimumChunkLength;
    }

    public void setMinimumChunkLength(int minimumChunkLength) {
        this.minimumChunkLength = minimumChunkLength;
    }
}
