package com.documind.ingestion.pipeline;

public class RetryableIngestionException extends RuntimeException {

    public RetryableIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
