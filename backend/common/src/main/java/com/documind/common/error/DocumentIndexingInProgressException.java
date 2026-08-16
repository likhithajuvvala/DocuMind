package com.documind.common.error;

public class DocumentIndexingInProgressException extends RuntimeException {

    public DocumentIndexingInProgressException(String message) {
        super(message);
    }
}
